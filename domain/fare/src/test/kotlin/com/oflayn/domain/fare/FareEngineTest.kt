package com.oflayn.domain.fare

import com.oflayn.core.model.BursaCardType
import com.oflayn.core.model.Kurus
import java.time.Instant
import kotlin.test.*

class FareEngineTest {
    private val bytes = javaClass.getResourceAsStream("/official/fare-manifest-2026-10-01.json")!!.readBytes()
    private val manifest = (FareManifestLoader.loadBundled(bytes) as LoadResult.Accepted).manifest
    private val engine = FareEngine(manifest)
    private val now = Instant.parse("2026-10-02T09:00:00Z")
    private val BURSARAY = 1

    private fun paid(card: BursaCardType, tariff: Int = BURSARAY) = engine.quote(card, tariff, now) as FareResult.Paid

    @Test fun `bundled manifest is accepted and has no warnings`() {
        val r = FareManifestLoader.loadBundled(bytes) as LoadResult.Accepted
        assertEquals(26, r.manifest.tariffs.size)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun `bursaray fares per card type match the official table`() {
        assertEquals(Kurus(5000), paid(BursaCardType.FULL).amount)
        assertEquals(Kurus(4450), paid(BursaCardType.DISCOUNTED).amount)
        assertEquals(Kurus(1250), paid(BursaCardType.STUDENT).amount)
    }

    @Test fun `teacher and 60+ use the discounted column but are flagged as derived`() {
        for (c in listOf(BursaCardType.TEACHER, BursaCardType.AGE_60_PLUS)) {
            val p = paid(c)
            assertEquals(Kurus(4450), p.amount)
            assertEquals(RuleStatus.DERIVED, p.provenance.ruleStatus)
        }
    }

    @Test fun `graduating student has no published price so the engine does not guess`() {
        assertEquals(FareResult.Unknown(UnknownReason.FARE_COLUMN_UNPUBLISHED), engine.quote(BursaCardType.GRADUATING_STUDENT, BURSARAY, now))
    }

    @Test fun `unknown card type never yields a price`() {
        assertEquals(FareResult.Unknown(UnknownReason.CARD_TYPE_UNKNOWN), engine.quote(BursaCardType.UNKNOWN, BURSARAY, now))
        assertEquals(FareResult.Unknown(UnknownReason.FARE_COLUMN_UNPUBLISHED), engine.quote(BursaCardType.OTHER_FREE, BURSARAY, now))
    }

    @Test fun `free cards report their ride limits`() {
        assertEquals(RideLimit.Unlimited, (engine.quote(BursaCardType.AGE_65_PLUS, BURSARAY, now) as FareResult.Free).limit)
        assertEquals(RideLimit.PerDay(10), (engine.quote(BursaCardType.DISABLED, BURSARAY, now) as FareResult.Free).limit)
        assertEquals(RideLimit.Unspecified, (engine.quote(BursaCardType.AGE_65_PLUS_2022, BURSARAY, now) as FareResult.Free).limit)
    }

    @Test fun `subscriptions deduct rides and respect tariffs without student subscription`() {
        assertEquals(1, (engine.quote(BursaCardType.FULL_SUBSCRIPTION, 0, now) as FareResult.Subscription).ridesDeducted)
        assertEquals(2, (engine.quote(BursaCardType.FULL_SUBSCRIPTION, 11, now) as FareResult.Subscription).ridesDeducted)
        assertEquals(FareResult.Unknown(UnknownReason.SUBSCRIPTION_NOT_APPLICABLE), engine.quote(BursaCardType.STUDENT_SUBSCRIPTION, 11, now))
    }

    @Test fun `unknown tariff and out-of-effect dates`() {
        assertEquals(FareResult.Unknown(UnknownReason.TARIFF_NOT_FOUND), engine.quote(BursaCardType.FULL, 999, now))
        assertEquals(FareResult.Unknown(UnknownReason.MANIFEST_NOT_IN_EFFECT), engine.quote(BursaCardType.FULL, BURSARAY, Instant.parse("2026-09-30T00:00:00Z")))
    }

    @Test fun `credit card service fee follows the official step rule`() {
        assertEquals(Kurus(100), engine.creditCardFee(Kurus(500)))
        assertEquals(Kurus(200), engine.creditCardFee(Kurus(501)))
        assertEquals(Kurus(300), engine.creditCardFee(Kurus(1250)))
        assertEquals(Kurus(1000), engine.creditCardFee(Kurus(5000)))
        assertEquals(Kurus.ZERO, engine.creditCardFee(Kurus.ZERO))
    }

    @Test fun `bursaray refund by distance`() {
        assertEquals(Kurus(1200), engine.bursaRayRefund(3))
        assertEquals(Kurus(500), engine.bursaRayRefund(8))
        assertNull(engine.bursaRayRefund(12))
    }

    @Test fun `money formatting`() {
        assertEquals("12.50", Kurus.ofLira("12.50").toPlainString())
        assertEquals("0.05", Kurus(5).toPlainString())
        assertFailsWith<ArithmeticException> { Kurus.ofLira("1.005") }
    }
}
