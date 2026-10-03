package com.oflayn.domain.sources

import com.oflayn.core.model.Freshness
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.*

class MutableClock(private var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
    fun advance(d: Duration) { now = now.plus(d) }
}

class FakeSource(override val id: String, override val priority: Int, var behaviour: () -> SourceResult<String>) : DataSource<String> {
    var calls = 0
    override suspend fun fetch(): SourceResult<String> { calls++; return behaviour() }
}

class SourceResolverTest {
    private val t0 = Instant.parse("2026-10-01T10:00:00Z")
    private val clock = MutableClock(t0)
    private val ok = { SourceResult.Success("data", observedAt = clock.instant()) }
    private val fail = { SourceResult.Failure("down") }

    private fun resolver(vararg s: FakeSource) = SourceResolver(s.toList(), FreshnessPolicy.VEHICLES, ResolverPolicy(), clock)

    @Test fun `highest priority healthy source wins`() = runTest {
        val a = FakeSource("official", 1, ok); val b = FakeSource("backup", 2, ok)
        val r = resolver(a, b).resolve() as Resolution.Resolved
        assertEquals("official", r.sourceId); assertEquals(Freshness.LIVE, r.freshness); assertEquals(0, b.calls)
    }

    @Test fun `falls through to the next source on failure`() = runTest {
        val a = FakeSource("official", 1, fail); val b = FakeSource("backup", 2, ok)
        val r = resolver(a, b).resolve() as Resolution.Resolved
        assertEquals("backup", r.sourceId)
        assertIs<Attempt.Failed>(r.attempts.first())
    }

    @Test fun `failing source is skipped after the threshold and recovers automatically`() = runTest {
        val a = FakeSource("official", 1, fail); val b = FakeSource("backup", 2, ok)
        val res = resolver(a, b)
        repeat(3) { res.resolve() }
        assertEquals(3, a.calls)
        val skipped = res.resolve() as Resolution.Resolved
        assertEquals(3, a.calls, "open circuit: primary must not be called")
        assertIs<Attempt.SkippedCircuitOpen>(skipped.attempts.first())

        a.behaviour = ok                      // source comes back
        clock.advance(Duration.ofSeconds(31)) // back-off (30 s) elapsed -> probe
        val back = res.resolve() as Resolution.Resolved
        assertEquals("official", back.sourceId, "must switch back automatically")
    }

    @Test fun `all sources down serves last good data and never LIVE`() = runTest {
        val a = FakeSource("official", 1, ok)
        val res = resolver(a)
        res.resolve()
        a.behaviour = fail
        clock.advance(Duration.ofSeconds(5))
        val r = res.resolve() as Resolution.Resolved
        assertTrue(r.servedFromFallback)
        assertNotEquals(Freshness.LIVE, r.freshness)
        clock.advance(Duration.ofMinutes(10))
        assertEquals(Freshness.STALE, (res.resolve() as Resolution.Resolved).freshness)
    }

    @Test fun `unknown observation time is never LIVE`() = runTest {
        val a = FakeSource("official", 1) { SourceResult.Success("data", observedAt = null) }
        assertEquals(Freshness.RECENT, (resolver(a).resolve() as Resolution.Resolved).freshness)
    }

    @Test fun `no data at all is Unavailable`() = runTest {
        assertIs<Resolution.Unavailable>(resolver(FakeSource("a", 1, fail)).resolve())
    }

    @Test fun `exceptions count as failures`() = runTest {
        val a = FakeSource("a", 1) { throw IllegalStateException("boom") }; val b = FakeSource("b", 2, ok)
        assertEquals("b", (resolver(a, b).resolve() as Resolution.Resolved).sourceId)
    }
}
