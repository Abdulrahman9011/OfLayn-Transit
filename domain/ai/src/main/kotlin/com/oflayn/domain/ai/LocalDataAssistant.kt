package com.oflayn.domain.ai

import com.oflayn.core.model.BursaCardType
import com.oflayn.core.model.Freshness

enum class Lang { AR, TR, EN }

object LanguageDetector {
    private val trChars = Regex("[çğıöşüÇĞİÖŞÜ]")
    private val trWords = Regex("\\b(durak|hat|ücret|ucret|bakiye|nerede|yakın|yakin|nasıl|nasil|otobüs|otobus|kart|git)\\b", RegexOption.IGNORE_CASE)
    fun detect(text: String): Lang = when {
        text.any { it in '\u0600'..'\u06FF' } -> Lang.AR
        trChars.containsMatchIn(text) || trWords.containsMatchIn(text) -> Lang.TR
        else -> Lang.EN
    }
}

/**
 * Rule-based assistant. It only orchestrates tools and formats their output; it never produces a fact by itself.
 * Used when no LLM is reachable/installed, and as the grounded fallback of the router.
 */
class LocalDataAssistant(private val tools: ToolRegistry) : AiProvider {
    override val id = "local-data-assistant"
    override suspend fun isAvailable() = true

    private fun t(l: Lang, ar: String, tr: String, en: String) = when (l) { Lang.AR -> ar; Lang.TR -> tr; Lang.EN -> en }

    private val cardKeywords: List<Pair<List<String>, BursaCardType>> = listOf(
        listOf("طالب", "ogrenci", "student") to BursaCardType.STUDENT,
        listOf("مخفض", "indirimli", "discounted") to BursaCardType.DISCOUNTED,
        listOf("كامل", "tam ", "full") to BursaCardType.FULL,
    )
    private val tariffKeywords: List<Pair<List<String>, Int>> = listOf(
        listOf("بورصاراي", "bursaray", "bursa ray", "metro") to 1,
        listOf("ترام", "tramvay", "tram") to 13,
    )

    override suspend fun generate(request: AiRequest): AiResponse {
        val q = request.text.trim()
        val n = norm(q)
        val lang = LanguageDetector.detect(q)
        val used = ArrayList<String>()

        suspend fun call(name: String, args: Map<String, String> = emptyMap()): ToolResult { used += name; return tools.call(name, args) }
        fun render(r: ToolResult): String {
            if (!r.ok) return t(lang, "تعذّر تنفيذ الطلب: ${r.error}", "İstek tamamlanamadı: ${r.error}", "Request failed: ${r.error}")
            if (r.data.startsWith("NO_DATA")) return t(lang, "لا أملك بيانات كافية للإجابة عن هذا السؤال.", "Bu soruyu yanıtlamak için yeterli verim yok.", "I don't have enough data to answer this.") + "\n(${r.data.removePrefix("NO_DATA: ")})"
            val tag = if (r.freshness == Freshness.UNAVAILABLE) "" else "\n[${r.source} · ${r.freshness}]"
            return r.data + tag
        }
        fun done(text: String, grounded: Boolean = true) = AiResponse(text, id, used.toList(), grounded)

        // Trip planning: "من A الى B", "A'dan B'ye", "from A to B"
        val trip = Regex("من\\s+(.+?)\\s+(?:الى|إلى)\\s+(.+)").find(q)
            ?: Regex("(\\S+?)['’](?:den|dan|ten|tan)\\s+(\\S+?)['’](?:ye|ya|e|a|ne|na)").find(q)
            ?: Regex("from\\s+(.+?)\\s+to\\s+(.+)", RegexOption.IGNORE_CASE).find(q)
        if (trip != null) {
            val o = trip.groupValues[1].trim(); val d = trip.groupValues[2].trim().trimEnd('?', '؟', '.')
            return done(render(call("planTrip", mapOf("origin" to o, "destination" to d))))
        }
        if (listOf("اقرب", "yakin", "nearest", "nearby", "closest").any { n.contains(norm(it)) }) {
            return done(render(call("findNearbyStops")))
        }
        if (listOf("اجرة", "الاجرة", "اجره", "قديش", "سعر", "ucret", "fiyat", "fare", "price", "cost").any { n.contains(norm(it)) }) {
            val card = cardKeywords.firstOrNull { (k, _) -> k.any { n.contains(norm(it)) } }?.second
            val tariff = tariffKeywords.firstOrNull { (k, _) -> k.any { n.contains(norm(it)) } }?.second
            if (tariff == null) {
                return done(t(lang,
                    "حدّد وسيلة النقل (BursaRay أو ترام) أو رقم التعرفة حتى أحسب الأجرة من الجدول الرسمي.",
                    "Ücreti resmi tablodan hesaplamam için aracı (BursaRay veya tramvay) ya da tarife numarasını belirtin.",
                    "Tell me the transport (BursaRay or tram) or the tariff number so I can compute the fare from the official table."), grounded = false)
            }
            val args = buildMap { put("tariffNo", tariff.toString()); card?.let { put("cardType", it.name) } }
            return done(render(call("calculateFare", args)))
        }
        if (listOf("رصيد", "bakiye", "balance").any { n.contains(norm(it)) }) return done(render(call("getBursaKartStatus")))
        if (listOf("معاملات", "islem", "transaction", "history").any { n.contains(norm(it)) }) return done(render(call("getBursaKartTransactions")))
        if (listOf("تنبيه", "تنبيهات", "uyari", "duyuru", "alert", "disruption").any { n.contains(norm(it)) }) return done(render(call("getServiceAlerts")))
        val lineNo = Regex("(?<![\\p{L}])(?:خط|hat|line|route)\\s*([A-Za-z0-9/]+)", RegexOption.IGNORE_CASE).find(q)?.groupValues?.get(1)
        if (lineNo != null) return done(render(call("searchRoutes", mapOf("query" to lineNo))))
        if (listOf("محطة", "durak", "stop", "station").any { n.contains(norm(it)) }) {
            val term = q.split(" ").lastOrNull { it.length > 2 }
            if (term != null) return done(render(call("searchStops", mapOf("query" to term))))
        }
        return done(t(lang, AiModelRouter.NO_DATA_AR, "Bu soruyu yanıtlamak için yeterli verim yok.", "I don't have enough data to answer this."), grounded = false)
    }
}
