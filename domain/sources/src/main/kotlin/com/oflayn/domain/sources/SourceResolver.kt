package com.oflayn.domain.sources

import com.oflayn.core.model.Freshness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

sealed interface SourceResult<out T> {
    /** [observedAt] = when the data was observed at the origin (e.g. GPS fix time), if the source says so. */
    data class Success<T>(val data: T, val observedAt: Instant? = null) : SourceResult<T>
    data class Failure(val reason: String) : SourceResult<Nothing>
}

/** One concrete provider of a kind of data (official API, backend cache, OSM-derived pack, ...). Lower priority value = preferred. */
interface DataSource<T> {
    val id: String
    val priority: Int
    suspend fun fetch(): SourceResult<T>
}

data class ResolverPolicy(
    val failureThreshold: Int = 3,
    val baseBackoff: Duration = Duration.ofSeconds(30),
    val maxBackoff: Duration = Duration.ofMinutes(15),
)

data class FreshnessPolicy(val liveMax: Duration, val recentMax: Duration, val cachedMax: Duration) {
    fun classify(age: Duration): Freshness = when {
        age <= liveMax -> Freshness.LIVE
        age <= recentMax -> Freshness.RECENT
        age <= cachedMax -> Freshness.CACHED
        else -> Freshness.STALE
    }

    companion object {
        /** Tunable design parameters (not facts about any source). */
        val VEHICLES = FreshnessPolicy(Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(5))
        val ALERTS = FreshnessPolicy(Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(6))
        val STATIC_DATA = FreshnessPolicy(Duration.ofHours(1), Duration.ofDays(1), Duration.ofDays(30))
    }
}

sealed interface Attempt {
    val sourceId: String
    data class Ok(override val sourceId: String) : Attempt
    data class Failed(override val sourceId: String, val reason: String) : Attempt
    data class SkippedCircuitOpen(override val sourceId: String, val retryAt: Instant) : Attempt
}

sealed interface Resolution<out T> {
    val attempts: List<Attempt>

    data class Resolved<T>(
        val data: T,
        val sourceId: String,
        val fetchedAt: Instant,
        val observedAt: Instant?,
        val freshness: Freshness,
        /** true when every source failed just now and an earlier good result is being served. */
        val servedFromFallback: Boolean,
        override val attempts: List<Attempt>,
    ) : Resolution<T>

    data class Unavailable(override val attempts: List<Attempt>) : Resolution<Nothing>
}

/**
 * Multi-source resolver with circuit breakers.
 *  - tries sources by priority; a failing source is skipped for an exponentially growing back-off window
 *  - when the window ends the source is probed again, and on success it automatically takes over again
 *  - if all sources fail, the best earlier good result is served, never labelled LIVE
 */
class SourceResolver<T>(
    sources: List<DataSource<T>>,
    private val freshnessPolicy: FreshnessPolicy,
    private val policy: ResolverPolicy = ResolverPolicy(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private class State<T> {
        var failures = 0
        var openUntil: Instant? = null
        var lastGood: Good<T>? = null
    }

    private data class Good<T>(val data: T, val fetchedAt: Instant, val observedAt: Instant?)

    private val ordered = sources.sortedBy { it.priority }
    private val states = ordered.associate { it.id to State<T>() }
    private val mutex = Mutex()

    suspend fun resolve(): Resolution<T> = mutex.withLock {
        val attempts = mutableListOf<Attempt>()
        for (source in ordered) {
            val state = states.getValue(source.id)
            val now = clock.instant()
            val openUntil = state.openUntil
            if (openUntil != null && now.isBefore(openUntil)) {
                attempts += Attempt.SkippedCircuitOpen(source.id, openUntil)
                continue
            }
            val result = try {
                source.fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SourceResult.Failure("exception: ${e::class.simpleName}: ${e.message}")
            }
            when (result) {
                is SourceResult.Success -> {
                    state.failures = 0
                    state.openUntil = null
                    val good = Good(result.data, clock.instant(), result.observedAt)
                    state.lastGood = good
                    attempts += Attempt.Ok(source.id)
                    return@withLock resolved(source.id, good, fallback = false, attempts)
                }
                is SourceResult.Failure -> {
                    state.failures++
                    if (state.failures >= policy.failureThreshold) {
                        val exp = (state.failures - policy.failureThreshold).coerceAtMost(20)
                        val backoff = policy.baseBackoff.multipliedBy(1L shl exp)
                        state.openUntil = clock.instant().plus(if (backoff > policy.maxBackoff) policy.maxBackoff else backoff)
                    }
                    attempts += Attempt.Failed(source.id, result.reason)
                }
            }
        }
        // Everything failed or is skipped: serve the newest earlier good result, honestly labelled.
        val best = ordered.mapNotNull { s -> states.getValue(s.id).lastGood?.let { s.id to it } }
            .maxByOrNull { it.second.fetchedAt }
        if (best == null) Resolution.Unavailable(attempts) else resolved(best.first, best.second, fallback = true, attempts)
    }

    private fun resolved(sourceId: String, good: Good<T>, fallback: Boolean, attempts: List<Attempt>): Resolution.Resolved<T> {
        val now = clock.instant()
        val basis = good.observedAt ?: good.fetchedAt
        var f = freshnessPolicy.classify(Duration.between(basis, now).coerceAtLeast(Duration.ZERO))
        // Unknown observation time or a fallback can never be shown as LIVE.
        if (f == Freshness.LIVE && (good.observedAt == null || fallback)) f = Freshness.RECENT
        return Resolution.Resolved(good.data, sourceId, good.fetchedAt, good.observedAt, f, fallback, attempts)
    }

    private fun Duration.coerceAtLeast(min: Duration) = if (this < min) min else this
}
