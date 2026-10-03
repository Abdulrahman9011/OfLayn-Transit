package com.oflayn.domain.fare

import java.time.OffsetDateTime

data class ValidationResult(val errors: List<String>, val warnings: List<String>) {
    val ok: Boolean get() = errors.isEmpty()
}

object ManifestValidator {
    const val SUPPORTED_SCHEMA = 1

    /** @param previous last trusted manifest, used for plausibility checks (warnings only). */
    fun validate(m: FareManifest, previous: FareManifest? = null): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (m.schemaVersion != SUPPORTED_SCHEMA) errors += "Unsupported schemaVersion ${m.schemaVersion}"
        val from = runCatching { OffsetDateTime.parse(m.validFrom) }.getOrNull()
        if (from == null) errors += "validFrom is not an ISO-8601 offset date-time"
        val until = m.validUntil?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
        if (m.validUntil != null && until == null) errors += "validUntil is not an ISO-8601 offset date-time"
        if (from != null && until != null && until.isBefore(from)) errors += "validUntil is before validFrom"

        if (m.tariffs.isEmpty()) errors += "No tariffs"
        val dup = m.tariffs.groupBy { it.tariffNo }.filterValues { it.size > 1 }.keys
        if (dup.isNotEmpty()) errors += "Duplicate tariffNo: $dup"
        m.tariffs.forEach { t ->
            if (t.fullKurus < 0 || t.discountedKurus < 0 || t.studentKurus < 0) errors += "Negative fare in tariff ${t.tariffNo}"
            if (!(t.studentKurus <= t.discountedKurus && t.discountedKurus <= t.fullKurus)) {
                errors += "Fare ordering violated in tariff ${t.tariffNo} (expected student <= discounted <= full)"
            }
            if ((t.subscriptionRidesFull ?: 1) < 1 || (t.subscriptionRidesStudent ?: 1) < 1) {
                errors += "Subscription deduction < 1 in tariff ${t.tariffNo}"
            }
        }
        val dupCards = m.cardRules.groupBy { it.cardType }.filterValues { it.size > 1 }.keys
        if (dupCards.isNotEmpty()) errors += "Duplicate card rules: $dupCards"
        m.cardRules.forEach { r ->
            if (r.status == RuleStatus.UNVERIFIED && r.fareColumn != null) {
                errors += "UNVERIFIED card rule ${r.cardType} must not carry a fare column"
            }
        }
        m.refunds.forEach { if (it.refundKurus < 0 || it.stopsTo < it.stopsFrom) errors += "Bad refund rule $it" }

        if (previous != null) {
            val old = previous.tariffs.associateBy { it.tariffNo }
            m.tariffs.forEach { t ->
                val o = old[t.tariffNo] ?: return@forEach
                if (o.fullKurus > 0) {
                    val ratio = t.fullKurus.toDouble() / o.fullKurus
                    if (ratio > 2.0 || ratio < 0.5) warnings += "Tariff ${t.tariffNo}: full fare changed by factor %.2f".format(ratio)
                }
            }
            if (OffsetDateTime.parse(m.issuedAt).isBefore(OffsetDateTime.parse(previous.issuedAt))) {
                errors += "Manifest is older than the last trusted one (rollback)"
            }
        }
        if (m.source.verification == ManifestVerification.UNVERIFIED) warnings += "Manifest source is UNVERIFIED"
        return ValidationResult(errors, warnings)
    }
}
