package com.oflayn.domain.transit

import com.oflayn.core.model.Freshness
import com.oflayn.core.model.Verification

enum class SourceType { OFFICIAL_GTFS, UNOFFICIAL_GTFS, DERIVED_OSM, MANUAL }

/** Provenance stored with every installed network dataset. */
data class DatasetInfo(
    val source: String,
    val sourceType: SourceType,
    val url: String,
    val fetchedAtMs: Long,
    val verification: Verification,
    val stopCount: Int,
    val routeCount: Int,
    val attribution: String,
) {
    /** User-facing label. Derived data is never presented as official. */
    val label: String get() = when (sourceType) {
        SourceType.OFFICIAL_GTFS -> "Official GTFS"
        SourceType.UNOFFICIAL_GTFS -> "Unofficial GTFS"
        SourceType.DERIVED_OSM -> "DERIVED / UNOFFICIAL"
        SourceType.MANUAL -> "MANUAL"
    }

    /** Age thresholds are design parameters: <=7 days RECENT, <=60 days CACHED, older STALE. */
    fun freshness(nowMs: Long): Freshness {
        val ageDays = (nowMs - fetchedAtMs) / 86_400_000.0
        return when {
            ageDays < 0 -> Freshness.CACHED
            ageDays <= 7 -> Freshness.RECENT
            ageDays <= 60 -> Freshness.CACHED
            else -> Freshness.STALE
        }
    }
}
