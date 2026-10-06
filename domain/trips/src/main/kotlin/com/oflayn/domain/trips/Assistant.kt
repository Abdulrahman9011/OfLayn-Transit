package com.oflayn.domain.trips

enum class Lang { AR, TR, EN }

data class Answer(val text: String, val trips: List<Trip> = emptyList(), val lines: List<LineInfo> = emptyList(), val intent: String = "unknown")

/**
 * The built-in assistant. It runs entirely on the phone, from the data already stored there
 * (bundled network + today's stored trips). No key, no token, no download: it works the moment
 * the app opens, online or offline. It never invents a trip that is not in the local store.
 */
class OfflineAssistant(val network: Network, val engine: TripsEngine) {

    fun detectLang(q: String): Lang {
        if (q.any { it in '\u0600'..'\u06FF' }) return Lang.AR
        val t = norm(q)
        if (t.split(' ').any { it in setOf("ne", "zaman", "saat", "durak", "hat", "kac", "nerede", "nasil", "giderim", "otobus") }) return Lang.TR
        return Lang.EN
    }

    private fun t(l: Lang, ar: String, tr: String, en: String) = when (l) { Lang.AR -> ar; Lang.TR -> tr; Lang.EN -> en }

    fun answer(query: String, now: Long = System.currentTimeMillis()): Answer {
        val l = detectLang(query)
        val q = norm(query)
        val nowMin = minuteOfDay(now)
        if (q.isBlank()) return Answer(t(l, "اسألني عن أي خط أو محطة في بورصة.", "Bursa'daki herhangi bir hat veya durağı sor.", "Ask me about any Bursa line or stop."), intent = "empty")

        val line = findLine(query)
        val stop = findStop(query)
        val asksNext = listOf("بعد", "متى", "اقرب", "أقرب", "رحل", "موعد", "الساعه", "القادم", "قادم").any { q.contains(it) } || listOf("sonraki", "ne zaman", "kac dk").any { q.contains(it) } || listOf("next", "when", "upcoming").any { q.contains(it) }
        val asksStops = q.contains("محط") || q.contains("موقف") || q.contains("durak") || q.contains("stop")
        val asksLinesAt = q.contains("خطوط") || q.contains("hatlar") || q.contains("lines") || q.contains("اي خط") || q.contains("شو الخط")
        val asksCount = q.contains("كم") || q.contains("عدد") || q.contains("kac") || q.contains("how many")
        val asksPlan = q.contains("كيف اروح") || q.contains("اروح") || q.contains("اوصل") || q.contains("giderim") || q.contains("how do i get")

        if (asksPlan && line == null && stop == null) {
            return Answer(t(l, "قل لي اسم محطة الانطلاق ومحطة الوصول، مثال: كيف أروح من سيتيلر باركي إلى إمك؟", "Kalkış ve varış durağını yaz, örnek: SİTELER PARKI'dan EMEK'e nasıl giderim?", "Give me the start and the destination stop, e.g. how do I get from SITELER PARKI to EMEK?"), intent = "plan")
        }

        if (line != null) {
            val all = engine.forLine(line.code)
            val todays = all.filter { it.kind != TripKind.ESTIMATED }
            val next = all.filter { it.departMin >= nowMin - 2 }.sortedBy { it.departMin }
            if (asksCount) {
                return Answer(
                    t(l, "خط ${line.code} (${line.title}): ${all.size} رحلة محفوظة اليوم، ${line.dirs.sumOf { it.stops.size }} محطة عبر ${line.dirs.size} اتجاه.",
                        "Hat ${line.code} (${line.title}): bugün ${all.size} kayıtlı sefer, ${line.dirs.size} yönde ${line.dirs.sumOf { it.stops.size }} durak.",
                        "Line ${line.code} (${line.title}): ${all.size} trips stored today, ${line.dirs.sumOf { it.stops.size }} stops across ${line.dirs.size} directions."),
                    trips = next.take(6), lines = listOf(line), intent = "line_count",
                )
            }
            if (asksStops) {
                val d = line.dirs.maxByOrNull { it.stops.size } ?: return Answer(t(l, "لا توجد محطات محفوظة.", "Kayıtlı durak yok.", "No stops stored."), intent = "line_stops")
                return Answer(
                    t(l, "محطات خط ${line.code} (${d.dir}): ${d.stops.size} محطة — " + d.stops.take(10).joinToString(" ← ") { it.name.let { titleTr(it) } } + " …",
                        "Hat ${line.code} (${d.dir}) durakları: ${d.stops.size} durak — " + d.stops.take(10).joinToString(" ← ") { it.name.let { titleTr(it) } } + " …",
                        "Line ${line.code} (${d.dir}) stops: ${d.stops.size} — " + d.stops.take(10).joinToString(" → ") { it.name.let { titleTr(it) } } + " …"),
                    lines = listOf(line), intent = "line_stops",
                )
            }
            return when {
                next.isEmpty() && todays.isEmpty() -> Answer(
                    t(l, "لا توجد رحلات محفوظة لخط ${line.code} اليوم. افتح التطبيق مع إنترنت لتحديثها.",
                        "Bugün ${line.code} için kayıtlı sefer yok. Güncellemek için internetle aç.",
                        "No trips stored for ${line.code} today. Open the app with internet to refresh."),
                    lines = listOf(line), intent = "line_none",
                )
                next.isEmpty() -> Answer(
                    t(l, "انتهت رحلات اليوم المحفوظة لخط ${line.code} (آخرها ${todays.last().hhmm}).",
                        "${line.code} için bugünün kayıtlı seferleri bitti (son: ${todays.last().hhmm}).",
                        "Today's stored trips for ${line.code} are finished (last ${todays.last().hhmm})."),
                    trips = todays.takeLast(6), lines = listOf(line), intent = "line_done",
                )
                else -> {
                    val n = next.first()
                    val extra = next.drop(1).take(4)
                    Answer(
                        t(l, "أقرب رحلة لخط ${line.code} (${line.title}): ${n.hhmm} من ${n.stopName.let { titleTr(it) }} — بعد ${(n.departMin - nowMin).coerceAtLeast(0)} دقيقة." + if (n.plate.isNotBlank()) " المركبة ${n.plate}." else "",
                            "${line.code} (${line.title}) sonraki sefer: ${n.hhmm}, ${n.stopName.let { titleTr(it) }} kalkışlı — ${(n.departMin - nowMin).coerceAtLeast(0)} dk sonra." + if (n.plate.isNotBlank()) " Araç ${n.plate}." else "",
                            "Next trip on ${line.code} (${line.title}): ${n.hhmm} from ${n.stopName.let { titleTr(it) }} — in ${(n.departMin - nowMin).coerceAtLeast(0)} min." + if (n.plate.isNotBlank()) " Vehicle ${n.plate}." else ""),
                        trips = (listOf(n) + extra), lines = listOf(line), intent = "line_next",
                    )
                }
            }
        }

        if (stop != null) {
            val at = engine.forStop(stop.id).take(8)
            val linesHere = network.linesAtStop(stop.id)
            if (asksLinesAt || at.isEmpty()) {
                return Answer(
                    t(l, "محطة ${stop.name.let { titleTr(it) }}: يخدمها ${linesHere.size} خط — " + linesHere.take(12).joinToString("، ") { it.code } + if (at.isEmpty()) ". لا توجد رحلات محفوظة لها اليوم." else ". أقرب رحلة ${at.first().hhmm} على خط ${at.first().lineCode}.",
                        "${stop.name.let { titleTr(it) }} durağı: ${linesHere.size} hat geçiyor — " + linesHere.take(12).joinToString(", ") { it.code } + if (at.isEmpty()) ". Bugün kayıtlı sefer yok." else ". Sonraki sefer ${at.first().hhmm}, hat ${at.first().lineCode}.",
                        "${stop.name.let { titleTr(it) }} stop: ${linesHere.size} lines — " + linesHere.take(12).joinToString(", ") { it.code } + if (at.isEmpty()) ". No trips stored for it today." else ". Next trip ${at.first().hhmm} on ${at.first().lineCode}."),
                    trips = at, lines = linesHere.take(12), intent = "stop_lines",
                )
            }
            return Answer(
                t(l, "محطة ${stop.name.let { titleTr(it) }}: ${at.size} رحلة قادمة — " + at.take(4).joinToString("، ") { "${it.lineCode} ${it.hhmm}" } + ".",
                    "${stop.name.let { titleTr(it) }}: ${at.size} yaklaşan sefer — " + at.take(4).joinToString(", ") { "${it.lineCode} ${it.hhmm}" } + ".",
                    "${stop.name.let { titleTr(it) }}: ${at.size} upcoming trips — " + at.take(4).joinToString(", ") { "${it.lineCode} ${it.hhmm}" } + "."),
                trips = at, intent = "stop_arrivals",
            )
        }

        if (asksLinesAt || q.contains("اقرب محطه") || q.contains("en yakin durak") || q.contains("nearest stop")) {
            return Answer(
                t(l, "اكتب اسم المحطة أو رقم الخط، أو استخدم زر الموقع في الشاشة الرئيسية لإظهار أقرب المحطات.",
                    "Durak adını veya hat numarasını yaz, ya da ana ekrandaki konum düğmesini kullan.",
                    "Type a stop name or a line number, or use the location button on the home screen."),
                intent = "help",
            )
        }

        return Answer(
            t(l, "أنا مساعد التطبيق، أعمل من البيانات المحفوظة على هاتفك. جرّب: «31A» أو «محطات 31A» أو «موعد 31A» أو «شو الخطوط على محطة 1420».",
                "Ben uygulamanın asistanıyım, telefondaki verilerle çalışırım. Dene: «31A», «31A durakları», «31A sonraki sefer».",
                "I am the app assistant and I work from the data stored on your phone. Try: \"31A\", \"31A stops\", \"next 31A\"."),
            intent = "help",
        )
    }

