package com.oflayn.domain.fare

import com.oflayn.core.model.BursaCardType
import com.oflayn.core.model.Kurus
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime

class FareEngine(private val manifest: FareManifest, private val clock: Clock = Clock.systemUTC()) {
    private val tariffs = manifest.tariffs.associateBy { it.tariffNo }
    private val cardRules = manifest.cardRules.associateBy { it.cardType }
    private val effectiveFrom: Instant = OffsetDateTime.parse(manifest.validFrom).toInstant()
    private val effectiveUntil: Instant? = manifest.validUntil?.let { OffsetDateTime.parse(it).toInstant() }

    val manifestId: String get() = manifest.manifestId

    fun quote(card: BursaCardType, tariffNo: Int, at: Instant = clock.instant()): FareResult {
        if (at.isBefore(effectiveFrom) || (effectiveUntil != null && at.isAfter(effectiveUntil))) {
            return FareResult.Unknown(UnknownReason.MANIFEST_NOT_IN_EFFECT)
        }
        if (card == BursaCardType.UNKNOWN) return FareResult.Unknown(UnknownReason.CARD_TYPE_UNKNOWN)
        val rule = cardRules[card.name] ?: return FareResult.Unknown(UnknownReason.CARD_RULE_MISSING)
        val tariff = tariffs[tariffNo] ?: return FareResult.Unknown(UnknownReason.TARIFF_NOT_FOUND)
        val provenance = Provenance(manifest.manifestId, rule.sourceUrl, rule.status, manifest.source.verification)

        val column = rule.fareColumn ?: return FareResult.Unknown(UnknownReason.FARE_COLUMN_UNPUBLISHED)
        if (column == FareColumn.FREE) {
            val limit = when {
                rule.unlimitedRides -> RideLimit.Unlimited
                rule.ridesPerDay != null -> RideLimit.PerDay(rule.ridesPerDay)
                else -> RideLimit.Unspecified
            }
            return FareResult.Free(limit, provenance)
        }
        if (rule.subscription) {
            val rides = when (column) {
                FareColumn.FULL -> tariff.subscriptionRidesFull
                FareColumn.STUDENT -> tariff.subscriptionRidesStudent
                else -> null
            } ?: return FareResult.Unknown(UnknownReason.SUBSCRIPTION_NOT_APPLICABLE)
            return FareResult.Subscription(rides, provenance)
        }
        val amount = when (column) {
            FareColumn.FULL -> tariff.fullKurus
            FareColumn.DISCOUNTED -> tariff.discountedKurus
            FareColumn.STUDENT -> tariff.studentKurus
            FareColumn.FREE -> return FareResult.Unknown(UnknownReason.FARE_COLUMN_UNPUBLISHED)
        }
        return FareResult.Paid(Kurus(amount), tariff.tariffNo, column, provenance)
    }

    /** BursaRay refund validators: amount depends on the number of stops travelled; null if not published. */
    fun bursaRayRefund(stopsTravelled: Int): Kurus? =
        manifest.refunds.firstOrNull { stopsTravelled in it.stopsFrom..it.stopsTo }?.let { Kurus(it.refundKurus) }

    /** Service fee when paying with a credit card: +1 TL per 5 TL (or part) of the fare. Null if rule not published. */
    fun creditCardFee(fare: Kurus): Kurus? {
        val rule = manifest.creditCardFee ?: return null
        if (fare.value <= 0) return Kurus.ZERO
        val steps = (fare.value + rule.stepKurus - 1) / rule.stepKurus
        return Kurus(steps * rule.feePerStepKurus)
    }

    fun fee(code: String): Kurus? = manifest.fees.firstOrNull { it.code == code }?.let { Kurus(it.amountKurus) }

    fun transferPolicy(): TransferPolicy? = manifest.transferPolicy
}
