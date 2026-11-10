package ai.promptstudio.tvclean.adb.pairing

import ai.promptstudio.tvclean.adb.wire.AdbPublicKeyCodec
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCryptoTest {

    private fun keyMaterial(): ByteArray = ByteArray(64) { (it * 7 + 1).toByte() }

    @Test fun aesKeyIsSixteenBytesAndDeterministic() {
        val k1 = PairingCrypto.deriveAesKey(keyMaterial())
        val k2 = PairingCrypto.deriveAesKey(keyMaterial())
        assertEquals(16, k1.size)
        assertEquals(k1.toHex(), k2.toHex())
        val k3 = PairingCrypto.deriveAesKey(ByteArray(64) { 0x11 })
        assertNotEquals(k1.toHex(), k3.toHex())
    }

    @Test fun peerInfoEncryptDecryptRoundtrips() {
        val aesKey = PairingCrypto.deriveAesKey(keyMaterial())
        val plain = ByteArray(PairingCrypto.PEER_INFO_SIZE) { (it % 251).toByte() }
        val wire = PairingCrypto.encryptPeerInfo(aesKey, plain)
        assertEquals(PairingCrypto.PEER_INFO_WIRE_SIZE, wire.size)
        val back = PairingCrypto.decryptPeerInfo(aesKey, wire)
        assertEquals(plain.toHex(), back.toHex())
    }

    @Test fun decryptWithTheWrongKeyFails() {
        val aesKey = PairingCrypto.deriveAesKey(keyMaterial())
        val plain = ByteArray(PairingCrypto.PEER_INFO_SIZE) { 0x55 }
        val wire = PairingCrypto.encryptPeerInfo(aesKey, plain)
        val wrongKey = PairingCrypto.deriveAesKey(ByteArray(64) { 0x22 })
        try {
            PairingCrypto.decryptPeerInfo(wrongKey, wire)
            assertTrue("expected an AEAD authentication failure", false)
        } catch (e: Exception) {
            // expected: GCM tag verification fails
        }
    }

    @Test fun ourPeerInfoRoundtripsThroughTheWireFormat() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = keyPair.public as RSAPublicKey
        val line = AdbPublicKeyCodec.line(publicKey)
        val plain = PairingCrypto.buildOurPeerInfo(line)
        assertEquals(PairingCrypto.PEER_INFO_SIZE, plain.size)
        assertEquals(PairingCrypto.PEER_TYPE_RSA_KEY, plain[0])
        val identity = PairingCrypto.parsePeerInfo(plain) as PairingCrypto.PeerIdentity.RsaKey
        assertEquals(AdbPublicKeyCodec.fingerprint(publicKey), identity.fingerprint)
        assertEquals(
            AdbPublicKeyCodec.encode(publicKey).toHex(),
            AdbPublicKeyCodec.encode(identity.key).toHex(),
        )
        // And through encryption, as it goes over the wire.
        val aesKey = PairingCrypto.deriveAesKey(keyMaterial())
        val wire = PairingCrypto.encryptPeerInfo(aesKey, plain)
        val back = PairingCrypto.parsePeerInfo(PairingCrypto.decryptPeerInfo(aesKey, wire))
            as PairingCrypto.PeerIdentity.RsaKey
        assertEquals(identity.fingerprint, back.fingerprint)
    }

    @Test fun guidPeerInfoParses() {
        val plain = ByteArray(PairingCrypto.PEER_INFO_SIZE)
        plain[0] = PairingCrypto.PEER_TYPE_GUID
        val guid = ByteArray(16) { (it + 1).toByte() }
        guid.copyInto(plain, 1)
        val identity = PairingCrypto.parsePeerInfo(plain) as PairingCrypto.PeerIdentity.Guid
        assertEquals(guid.toHex(), identity.bytes.toHex())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
