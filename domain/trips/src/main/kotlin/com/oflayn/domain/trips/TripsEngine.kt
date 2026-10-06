package com.oflayn.domain.trips

data class Board(
    val day: DaySnapshot?,
    val trips: List<Trip>,
    val freshness: Freshness,
    val fromCache: Boolean,
    val offline: Boolean,
    val message: String,
    val dayIso: String,
)

data class RefreshReport(val linesTried: Int, val linesAnswered: Int, val trips: Int, val saved: Boolean, val error: String = "")

/**
 * Offline-first day board.
 *
 *  1. first open with internet  -> pull today's real trips for every line, store them on the phone
 *  2. later opens               -> show the stored day immediately, refresh only when the TTL lapsed
 *  3. no internet               -> serve the stored day, flagged offline + with its age
 */
class TripsEngine(
    val network: Network,
    private val store: TripStore,
    val ttlMs: Long = 6 * 3_600_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val online: () -> Boolean = { false },
    private val live: LiveTripsSource? = null,
) {
    val policy = FreshnessPolicy(ttlMs)
    private var cached: DaySnapshot? = null

    fun stored(): DaySnapshot? = cached ?: store.load()?.also { cached = it }

    fun freshness(now: Long = nowMs()): Freshness = policy.of(stored()?.builtAtMs ?: 0L, now)

    /** Pure cache read: never touches the network. Used to render the screen instantly. */
    fun board(now: Long = nowMs()): Board {
        val day = stored()
        val today = dayIsoOf(now)
        val f = policy.of(day?.builtAtMs ?: 0L, now)
        val stale = day != null && day.dayIso != today
        val trips = day?.trips.orEmpty()
        val msg = when {
            day == null -> "لا توجد رحلات محفوظة بعد — افتح التطبيق مع إنترنت مرة واحدة."
            stale -> "الرحلات المحفوظة تعود ليوم ${day.dayIso}."
            f.level == FreshLevel.FRESH -> ""
            else -> "آخر تحديث قبل ${f.ageLabel}."
        }
        return Board(day, trips, if (stale) Freshness(FreshLevel.EXPIRED, f.ageMs, f.expiresAtMs, f.ageLabel) else f, true, !online(), msg, day?.dayIso ?: today)
    }

    /** Entry point for the UI: instant cache board, plus a refresh when it is worth doing. */
    suspend fun open(force: Boolean = false, onProgress: (Int, Int) -> Unit = { _, _ -> }): Board {
        val now = nowMs()
        val today = dayIsoOf(now)
        val day = stored()
        val usable = day != null && day.dayIso == today && day.trips.isNotEmpty()
        val f = policy.of(day?.builtAtMs ?: 0L, now)
        val shouldRefresh = online() && (force || !usable || f.level != FreshLevel.FRESH)
        if (shouldRefresh) refresh(onProgress)
        return board(nowMs())
    }

    /** Pulls today's real trips for every line in the bundled network and merges them into today's snapshot. */
    suspend fun refresh(onProgress: (Int, Int) -> Unit = { _, _ -> }): RefreshReport {
        val src = live ?: return RefreshReport(0, 0, 0, false, "no live source")
        if (!online()) return RefreshReport(0, 0, 0, false, "offline")
        val now = nowMs()
        val today = dayIsoOf(now)
        val base = stored()?.takeIf { it.dayIso == today }?.trips.orEmpty()
        val merged = LinkedHashMap<String, Trip>()
        // a trip is identified by line + direction + first stop + passing minute
        fun keyOf(t: Trip) = "${t.routeId}|${t.dir}|${t.stopId}|${t.departMin}"
        base.forEach { merged[keyOf(it)] = it }

        val all = network.lines
        var answered = 0
        all.forEachIndexed { i, line ->
            val got = runCatching { src.line(line.code, line.title, line.routeId, line.firstStops()) }.getOrDefault(emptyList())
            if (got.isNotEmpty()) answered++
            got.forEach { merged[keyOf(it)] = it }
            if (i % 10 == 0 || i == all.lastIndex) onProgress(i + 1, all.size)
        }
        val trips = merged.values.toList()
        if (trips.isEmpty()) return RefreshReport(all.size, answered, 0, false, "no data returned")
        val snap = DaySnapshot(today, now, network.source, trips).sorted()
        store.save(snap); cached = snap
        return RefreshReport(all.size, answered, trips.size, true)
    }

    /**
     * Fills the rest of the day for a line from its own observed headway. Every added trip is ESTIMATED
     * so the UI can label it; nothing here is presented as an official timetable.
     */
    fun extendEstimates(now: Long = nowMs(), horizonMin: Int = 240, maxPerLine: Int = 24): List<Trip> {
        val day = stored() ?: return emptyList()
        val nowMin = minuteOfDay(now)
        val out = ArrayList<Trip>()
        day.trips.filter { it.kind == TripKind.OBSERVED }.groupBy { it.routeId }.forEach { (_, list) ->
            val mins = list.map { it.departMin }.distinct().sorted()
            if (mins.size < 2) return@forEach
            val gaps = mins.zipWithNext { a, b -> b - a }.filter { it in 3..120 }
            if (gaps.isEmpty()) return@forEach
            val step = gaps.sorted()[gaps.size / 2]
            val ref = list.first()
            var t = (mins.last() + step)
            var n = 0
            while (t <= nowMin + horizonMin && n < maxPerLine) {
                if (t > nowMin) out.add(ref.copy(departMin = t, plate = "", observedAtMs = now, kind = TripKind.ESTIMATED))
                t += step; n++
            }
        }
        return out
    }

    /** Today's board for one line, cache-first. */
    fun forLine(code: String, now: Long = nowMs()): List<Trip> {
        val day = stored() ?: return emptyList()
        val key = codeKey(code)
        return day.trips.filter { codeKey(it.lineCode) == key }.sortedBy { it.departMin }
    }

    fun forStop(stopId: Int, now: Long = nowMs()): List<Trip> {
        val day = stored() ?: return emptyList()
        val nowMin = minuteOfDay(now)
        return day.trips.filter { it.stopId == stopId && it.departMin >= nowMin - 2 }.sortedBy { it.departMin }
    }

    /** The next real trips anywhere in the network, cache-first (what "رحلات اليوم" opens with). */
    fun upcoming(now: Long = nowMs(), limit: Int = 400): List<Trip> {
        val day = stored() ?: return emptyList()
        val nowMin = minuteOfDay(now)
        return day.trips.filter { it.departMin >= nowMin - 2 }.sortedWith(compareBy({ it.departMin }, { it.lineCode })).take(limit)
    }
}
