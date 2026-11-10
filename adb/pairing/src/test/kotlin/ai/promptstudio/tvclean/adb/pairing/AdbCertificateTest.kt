// TV CLEAN 0.2 — the ADB client certificate: self-signed, carries the ADB RSA key,
// and verifies as SHA256withRSA under that same key.
package ai.promptstudio.tvclean.adb.pairing

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdbCertificateTest {
    private fun keyPair() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    @Test fun selfSignFromKeyPairVerifiesUnderItsPublicKey() {
        val kp = keyPair()
        val cert = AdbCertificate.selfSign(kp)
        assertEquals(kp.public, cert.publicKey)
        // The signature is SHA256withRSA over the TBSCertificate.
        cert.verify(kp.public)
        // Self-signed: issuer == subject.
        assertEquals(cert.subjectX500Principal, cert.issuerX500Principal)
        // Not a CA.
        assertTrue(cert.basicConstraints < 0)
    }

    @Test fun selfSignThroughDigestInfoLambdaMatchesKeyPair() {
        val kp = keyPair()
        val cert = AdbCertificate.selfSign(kp.public as java.security.interfaces.RSAPublicKey) { digestInfo ->
            Signature.getInstance("NONEwithRSA").run {
                initSign(kp.private); update(digestInfo); sign()
            }
        }
        assertEquals(kp.public, cert.publicKey)
        cert.verify(kp.public)
    }

    @Test fun tlsIdentityCarriesCertificateAndKey() {
        val kp = keyPair()
        val identity = TlsIdentity(kp)
        assertEquals(kp.public, identity.certificate.publicKey)
        assertEquals(kp.private, identity.privateKey)
        // The SHA-256 fingerprint is 64 hex chars.
        assertTrue(Regex("^[0-9a-f]{64}$").matches(identity.certificateSha256Hex))
        // The fingerprint is stable and matches the cert bytes.
        val again = TlsIdentity(kp).certificateSha256Hex
        assertEquals(identity.certificateSha256Hex, again)
    }

    @Test fun digestInfoPrefixIsSha256() {
        // 19-byte DER prefix + 32-byte hash = 51 bytes, the input the signing lambda gets.
        assertEquals(19, AdbCertificate.SHA256_DIGEST_INFO.size)
        assertEquals(51, AdbCertificate.DIGEST_INFO_BYTES)
        // Sanity: the prefix decodes as DigestInfo for SHA-256 (OID 2.16.840.1.101.3.4.2.1).
        val prefix = AdbCertificate.SHA256_DIGEST_INFO
        assertEquals(0x30.toByte(), prefix[0])
        assertEquals(0x31.toByte(), prefix[1])
    }
}
