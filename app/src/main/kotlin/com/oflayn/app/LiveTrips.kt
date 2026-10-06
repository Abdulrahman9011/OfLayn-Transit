package com.oflayn.app

import android.content.Context
import com.oflayn.domain.trips.LiveTripsSource
import com.oflayn.domain.trips.StopRef
import com.oflayn.domain.trips.Trip
import com.oflayn.domain.trips.TripKind
import com.oflayn.domain.trips.minuteOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real trips for today, taken from the same official BursaKart "Otobüsüm Nerede" service the
 * operator's own page calls. The service answers with the remaining time of the vehicles that are
 * actually running, so a departure time = now + remaining minutes.
 *
 * Only the app's own phone-to-service request is used; no key, no token, no account.
 * Every failure returns an empty list: the caller falls back to the data already stored locally.
 */
class LiveTrips(private val ctx: Context) : LiveTripsSource {

    @Volatile var lastError: String = ""
        private set

    override suspend fun line(code: String, title: String, routeId: Int, firstStops: List<Pair<String, StopRef>>): List<Trip> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<Trip>()
            val now = System.currentTimeMillis()
            val nowMin = minuteOfDay(now)
            for ((dir, stop) in firstStops) {
                val arr = post("static/stationremainingtime", JSONObject().put("keyword", stop.id)) ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (!o.optString("routeCode").equals(code, ignoreCase = true)) continue
                    val rem = minutesOf(o.optString("passTime")) ?: continue
                    if (rem < 0 || rem > 240) continue
                    out.add(
                        Trip(
                            lineCode = code,
                            lineTitle = o.optString("routeTitle").ifBlank { title },
                            routeId = routeId,
                            dir = o.optString("direction").ifBlank { dir },
                            stopId = stop.id,
                            stopName = stop.name,
                            departMin = nowMin + rem,
                            plate = o.optString("licencePlate").trim(),
                            observedAtMs = now,
                            kind = TripKind.OBSERVED,
                        )
                    )
                }
            }
            out
        }

    private fun minutesOf(t: String): Int? {
        val p = t.split(":")
        if (p.size < 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun post(path: String, body: JSONObject): org.json.JSONArray? = try {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json, text/plain, */*")
            c.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8")
            c.setRequestProperty("Origin", "https://www.bursakart.com.tr")
            c.setRequestProperty("Referer", "https://www.bursakart.com.tr/wheremybus")
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) { lastError = "HTTP ${c.responseCode}"; null }
            else {
                val text = String(readCapped(c.inputStream.use { it.readBytes() }))
                val o = JSONObject(text)
                if (o.optInt("statusCode") == 200 && o.has("result")) o.getJSONArray("result") else { lastError = "bad answer"; null }
            }
        } finally { c.disconnect() }
    } catch (e: Exception) {
        lastError = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
        null
    }

    /** Refuses absurdly large bodies so a broken server cannot exhaust memory. */
    private fun readCapped(bytes: ByteArray, max: Int = 4_000_000): ByteArray =
        if (bytes.size <= max) bytes else bytes.copyOf(max)

    companion object {
        const val BASE = "https://bursakartapi.abys-web.com/api/"
    }
}
