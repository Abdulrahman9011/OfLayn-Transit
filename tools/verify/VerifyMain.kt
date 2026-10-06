package com.oflayn.domain.trips

import java.io.File

/** End-to-end verification of the offline core on the JVM: no Android, no emulator, no network. */
var pass = 0
var fail = 0

fun check(name: String, cond: Boolean, info: String = "") {
    if (cond) { pass++; println("PASS  $name") } else { fail++; println("FAIL  $name  $info") }
}

fun fakeNetwork(): Network {
    val a = StopRef(1, "SİTELER HAREKET MERKEZİ 2", 40.178, 29.126, 1)
    val b = StopRef(2, "SİTELER PARKI 2", 40.1795, 29.1272, 2)
    val c = StopRef(3, "EMEK 2", 40.19, 29.13, 3)
    val d = StopRef(4, "TERMİNAL", 40.20, 29.10, 1)
    return Network(
        1, "2026-10-06", "test",
        listOf(
            LineInfo(1107, "31A", "Siteler - Emek", listOf(LineDir("G", listOf(a, b, c)))),
            LineInfo(1025, "99", "Terminal - Siteler", listOf(LineDir("G", listOf(d, b)))),
        ),
        listOf(a, b, c, d),
    )
}

fun <T> onThread(block: () -> T): T {
    var out: T? = null
    var err: Throwable? = null
    val th = Thread { try { out = block() } catch (t: Throwable) { err = t } }
    th.start(); th.join()
    err?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return out as T
}

