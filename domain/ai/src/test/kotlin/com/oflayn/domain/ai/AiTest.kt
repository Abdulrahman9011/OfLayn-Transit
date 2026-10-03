package com.oflayn.domain.ai

import com.oflayn.core.model.BursaCardType
import com.oflayn.core.model.Freshness
import com.oflayn.core.model.GeoPoint
import com.oflayn.core.model.Route
import com.oflayn.core.model.Stop
import com.oflayn.core.model.Transaction
import com.oflayn.core.model.TransitAlert
import com.oflayn.core.model.VehicleType
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Synthetic fixtures for tests only. */
private class FakeTransit(
    val s: List<Stop> = emptyList(), val r: List<Route> = emptyList(), val a: List<TransitAlert> = emptyList(),
) : TransitData {
    override val sourceName = "test-fixture"
    override val freshness = Freshness.CACHED
    override suspend fun stops() = s
    override suspend fun routes() = r
    override suspend fun alerts() = a
}

private class FakeUser(val card: BursaCardType = BursaCardType.UNKNOWN) : UserData {
    override suspend fun favoriteStopIds() = emptyList<String>()
    override suspend fun favoriteRouteIds() = emptyList<String>()
    override suspend fun recentTrips() = emptyList<String>()
    override suspend fun cardType() = card
    override suspend fun transactions() = emptyList<Transaction>()
    override suspend fun settingsSummary() = "lang=ar"
}

private class FakeLoc(val p: GeoPoint?) : LocationSource { override suspend fun current() = p }

private class StubProvider(override val id: String, val avail: Boolean = true, val fail: Boolean = false) : AiProvider {
    override suspend fun isAvailable() = avail
    override suspend fun generate(request: AiRequest): AiResponse {
        if (fail) error("boom")
        return AiResponse("from $id", id)
    }
}

class AiTest {
    private fun registry(transit: TransitData = FakeTransit(), loc: GeoPoint? = null, timeout: Long = 5_000) =
        ToolRegistry(TransitTools.build(transit, FakeLoc(loc), FakeUser(), null, null), timeout)

    @Test fun routerFallsBackWhenOnlineFails() = runTest {
        val local = StubProvider("local")
        val router = AiModelRouter(StubProvider("online", fail = true), null, null, local, { true }, { DeviceTier.LOW })
        assertEquals("local", router.generate(AiRequest("x")).provider)
    }

    @Test fun routerSkipsOnlineWhenOffline() = runTest {
        val router = AiModelRouter(StubProvider("online"), null, StubProvider("e2b"), StubProvider("local"), { false }, { DeviceTier.MID })
        assertEquals("e2b", router.generate(AiRequest("x")).provider)
    }

    @Test fun lowTierNeverUsesGemma() = runTest {
        val router = AiModelRouter(null, StubProvider("e4b"), StubProvider("e2b"), StubProvider("local"), { false }, { DeviceTier.LOW })
        assertEquals("local", router.generate(AiRequest("x")).provider)
    }

    @Test fun highTierPrefersE4B() = runTest {
        val router = AiModelRouter(null, StubProvider("e4b"), StubProvider("e2b"), StubProvider("local"), { false }, { DeviceTier.HIGH })
        assertEquals("e4b", router.generate(AiRequest("x")).provider)
    }

    @Test fun rateLimitFallsThroughToLocal() = runTest {
        val router = AiModelRouter(StubProvider("online"), null, null, StubProvider("local"), { true }, { DeviceTier.LOW }, RateLimiter(1))
        assertEquals("online", router.generate(AiRequest("1")).provider)
        assertEquals("local", router.generate(AiRequest("2")).provider)
    }

    @Test fun deviceTiers() {
        assertEquals(DeviceTier.LOW, DeviceCapability.tier(3_000))
        assertEquals(DeviceTier.MID, DeviceCapability.tier(6_000))
        assertEquals(DeviceTier.HIGH, DeviceCapability.tier(12_000))
    }

