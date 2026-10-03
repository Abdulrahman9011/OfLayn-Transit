package com.oflayn.domain.fare

import com.oflayn.core.model.Kurus

data class Provenance(
    val manifestId: String,
    val sourceUrl: String,
    val ruleStatus: RuleStatus,
    val manifestVerification: ManifestVerification,
)

sealed interface RideLimit {
    data object Unlimited : RideLimit
    data class PerDay(val rides: Int) : RideLimit
    /** The sources we read do not state a limit. Do not invent one. */
    data object Unspecified : RideLimit
}

enum class UnknownReason {
    MANIFEST_NOT_IN_EFFECT, CARD_TYPE_UNKNOWN, CARD_RULE_MISSING, TARIFF_NOT_FOUND,
    FARE_COLUMN_UNPUBLISHED, SUBSCRIPTION_NOT_APPLICABLE,
}

/** The engine never guesses: if a price is not published it answers Unknown with a reason. */
sealed interface FareResult {
    data class Paid(val amount: Kurus, val tariffNo: Int, val column: FareColumn, val provenance: Provenance) : FareResult
    data class Free(val limit: RideLimit, val provenance: Provenance) : FareResult
    data class Subscription(val ridesDeducted: Int, val provenance: Provenance) : FareResult
    data class Unknown(val reason: UnknownReason) : FareResult
}
