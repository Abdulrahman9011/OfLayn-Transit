package com.oflayn.domain.trips

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun codeKey(s: String): String = s.filter { it.isLetterOrDigit() }.uppercase()

/** Normalises Turkish / Arabic text for search: case, accents, diacritics. */
fun norm(s: String): String {
    val t = s.lowercase()
    val sb = StringBuilder(t.length)
    for (c in t) {
        when (c) {
            'ç' -> sb.append('c'); 'ğ' -> sb.append('g'); 'ı' -> sb.append('i'); 'İ' -> sb.append('i')
            'ö' -> sb.append('o'); 'ş' -> sb.append('s'); 'ü' -> sb.append('u'); 'â' -> sb.append('a')
            'î' -> sb.append('i'); 'û' -> sb.append('u'); 'é' -> sb.append('e')
            '\u064B', '\u064C', '\u064D', '\u064E', '\u064F', '\u0650', '\u0651', '\u0652' -> {}
            'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
            'ى' -> sb.append('ي')
            'ة' -> sb.append('ه')
            else -> if (c.isLetterOrDigit() || c == ' ') sb.append(c)
        }
    }
    return sb.toString().trim()
}

private val TR_LOCALE = java.util.Locale.forLanguageTag("tr")

/** Turkish-safe "Sentence case" for the ALL-CAPS stop names the official service returns. */
fun titleTr(s: String): String {
    if (s.isEmpty()) return s
    val lower = s.lowercase(TR_LOCALE)
    return lower.replaceFirstChar { it.toString().uppercase(TR_LOCALE) }
}

fun distM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(sqrt(a))
}

data class StopRef(val id: Int, val name: String, val lat: Double, val lon: Double, val seq: Int)

data class LineDir(val dir: String, val stops: List<StopRef>)

data class LineInfo(val routeId: Int, val code: String, val title: String, val dirs: List<LineDir>) {
    val key: String get() = codeKey(code)
    val stopCount: Int get() = dirs.sumOf { it.stops.size }
    fun firstStop(dir: String): StopRef? = dirs.firstOrNull { it.dir == dir }?.stops?.minByOrNull { it.seq }
    fun firstStops(): List<Pair<String, StopRef>> = dirs.mapNotNull { d -> d.stops.minByOrNull { it.seq }?.let { d.dir to it } }
    fun hasStop(id: Int): Boolean = dirs.any { d -> d.stops.any { it.id == id } }
    fun dirWith(id: Int): LineDir? = dirs.firstOrNull { d -> d.stops.any { it.id == id } }
}

/**
 * The complete official Bursa bus network, bundled inside the APK so the app shows content on the
 * very first open, with no internet and no download. Shape on disk (gzipped):
 *   {"v":1,"built":"…","source":"…","stops":[[id,name,lat,lon],…],"lines":[{"id":…,"code":"…","title":"…","dirs":[{"d":"G","ids":[…]}]}]}
 */
class Network(
    val version: Int,
    val builtAt: String,
    val source: String,
    val lines: List<LineInfo>,
    val stops: List<StopRef>,
) {
    private val byId: Map<Int, LineInfo> = lines.associateBy { it.routeId }
    private val byKey: Map<String, LineInfo> = LinkedHashMap<String, LineInfo>().also { m -> lines.forEach { l -> if (!m.containsKey(l.key)) m[l.key] = l } }
    private val stopById: Map<Int, StopRef> = stops.associateBy { it.id }
    private val stopNorm: List<Pair<String, StopRef>> = stops.map { norm(it.name) to it }
    private val stopLines: Map<Int, List<LineInfo>> = HashMap<Int, MutableList<LineInfo>>().also { m ->
        lines.forEach { l -> l.dirs.forEach { d -> d.stops.forEach { s -> m.getOrPut(s.id) { ArrayList() }.let { if (!it.contains(l)) it.add(l) } } } }
    }

    val stopCount: Int get() = stops.size
    val lineCount: Int get() = lines.size

    fun line(routeId: Int): LineInfo? = byId[routeId]
    fun line(code: String): LineInfo? = byKey[codeKey(code)]
    fun stop(id: Int): StopRef? = stopById[id]
    fun linesAtStop(stopId: Int): List<LineInfo> = stopLines[stopId] ?: emptyList()

    /** Ranked search over line codes and line titles. */
    fun searchLines(q: String, limit: Int = 40): List<LineInfo> {
        val k = codeKey(q); val n = norm(q)
        if (k.isEmpty() && n.isEmpty()) return lines.take(limit)
        val exact = ArrayList<LineInfo>(); val pre = ArrayList<LineInfo>(); val other = ArrayList<LineInfo>()
        for (l in lines) {
            when {
                l.key == k || norm(l.code) == n -> exact.add(l)
                k.isNotEmpty() && l.key.startsWith(k) -> pre.add(l)
                (n.length >= 3 && norm(l.title).contains(n)) || (k.length >= 2 && l.key.contains(k)) -> other.add(l)
            }
        }
        return (exact + pre.sortedBy { it.code } + other.sortedBy { it.code }).take(limit)
    }

    /** Ranked search over stop names (accent- and diacritic-insensitive). */
    fun searchStops(q: String, limit: Int = 40): List<StopRef> {
        val n = norm(q)
        if (n.length < 2) return emptyList()
        val starts = ArrayList<StopRef>(); val contains = ArrayList<StopRef>()
        for ((sn, s) in stopNorm) {
            if (sn.startsWith(n)) { if (starts.size < limit) starts.add(s) } else if (contains.size < limit && sn.contains(n)) contains.add(s)
        }
        return (starts + contains).take(limit)
    }

    fun near(lat: Double, lon: Double, limit: Int = 15, maxM: Double = 3000.0): List<Pair<StopRef, Double>> =
        stops.asSequence()
            .filter { it.lat != 0.0 && it.lon != 0.0 }
            .map { it to distM(lat, lon, it.lat, it.lon) }
            .filter { it.second <= maxM }
            .sortedBy { it.second }
            .take(limit)
            .toList()

    /** Lines that serve both stops directly, in that order, in the same direction. */
    fun direct(a: StopRef, b: StopRef): List<LineInfo> =
        linesAtStop(a.id).filter { l ->
            l.dirs.any { d ->
                val i = d.stops.indexOfFirst { it.id == a.id }
                val j = d.stops.indexOfFirst { it.id == b.id }
                i >= 0 && j >= 0 && i < j
            }
        }

    companion object {
        fun fromJson(j: J): Network {
            val stops = j.at("stops").items().map { a -> StopRef(a.at(0).int(), a.at(1).str(), a.at(2).num(), a.at(3).num(), 0) }
            val byId = stops.associateBy { it.id }
            val lines = j.at("lines").items().map { l ->
                LineInfo(
                    l.at("id").int(), l.at("code").str(), l.at("title").str(),
                    l.at("dirs").items().map { d ->
                        LineDir(d.at("d").str("G"), d.at("ids").items().mapIndexed { i, x ->
                            val id = x.int()
                            (byId[id] ?: StopRef(id, "", 0.0, 0.0, 0)).copy(seq = i + 1)
                        })
                    },
                )
            }
            return Network(j.at("v").int(1), j.at("built").str(), j.at("source").str(), lines, stops)
        }
    }
}
