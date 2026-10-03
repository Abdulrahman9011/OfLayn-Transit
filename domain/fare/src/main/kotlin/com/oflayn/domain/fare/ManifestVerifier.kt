package com.oflayn.domain.fare

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifies a detached ECDSA (P-256, SHA-256, DER) signature over the exact manifest bytes.
 * Signing happens off-device (tools/sign_manifest.sh); only the PUBLIC key ships in the app.
 */
class ManifestVerifier(publicKeyX509: ByteArray) {
    private val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyX509))

    fun verify(manifestBytes: ByteArray, signature: ByteArray): Boolean = try {
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(manifestBytes)
            verify(signature)
        }
    } catch (e: java.security.GeneralSecurityException) {
        false
    }
}
