package com.oflayn.core.model

import java.time.Instant

/** Every important value carries its source, timestamp and status. */
data class DataEnvelope<out T>(
    val data: T?,
    val source: String,
    val timestamp: Instant?,
    val freshness: Freshness,
    val verification: Verification,
)
