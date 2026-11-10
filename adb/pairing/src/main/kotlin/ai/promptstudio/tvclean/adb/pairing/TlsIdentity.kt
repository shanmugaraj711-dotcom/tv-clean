// TV CLEAN 0.2 — the phone's TLS identity for ADB wireless debugging.
//
// adbd's TLS (pairing and connect) authenticates the phone by a client certificate whose
// public key is the phone's ADB RSA-2048 key — the same key the plain AUTH handshake signs
// with. The certificate is self-signed: adbd checks the public key inside it against the
// keys it has authorised (adb_keys), not a CA chain. So the certificate is just a carrier
// for the public key, and it must be signed by the matching private key.
//
// The signing seam is a lambda over the SHA-256 DigestInfo, not a JCA Signature name:
// the Keystore key (StrongBox/TEE) was created with DIGEST_NONE and PURPOSE_SIGN alone,
// so it can only do NONEwithRSA — the caller signs DigestInfo(SHA-256) || SHA-256(data)
// exactly the way AdbSignature signs DigestInfo(SHA-1) || token. A plain in-memory key
// signs the same bytes through the default provider. One code path, both key backings.

package ai.promptstudio.tvclean.adb.pairing

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.operator.ContentSigner

// The phone's TLS client identity: the self-signed certificate carrying the ADB public
// key, plus the private key that signs with it (an in-memory key, or the AndroidKeyStore
// key object whose signing happens inside secure hardware).
data class TlsIdentity(val certificate: X509Certificate, val privateKey: PrivateKey) {
    // SHA-256 of the DER certificate, the value the TV's trust record pins (TrustRecord.tlsCertSha256).
    val certificateSha256Hex: String
        get() = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
}

// Builds the self-signed ADB client certificate.
object AdbCertificate {
    // DER prefix of DigestInfo for SHA-256 (RFC 8017): the 19 bytes before the 32-byte hash.
    // (0x86, 0x48, 0x65 are above 127, so they need an explicit .toByte(); Kotlin hex literals
    // are Int, and byteArrayOf wants Byte.)
    val SHA256_DIGEST_INFO: ByteArray = byteArrayOf(
        0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86.toByte(), 0x48.toByte(), 0x01, 0x65.toByte(), 0x03, 0x04, 0x02, 0x01,
        0x05, 0x00, 0x04, 0x20,
    )
    const val DIGEST_INFO_BYTES = 19 + 32

    // The certificate subject. adbd does not check it; it is here so a human reading a
    // certificate dump can tell whose key this is.
    const val SUBJECT = "CN=TV Clean ADB Client, OU=TV Clean, O=PromptStudioAI"

    // 30-year validity: the ADB key is long-lived and the TV re-authorises it only when the
    // phone's key is reset.
    const val VALIDITY_DAYS = 30L * 365
    // Fixed validity start: 2026-01-01 00:00 UTC, so the certificate is deterministic per key.
    const val FIXED_NOT_BEFORE_MS = 1767225600000L

    // A ContentSigner over SHA-256 DigestInfo + NONEwithRSA, the one signing operation both
    // key backings support. `signDigestInfo` receives the 51-byte DigestInfo(SHA-256)||hash
    // and returns the 256-byte RSA signature.
    class DigestInfoContentSigner(private val signDigestInfo: (ByteArray) -> ByteArray) : ContentSigner {
        private val buffer = ByteArrayOutputStream()
        override fun getAlgorithmIdentifier(): AlgorithmIdentifier =
            AlgorithmIdentifier(ASN1ObjectIdentifier("1.2.840.113549.1.1.11")) // sha256WithRSAEncryption
        override fun getOutputStream(): OutputStream = buffer
        override fun getSignature(): ByteArray {
            val hash = MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray())
            require(hash.size == 32)
            return signDigestInfo(SHA256_DIGEST_INFO + hash)
        }
    }

    // Self-sign from an in-memory key pair (software key, tests): the standard JCA path.
    fun selfSign(keyPair: KeyPair): X509Certificate {
        val private = keyPair.private
        return selfSign(keyPair.public as RSAPublicKey) { digestInfo ->
            Signature.getInstance("NONEwithRSA").run {
                initSign(private); update(digestInfo); sign()
            }
        }
    }

    // Self-sign from a public key plus a signing lambda (Keystore key): the lambda signs the
    // 51-byte DigestInfo(SHA-256)||hash with NONEwithRSA through the key's own provider.
    //
    // The certificate is DETERMINISTIC for a given public key: the serial is derived from the
    // key itself and the validity window is fixed (2026-01-01 + 30 years). That matters
    // because the phone rebuilds this certificate on every TLS connect and every pairing,
    // and its SHA-256 fingerprint must be the same each time - a cert stamped with "now"
    // and a random serial would fingerprint differently on every run, so no trust record
    // could ever pin it. (The window starts 2026-01-01, before this app's first release,
    // so any TV with a sane clock sees the cert as valid.)
    fun selfSign(publicKey: RSAPublicKey, signDigestInfo: (ByteArray) -> ByteArray): X509Certificate {
        val keyHash = MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
        val serial = BigInteger(1, keyHash.copyOfRange(0, 8))
        val notBefore = Date(FIXED_NOT_BEFORE_MS)
        val notAfter = Date(FIXED_NOT_BEFORE_MS + VALIDITY_DAYS * 24 * 60 * 60 * 1000L)
        val name = X500Name(SUBJECT)
        val builder = X509v3CertificateBuilder(
            name, serial, notBefore, notAfter, name,
            org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.getInstance(publicKey.encoded),
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage, true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
        )
        builder.addExtension(
            Extension.subjectKeyIdentifier, false,
            JcaX509ExtensionUtils().createSubjectKeyIdentifier(publicKey),
        )
        val holder = builder.build(DigestInfoContentSigner(signDigestInfo))
        return JcaX509CertificateConverter().getCertificate(holder)
    }
}

// The identity (certificate + private key) for a key pair the caller already holds in memory.
fun TlsIdentity(keyPair: KeyPair): TlsIdentity =
    TlsIdentity(AdbCertificate.selfSign(keyPair), keyPair.private)

// The identity for a Keystore-backed key: the certificate is self-signed through the key's
// own signing lambda, and the private key object is carried for the TLS KeyManager.
fun TlsIdentity(publicKey: PublicKey, privateKey: PrivateKey, signDigestInfo: (ByteArray) -> ByteArray): TlsIdentity =
    TlsIdentity(AdbCertificate.selfSign(publicKey as RSAPublicKey, signDigestInfo), privateKey)
