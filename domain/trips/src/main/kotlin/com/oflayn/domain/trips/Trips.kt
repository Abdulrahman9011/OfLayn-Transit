package com.oflayn.domain.trips

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val ISTANBUL: ZoneId = ZoneId.of("Europe/Istanbul")

fun dayIsoOf(ms: Long): String = LocalDate.ofInstant(Instant.ofEpochMilli(ms), ISTANBUL).toString()

fun minuteOfDay(ms: Long): Int {
    val t = java.time.LocalTime.ofInstant(Instant.ofEpochMilli(ms), ISTANBUL)
    return t.hour * 60 + t.minute
}

fun hhmm(min: Int): String = "%02d:%02d".format(((min % 1440) + 1440) % 1440 / 60, ((min % 60) + 60) % 60)

/**
 * OFFICIAL  = published timetable transcribed from the official operator (e.g. line 31A).
 * OBSERVED  = a real trip the official live service reported today (plate + passing time).
 * ESTIMATED = derived from the observed headway of the same line; always labelled as an estimate.
 */
enum class TripKind { OFFICIAL, OBSERVED, ESTIMATED }

data class Trip(
    val lineCode: String,
    val lineTitle: String,
    val routeId: Int,
    val dir: String,
    val stopId: Int,
    val stopName: String,
    val departMin: Int,
    val plate: String,
    val observedAtMs: Long,
    val kind: TripKind,
) {
    val hhmm: String get() = com.oflayn.domain.trips.hhmm(departMin)

    fun toJson(): J = jobj(
        "c" to js(lineCode), "t" to js(lineTitle), "r" to jn(routeId), "d" to js(dir),
        "si" to jn(stopId), "sn" to js(stopName), "m" to jn(departMin), "p" to js(plate),
        "o" to jn(observedAtMs), "k" to js(kind.name),
    )

    companion object {
        fun fromJson(j: J): Trip = Trip(
            j.at("c").str(), j.at("t").str(), j.at("r").int(), j.at("d").str(),
            j.at("si").int(), j.at("sn").str(), j.at("m").int(), j.at("p").str(),
            j.at("o").num().toLong(),
            runCatching { TripKind.valueOf(j.at("k").str("OBSERVED")) }.getOrDefault(TripKind.OBSERVED),
        )
    }
}

/** Everything the app collected for one calendar day, stored on the phone. */
data class DaySnapshot(val dayIso: String, val builtAtMs: Long, val source: String, val trips: List<Trip>) {
    fun sorted(): DaySnapshot = copy(trips = trips.sortedWith(compareBy({ it.departMin }, { it.lineCode })))

    fun toJson(): J = jobj(
        "day" to js(dayIso), "built" to jn(builtAtMs), "source" to js(source),
        "trips" to jarr(trips.map { it.toJson() }),
    )

    companion object {
        fun fromJson(j: J): DaySnapshot? {
            if (j.at("trips").isNull && j.at("day").isNull) return null
            return DaySnapshot(
                j.at("day").str(), j.at("built").num().toLong(), j.at("source").str(),
                j.at("trips").items().map { Trip.fromJson(it) },
            )
        }
    }
}

enum class FreshLevel { FRESH, STALE, EXPIRED, EMPTY }

data class Freshness(val level: FreshLevel, val ageMs: Long, val expiresAtMs: Long, val ageLabel: String)

/** "حسب المدة": a snapshot stays usable for [ttlMs]; after that it is stale, then expired. */
class FreshnessPolicy(val ttlMs: Long = 6 * 3_600_000L) {
    fun of(builtAtMs: Long, nowMs: Long): Freshness {
        if (builtAtMs <= 0L) return Freshness(FreshLevel.EMPTY, -1L, 0L, "")
        val age = nowMs - builtAtMs
        val left = builtAtMs + ttlMs - nowMs
        val level = when {
            left > 0L -> FreshLevel.FRESH
            left > -ttlMs -> FreshLevel.STALE
            else -> FreshLevel.EXPIRED
        }
        val mins = age / 60_000L
        val label = when {
            mins < 1L -> "الآن"
            mins < 60L -> "$mins دقيقة"
            mins < 1440L -> "${mins / 60} س ${mins % 60} د"
            else -> "${mins / 1440} يوم"
        }
        return Freshness(level, age, builtAtMs + ttlMs, label)
    }
}

/** Persistence is injected (a file on Android, memory in tests) so the core stays Android-free. */
class TripStore(private val write: (String) -> Unit, private val read: () -> String?) {
    fun save(snapshot: DaySnapshot) = write(Json.write(snapshot.toJson()))
    fun load(): DaySnapshot? {
        val raw = read() ?: return null
        if (raw.isBlank()) return null
        return runCatching { DaySnapshot.fromJson(Json.parse(raw)) }.getOrNull()
    }
}

/** Source of today's real trips. The Android layer implements it over the official live service. */
interface LiveTripsSource {
    /**
     * Real trips reported today for one line. [firstStops] are the first stop of each direction.
     * Must return an empty list (never throw) when the network or the service is unavailable.
     */
    suspend fun line(code: String, title: String, routeId: Int, firstStops: List<Pair<String, StopRef>>): List<Trip>
}
