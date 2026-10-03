package com.oflayn.domain.fare

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface LoadResult {
    data class Accepted(val manifest: FareManifest, val warnings: List<String>) : LoadResult
    data class Rejected(val reasons: List<String>) : LoadResult
}

/** Pipeline: signature verification -> parsing -> validation. Anything failing keeps the last trusted manifest. */
object FareManifestLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(bytes: ByteArray): FareManifest = json.decodeFromString(FareManifest.serializer(), bytes.decodeToString())

    fun loadSigned(bytes: ByteArray, signature: ByteArray, verifier: ManifestVerifier, previous: FareManifest? = null): LoadResult {
        if (!verifier.verify(bytes, signature)) return LoadResult.Rejected(listOf("Signature verification failed"))
        return validated(bytes, previous)
    }

    /** For the baseline manifest bundled inside the (already signed) APK. */
    fun loadBundled(bytes: ByteArray): LoadResult = validated(bytes, null)

    private fun validated(bytes: ByteArray, previous: FareManifest?): LoadResult {
        val manifest = try { parse(bytes) } catch (e: SerializationException) {
            return LoadResult.Rejected(listOf("Malformed manifest: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            return LoadResult.Rejected(listOf("Malformed manifest: ${e.message}"))
        }
        val v = ManifestValidator.validate(manifest, previous)
        return if (v.ok) LoadResult.Accepted(manifest, v.warnings) else LoadResult.Rejected(v.errors)
    }
}
