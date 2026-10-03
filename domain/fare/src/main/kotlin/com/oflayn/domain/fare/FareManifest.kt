package com.oflayn.domain.fare

import kotlinx.serialization.Serializable

/** Wire format of the signed fare manifest. All money is in kuruş (Long). */
@Serializable
data class FareManifest(
    val schemaVersion: Int,
    val manifestId: String,
    val issuedAt: String,
    val validFrom: String,
    val validUntil: String? = null,
    val source: ManifestSource,
    val tariffs: List<TariffRow>,
    val transferTiers: List<TransferTier> = emptyList(),
    val transferPolicy: TransferPolicy? = null,
    val refunds: List<RefundRule> = emptyList(),
    val fees: List<FeeRule> = emptyList(),
    val creditCardFee: CreditCardFeeRule? = null,
    val cardRules: List<CardRule> = emptyList(),
    val notes: List<String> = emptyList(),
)

@Serializable
data class ManifestSource(val name: String, val url: String, val retrievedAt: String, val verification: ManifestVerification)

@Serializable
enum class ManifestVerification { OFFICIAL_SOURCE_HUMAN_CONFIRMED, OFFICIAL_SOURCE_READ_PENDING_HUMAN_CONFIRMATION, UNVERIFIED }

@Serializable
data class TariffRow(
    val order: Int,
    val tariffNo: Int,
    val name: String,
    val fullKurus: Long,
    val discountedKurus: Long,
    val studentKurus: Long,
    val subscriptionRidesFull: Int? = null,
    val subscriptionRidesStudent: Int? = null,
)

@Serializable
enum class RuleStatus { OFFICIAL, DERIVED, UNVERIFIED }

@Serializable
enum class FareColumn { FULL, DISCOUNTED, STUDENT, FREE }

@Serializable
data class TransferTier(val tier: Int, val fullKurus: Long, val discountedKurus: Long, val interpretation: RuleStatus)

@Serializable
data class TransferPolicy(val windowMinutes: Int, val maxTransfers: Int, val scenarios: List<String>)

@Serializable
data class RefundRule(val stopsFrom: Int, val stopsTo: Int, val refundKurus: Long)

@Serializable
data class FeeRule(val code: String, val amountKurus: Long)

/** Official text: +1 TL service fee for every 5 TL (or part of it) of fare when paying by credit card. */
@Serializable
data class CreditCardFeeRule(val stepKurus: Long, val feePerStepKurus: Long)

@Serializable
data class CardRule(
    val cardType: String,
    val fareColumn: FareColumn? = null,
    val subscription: Boolean = false,
    val ridesPerDay: Int? = null,
    val unlimitedRides: Boolean = false,
    val validityDays: Int? = null,
    val status: RuleStatus,
    val sourceUrl: String,
    val note: String? = null,
)
