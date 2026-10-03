package com.oflayn.domain.ai

import kotlinx.coroutines.CancellationException

enum class DeviceTier { LOW, MID, HIGH }

/** Maps device RAM to a local model choice. Thresholds are design parameters, to be tuned by on-device benchmarks. */
object DeviceCapability {
    fun tier(totalRamMb: Long): DeviceTier = when {
        totalRamMb >= 8_000 -> DeviceTier.HIGH
        totalRamMb >= 5_000 -> DeviceTier.MID
        else -> DeviceTier.LOW
    }
}

/**
 * Order: online (when connected) -> local strong (HIGH tier only) -> local small -> local data assistant.
 * A failure of any provider falls through to the next; it never propagates (except cancellation).
 */
class AiModelRouter(
    private val online: AiProvider?,
    private val gemmaE4B: AiProvider?,
    private val gemmaE2B: AiProvider?,
    private val localAssistant: AiProvider,
    private val isOnline: () -> Boolean,
    private val tier: () -> DeviceTier,
    private val rateLimiter: RateLimiter = RateLimiter(maxPerMinute = 20),
) {
    suspend fun generate(request: AiRequest): AiResponse {
        val chain = buildList {
            if (online != null && isOnline() && rateLimiter.tryAcquire()) add(online)
            if (gemmaE4B != null && tier() == DeviceTier.HIGH) add(gemmaE4B)
            if (gemmaE2B != null && tier() != DeviceTier.LOW) add(gemmaE2B)
            add(localAssistant)
        }
        for (p in chain) {
            try {
                if (!p.isAvailable()) continue
                return p.generate(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // fall through to the next provider
            }
        }
        return AiResponse(NO_DATA_AR, "none")
    }

    companion object { const val NO_DATA_AR = "لا أملك بيانات كافية للإجابة عن هذا السؤال." }
}

class RateLimiter(private val maxPerMinute: Int, private val nowMs: () -> Long = System::currentTimeMillis) {
    private val stamps = ArrayDeque<Long>()
    @Synchronized fun tryAcquire(): Boolean {
        val now = nowMs()
        while (stamps.isNotEmpty() && now - stamps.first() > 60_000) stamps.removeFirst()
        if (stamps.size >= maxPerMinute) return false
        stamps.addLast(now)
        return true
    }
}
