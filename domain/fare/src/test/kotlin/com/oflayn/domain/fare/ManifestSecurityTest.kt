package com.oflayn.domain.fare

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.test.*

class ManifestSecurityTest {
    private val bytes = javaClass.getResourceAsStream("/official/fare-manifest-2026-10-01.json")!!.readBytes()
    private val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val verifier = ManifestVerifier(kp.public.encoded)
    private fun sign(b: ByteArray) = Signature.getInstance("SHA256withECDSA").run { initSign(kp.private); update(b); sign() }

    @Test fun `valid signature is accepted`() {
        assertIs<LoadResult.Accepted>(FareManifestLoader.loadSigned(bytes, sign(bytes), verifier))
    }

    @Test fun `tampered manifest is rejected`() {
        val sig = sign(bytes)
        val tampered = bytes.decodeToString().replace("\"fullKurus\": 5000", "\"fullKurus\": 4000").encodeToByteArray()
        assertNotEquals(bytes.decodeToString(), tampered.decodeToString())
        assertIs<LoadResult.Rejected>(FareManifestLoader.loadSigned(tampered, sig, verifier))
    }

    @Test fun `garbage signature is rejected without throwing`() {
        assertIs<LoadResult.Rejected>(FareManifestLoader.loadSigned(bytes, byteArrayOf(1, 2, 3), verifier))
    }

    @Test fun `validator catches duplicates and ordering errors`() {
        val m = FareManifestLoader.parse(bytes)
        val bad = m.copy(tariffs = m.tariffs + m.tariffs.first().copy(studentKurus = 99999))
        val v = ManifestValidator.validate(bad)
        assertFalse(v.ok)
        assertTrue(v.errors.any { "Duplicate" in it })
    }

    @Test fun `rollback to an older manifest is rejected and big jumps warn`() {
        val m = FareManifestLoader.parse(bytes)
        val older = m.copy(issuedAt = "2026-01-01T00:00:00Z")
        assertTrue(ManifestValidator.validate(older, m).errors.any { "rollback" in it })
        val jumped = m.copy(tariffs = m.tariffs.map { it.copy(fullKurus = it.fullKurus * 3, discountedKurus = it.discountedKurus * 3, studentKurus = it.studentKurus * 3) })
        assertTrue(ManifestValidator.validate(jumped, m).warnings.isNotEmpty())
    }
}