    @Test fun unknownToolAndMissingArgsAreFailuresNotCrashes() = runTest {
        val reg = registry()
        assertFalse(reg.call("nope").ok)
        assertFalse(reg.call("searchStops").ok)
    }

    @Test fun toolTimeoutIsReported() = runTest {
        val slow = SimpleTool("slow", "", "{}") { delay(10_000); ToolResult(true, "x", "t", Freshness.LIVE) }
        val r = ToolRegistry(listOf(slow), timeoutMs = 50).call("slow")
        assertFalse(r.ok)
        assertTrue(r.error!!.contains("timed out"))
    }

    @Test fun searchStopsHandlesTurkishCharacters() = runTest {
        val reg = registry(FakeTransit(s = listOf(Stop("1", "Şehreküstü", 40.0, 29.0))))
        val r = reg.call("searchStops", mapOf("query" to "sehrekustu"))
        assertTrue(r.ok && r.data.contains("Şehreküstü"))
    }

    @Test fun assistantNeverInventsWhenNoData() = runTest {
        val a = LocalDataAssistant(registry())
        val res = a.generate(AiRequest("وين أقرب باص؟"))
        assertTrue(res.text.contains("لا أملك بيانات كافية"))
    }

    @Test fun assistantAsksForTariffInsteadOfGuessingFare() = runTest {
        val res = LocalDataAssistant(registry()).generate(AiRequest("قديش أجرة الطالب؟"))
        assertFalse(res.grounded)
        assertTrue(res.toolsUsed.isEmpty())
    }

    @Test fun assistantFindsNearbyStopsFromTool() = runTest {
        val reg = registry(FakeTransit(s = listOf(Stop("1", "Kent Meydanı", 40.0001, 29.0))), loc = GeoPoint(40.0, 29.0))
        val res = LocalDataAssistant(reg).generate(AiRequest("where is the nearest stop"))
        assertTrue(res.text.contains("Kent Meydanı"))
        assertEquals(listOf("findNearbyStops"), res.toolsUsed)
    }

    @Test fun mixedArabicTurkishTripIsRouted() = runTest {
        val stops = listOf(Stop("a", "Soğanlı", 40.0, 29.0), Stop("b", "Şehreküstü", 40.01, 29.0))
        val routes = listOf(Route("r", "38", null, VehicleType.BUS, listOf("a", "b")))
        val res = LocalDataAssistant(registry(FakeTransit(stops, routes))).generate(AiRequest("كيف بروح من Soğanlı إلى Şehreküstü؟"))
        assertTrue(res.toolsUsed.contains("planTrip"))
        assertTrue(res.text.contains("RIDE 38"))
    }

    @Test fun languageDetection() {
        assertEquals(Lang.AR, LanguageDetector.detect("وين أقرب باص"))
        assertEquals(Lang.TR, LanguageDetector.detect("En yakın durak nerede"))
        assertEquals(Lang.EN, LanguageDetector.detect("nearest stop"))
    }

    @Test fun groundedProviderDoesNotAskLlmWhenNothingGrounded() = runTest {
        val llm = StubProvider("llm", fail = true)
        val g = GroundedProvider("g", llm, LocalDataAssistant(registry()))
        val res = g.generate(AiRequest("tell me a story"))
        assertFalse(res.grounded)
    }

    @Test fun groundedProviderPassesToolDataToLlm() = runTest {
        var seen = ""
        val llm = object : AiProvider {
            override val id = "llm"
            override suspend fun isAvailable() = true
            override suspend fun generate(request: AiRequest): AiResponse { seen = request.text; return AiResponse("ok", id) }
        }
        val reg = registry(FakeTransit(s = listOf(Stop("1", "Kent Meydanı", 40.0001, 29.0))), loc = GeoPoint(40.0, 29.0))
        val res = GroundedProvider("g", llm, LocalDataAssistant(reg)).generate(AiRequest("nearest stop"))
        assertTrue(seen.contains("VERIFIED DATA") && seen.contains("Kent Meydanı"))
        assertEquals("llm", res.provider)
    }
}
