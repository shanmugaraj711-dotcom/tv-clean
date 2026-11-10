package ai.promptstudio.tvclean.adb.pairing

import ai.promptstudio.tvclean.adb.wire.AdbPublicKeyCodec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters

// The encryption around the PeerInfo exchange in ADB TLS pairing (AOSP pairing_auth.cpp).
//
//   AES key = HKDF-SHA256(salt = null, ikm = SPAKE2 key material, info =
//             "adb pairing_auth aes-128-gcm key", L = 16)
//   nonce   = 12 bytes; the first 8 are the little-endian message counter, the rest zero.
//             Both sides start their counter at zero and use counter 0 for the one PeerInfo
//             message each direction sends, so the same (key, nonce) encrypts both messages.
//             That is what AOSP does (one cipher per direction, counters independent), so
//             replicate it for wire compatibility instead of "fixing" it.
//   wire    = ciphertext (8192 bytes) || 16-byte GCM tag, so a PEER_INFO payload is 8208 bytes.
//
// PeerInfo (8192 bytes, plaintext):
//   byte 0     = type: 0 = ADB RSA public key, 1 = device GUID
//   bytes 1..  = for type 0: the ADB authorized-key line
//                (base64 RSA public-key struct + " " + comment), NUL padded to 8192.
//                For type 1: a 16-byte GUID, NUL padded.
// The phone sends type 0 (its ADB RSA key, so the TV authorizes it); the TV answers with
// its own type 0 key (or a type 1 GUID). Parse the answer to learn the TV's ADB identity.
object PairingCrypto {
    const val PEER_INFO_SIZE = 8192
    const val GCM_TAG_BYTES = 16
    const val PEER_INFO_WIRE_SIZE = PEER_INFO_SIZE + GCM_TAG_BYTES
    const val AES_KEY_BYTES = 16
    const val NONCE_BYTES = 12
    val HKDF_INFO: ByteArray = "adb pairing_auth aes-128-gcm key".toByteArray(Charsets.US_ASCII)

    const val PEER_TYPE_RSA_KEY: Byte = 0
    const val PEER_TYPE_GUID: Byte = 1

    fun deriveAesKey(keyMaterial: ByteArray): ByteArray {
        val hkdf = HKDFBytesGenerator(org.bouncycastle.crypto.digests.SHA256Digest())
        hkdf.init(HKDFParameters(keyMaterial, null, HKDF_INFO))
        return ByteArray(AES_KEY_BYTES).also { hkdf.generateBytes(it, 0, it.size) }
    }

    private fun nonce(counter: Long): ByteArray {
        val n = ByteArray(NONCE_BYTES)
        ByteBuffer.wrap(n).order(ByteOrder.LITTLE_ENDIAN).putLong(counter)
        return n
    }

    /** Encrypt one 8192-byte PeerInfo; returns ciphertext || tag (8208 bytes). */
    fun encryptPeerInfo(aesKey: ByteArray, plaintext: ByteArray, counter: Long = 0L): ByteArray {
        require(plaintext.size == PEER_INFO_SIZE) { "PeerInfo is $PEER_INFO_SIZE bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BYTES * 8, nonce(counter)))
        return cipher.doFinal(plaintext)
    }

    /** Decrypt one 8208-byte PEER_INFO payload; returns the 8192-byte PeerInfo plaintext. */
    fun decryptPeerInfo(aesKey: ByteArray, wire: ByteArray, counter: Long = 0L): ByteArray {
        require(wire.size == PEER_INFO_WIRE_SIZE) { "PEER_INFO payload is $PEER_INFO_WIRE_SIZE bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BYTES * 8, nonce(counter)))
        val plain = cipher.doFinal(wire)
        require(plain.size == PEER_INFO_SIZE) { "PeerInfo decrypted to ${plain.size} bytes" }
        return plain
    }

    /** Build our 8192-byte PeerInfo carrying our ADB RSA public key. */
    fun buildOurPeerInfo(publicKeyLine: String): ByteArray {
        val out = ByteArray(PEER_INFO_SIZE)
        out[0] = PEER_TYPE_RSA_KEY
        val line = publicKeyLine.toByteArray(Charsets.US_ASCII)
        require(line.size <= PEER_INFO_SIZE - 1) { "public key line is too long for PeerInfo" }
        line.copyInto(out, 1)
        return out
    }

    sealed interface PeerIdentity {
        data class RsaKey(val key: RSAPublicKey, val fingerprint: String) : PeerIdentity
        data class Guid(val bytes: ByteArray) : PeerIdentity
    }

    /** Parse a decrypted PeerInfo into the peer's identity. */
    fun parsePeerInfo(plain: ByteArray): PeerIdentity {
        require(plain.size == PEER_INFO_SIZE) { "PeerInfo is $PEER_INFO_SIZE bytes" }
        return when (plain[0]) {
            PEER_TYPE_RSA_KEY -> {
                val line = plain.copyOfRange(1, plain.size)
                    .toString(Charsets.US_ASCII).trimEnd('\u0000', '\n', '\r')
                val key = AdbPublicKeyCodec.parseLine(line)
                PeerIdentity.RsaKey(key, AdbPublicKeyCodec.fingerprint(key))
            }
            PEER_TYPE_GUID -> PeerIdentity.Guid(plain.copyOfRange(1, 17))
            else -> throw IllegalArgumentException("unknown PeerInfo type ${plain[0]}")
        }
    }
}