    /** Finds a line code inside free text: 31A, B31A, 132İ, 99 … */
    fun findLine(query: String): LineInfo? {
        val tokens = query.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        val direct = tokens.mapNotNull { tok -> network.line(tok) }.firstOrNull()
        if (direct != null) return direct
        val m = Regex("([0-9]{1,4}\\s?-?\\s?[A-Za-zÇĞİÖŞÜ]{0,2})").findAll(query).map { it.value.replace(" ", "").replace("-", "") }.toList()
        for (cand in m) {
            network.line(cand)?.let { return it }
        }
        return null
    }

    fun findStop(query: String): StopRef? {
        val cleaned = query.replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        val words = norm(cleaned).split(' ').filter { it.length >= 4 && it !in STOP_STOPWORDS }
        if (words.isEmpty()) return null
        val joined = words.joinToString(" ")
        network.searchStops(joined, 1).firstOrNull()?.let { return it }
        for (w in words.sortedByDescending { it.length }) network.searchStops(w, 1).firstOrNull()?.let { return it }
        return null
    }

    private val STOP_STOPWORDS = setOf(
        "محطه", "محطات", "موقف", "مواقف", "الخطوط", "الخط", "خطوط", "موعد", "مواعيد", "رحله", "رحلات", "اليوم",
        "بعد", "كم", "شو", "ايش", "وين", "كيف", "اروح", "من", "الى", "على", "عن", "في", "هل", "هذا", "هذه",
        "durak", "duraklar", "hat", "hatlar", "sefer", "sonraki", "bugun", "kac", "nerede", "nasil", "giderim",
        "stop", "stops", "line", "lines", "next", "today", "when", "how", "many", "the", "from", "to",
    )
}