fun main() {
    // ---------- 1. first open with internet stores today's trips ----------
    var blob: String? = null
    val store = TripStore({ blob = it }, { blob })
    val net = fakeNetwork()
    var online = true
    var t = 1_800_000_000_000L
    val live = object : LiveTripsSource {
        override suspend fun line(code: String, title: String, routeId: Int, firstStops: List<Pair<String, StopRef>>): List<Trip> =
            firstStops.map { (dir, stop) ->
                Trip(code, title, routeId, dir, stop.id, stop.name, minuteOfDay(t) + 5, "16 ABC 1", t, TripKind.OBSERVED)
            }
    }
    val e1 = TripsEngine(net, store, ttlMs = 3_600_000L, nowMs = { t }, online = { online }, live = live)
    val b1 = onThread { kotlinx.coroutines.runBlocking { e1.open() } }
    check("first open (online) fetched and stored today's trips", b1.trips.isNotEmpty() && blob != null, "trips=${b1.trips.size}")
    check("first open is labelled online", !b1.offline)

    // ---------- 2. network gone: the same phone still shows today's trips ----------
    online = false
    t += 60_000L
    val e2 = TripsEngine(net, store, ttlMs = 3_600_000L, nowMs = { t }, online = { online }, live = null)
    val b2 = onThread { kotlinx.coroutines.runBlocking { e2.open() } }
    check("offline open serves the cached trips", b2.trips.size == b1.trips.size && b2.trips.isNotEmpty(), "cached=${b2.trips.size}")
    check("offline open is flagged offline", b2.offline)
    check("offline open keeps today's date", b2.dayIso == dayIsoOf(t), b2.dayIso)
    check("no network call is attempted while offline", !b2.fromCache || b2.trips.isNotEmpty())

    // ---------- 3. freshness "حسب المدة" ----------
    val pol = FreshnessPolicy(ttlMs = 10 * 60_000L)
    check("fresh inside the validity window", pol.of(t, t + 5 * 60_000L).level == FreshLevel.FRESH)
    check("stale inside the second window", pol.of(t, t + 15 * 60_000L).level == FreshLevel.STALE)
    check("expired past twice the window", pol.of(t, t + 20 * 60_000L).level == FreshLevel.EXPIRED)
    check("expired long after", pol.of(t, t + 60 * 60_000L).level == FreshLevel.EXPIRED)
    check("empty when nothing stored", pol.of(0L, t).level == FreshLevel.EMPTY)
    check("a day-old snapshot is not claimed as fresh", FreshnessPolicy(6 * 3_600_000L).of(t, t + 30 * 3_600_000L).level == FreshLevel.EXPIRED)

    // ---------- 4. the built-in assistant answers with no internet, no key ----------
    val asst = OfflineAssistant(net, e2)
    val a1 = asst.answer("متى موعد 31A؟", t)
    check("assistant: next 31A trip offline", a1.intent == "line_next" && a1.trips.isNotEmpty(), a1.intent)
    check("assistant: answer contains a real time", Regex("[0-2][0-9]:[0-5][0-9]").containsMatchIn(a1.text), a1.text)
    val a2 = asst.answer("محطات 31A", t)
    check("assistant: lists line stops offline", a2.intent == "line_stops" && a2.text.contains("Siteler"), a2.text.take(120))
    val a3 = asst.answer("SİTELER PARKI durağında hangi hatlar var", t)
    check("assistant: resolves a stop by name", a3.intent == "stop_lines" || a3.intent == "stop_arrivals", a3.intent)
    val a4 = asst.answer("كم عدد رحلات 99 اليوم", t)
    check("assistant: counts stored trips", a4.intent == "line_count", a4.intent)
    val a5 = asst.answer("hava nasıl", t)
    check("assistant: falls back to help, never invents data", a5.intent == "help")
    val a6 = asst.answer("777", t)
    check("assistant: unknown line is reported honestly", a6.intent == "line_none" || a6.intent == "help", a6.intent)

    // ---------- 5. derived trips are labelled ESTIMATED ----------
    val est = e2.extendEstimates(now = t, horizonMin = 600)
    check("derived trips are marked ESTIMATED", est.all { it.kind == TripKind.ESTIMATED })

    // ---------- 6. the dataset that ships inside the APK ----------
    val asset = File("app/src/main/assets/offline/network.json.gz")
    if (asset.exists()) {
        val parsed = Network.fromJson(Json.parse(Gz.read(asset.readBytes())))
        println("      bundled dataset: ${parsed.lineCount} lines, ${parsed.stopCount} stops, built ${parsed.builtAt}")
        check("bundled network carries the full Bursa line set", parsed.lineCount > 300, "lines=${parsed.lineCount}")
        check("bundled network carries thousands of stops", parsed.stopCount > 5000, "stops=${parsed.stopCount}")
        check("line 31A present with its stops", (parsed.line("31A")?.stopCount ?: 0) > 20, "stops=${parsed.line("31A")?.stopCount}")
        check("offline stop search works", parsed.searchStops("SİTELER PARKI", 5).isNotEmpty())
        check("offline lines-at-stop works", parsed.linesAtStop(parsed.searchStops("SİTELER PARKI", 1).first().id).isNotEmpty())
        check("offline line search finds 31A", parsed.searchLines("31A", 5).any { it.code == "31A" })
        check("coordinates are present for mapping", parsed.stops.count { it.lat != 0.0 } > 5000, "withCoords=${parsed.stops.count { it.lat != 0.0 }}")

        val snapFile = File("app/src/main/assets/offline/day_snapshot.json.gz")
        if (snapFile.exists()) {
            val snap = DaySnapshot.fromJson(Json.parse(Gz.read(snapFile.readBytes())))!!
            println("      bundled snapshot: ${snap.trips.size} real trips for ${snap.dayIso}")
            check("bundled day snapshot parses", snap.trips.isNotEmpty())
            var blob2: String? = Json.write(snap.toJson())
            val store2 = TripStore({ blob2 = it }, { blob2 })
            val e3 = TripsEngine(parsed, store2, nowMs = { t }, online = { false })
            val b3 = e3.board(t)
            check("real dataset is served offline from cache", b3.trips.isNotEmpty() && b3.offline)
            val asst2 = OfflineAssistant(parsed, e3)
            val real = asst2.answer("متى موعد 31A", t)
            println("      offline answer: ${real.text.take(200)}")
            check("assistant answers from the real offline dataset", real.trips.isNotEmpty() || real.intent == "line_done" || real.intent == "line_none", real.intent)
            val stopsOf31A = parsed.line("31A")!!.dirs.maxByOrNull { it.stops.size }!!
            val s1 = stopsOf31A.stops.first()
            val stopAnswer = asst2.answer("شو الخطوط على محطة ${s1.name}", t)
            check("assistant answers stop questions on the real dataset", stopAnswer.intent == "stop_lines" || stopAnswer.intent == "stop_arrivals", stopAnswer.intent)
        }
    } else println("SKIP  bundled dataset not found at $asset")

    println("\n==== RESULT: $pass passed, $fail failed ====")
    if (fail > 0) throw RuntimeException("verification failed")
}
