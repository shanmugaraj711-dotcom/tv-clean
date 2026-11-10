package ai.promptstudio.tvclean.adb.pairing

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.security.MessageDigest
import java.util.Hashtable
import java.util.Vector
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.tls.Certificate
import org.bouncycastle.tls.CertificateRequest
import org.bouncycastle.tls.DefaultTlsClient
import org.bouncycastle.tls.ProtocolName
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.SignatureAndHashAlgorithm
import org.bouncycastle.tls.TlsAuthentication
import org.bouncycastle.tls.TlsClientProtocol
import org.bouncycastle.tls.TlsCredentials
import org.bouncycastle.tls.TlsExtensionsUtils
import org.bouncycastle.tls.TlsServerCertificate
import org.bouncycastle.tls.crypto.TlsCryptoParameters
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto

// The phone side of ADB TLS pairing (AOSP pairing_connection.cpp), for TVs that only offer
// wireless debugging behind a pairing code.
//
// Flow, over a plain TCP socket to the TV's pairing port (from mDNS _adb-tls-pairing._tcp):
//   1. TLS 1.3 handshake, ALPN "adb", our self-signed cert as the client certificate,
//      the TV's self-signed cert accepted (its SHA-256 is recorded for the trust record).
//   2. Export 64 bytes of TLS keying material, label "adb-label" (the label AOSP passes
//      includes its NUL terminator, so 10 bytes - pass it as "adb-label\u0000").
//   3. SPAKE2 password = pairing code (ASCII, as the user typed it) + the 64 exported bytes.
//      Exchange the two 32-byte SPAKE2 messages, derive 64 bytes of key material.
//   4. AES-128-GCM key from that material; exchange the two 8192-byte PeerInfos encrypted
//      as PEER_INFO frames. Ours carries our ADB RSA public key (so the TV authorizes it);
//      theirs carries the TV's ADB key (or GUID).
//   5. Returns the TV's TLS certificate fingerprint and its ADB identity, for the trust
//      record - the next plain/TLS ADB connect can then pin the TV certificate.
//
// Pairing frames (pairing_connection.cpp): 6-byte header - version (1), type, payload size
// (uint32, network byte order). Type 0 = SPAKE2_MSG, type 1 = PEER_INFO. Max payload 16384.
//
// UNVERIFIED against a real TV: the SPAKE2 maths, the framing, the HKDF/AES-GCM and the
// PeerInfo layout are all grounded in BoringSSL/AOSP source and covered by unit tests,
// but no real TV has run this code yet. The first real pairing is the true test; the
// failure modes to watch are the exported-keying-material label and the PeerInfo counter.
data class PairingResult(
    val serverCertSha256Hex: String,
    val peerIdentity: PairingCrypto.PeerIdentity,
)

class PairingException(message: String, cause: Throwable? = null) : Exception(message, cause)

class PairingClient(
    private val identityProvider: () -> TlsIdentity,
    private val connectSocket: (host: String, port: Int) -> Socket = { host, port -> Socket(host, port) },
) {
    companion object {
        const val PROTOCOL_VERSION: Byte = 1
        const val TYPE_SPAKE2_MSG: Byte = 0
        const val TYPE_PEER_INFO: Byte = 1
        const val MAX_PAYLOAD_SIZE = 8192 * 2
        const val EXPORTED_KEY_BYTES = 64
        // AOSP passes the exporter label with its NUL terminator (sizeof = 10).
        const val KEYING_MATERIAL_LABEL = "adb-label\u0000"
        const val ALPN_PROTOCOL = "adb"
    }

    /** Pair with the TV at [host]:[port] using the 6-digit [pairingCode] the TV shows. */
    fun pair(host: String, port: Int, pairingCode: String, ourPublicKeyLine: String): PairingResult {
        val identity = identityProvider()
        val socket = try {
            connectSocket(host, port)
        } catch (e: Exception) {
            throw PairingException("cannot reach the TV pairing port $host:$port", e)
        }
        socket.use { s ->
            try {
                val protocol = TlsClientProtocol(s.getInputStream(), s.getOutputStream())
                var serverCertSha256: String? = null
                val client = AdbPairingTlsClient(identity) { hex -> serverCertSha256 = hex }
                protocol.connect(client)
                val tlsIn = protocol.inputStream
                val tlsOut = protocol.outputStream

                // The SPAKE2 password is the code plus the exported TLS keying material.
                val exported = client.exportKeyingMaterial(
                    KEYING_MATERIAL_LABEL, null, EXPORTED_KEY_BYTES,
                )
                val password = pairingCode.toByteArray(Charsets.US_ASCII) + exported

                val spake2 = Spake2.forPairing(password)
                val myMsg = spake2.generateMsg()
                writeFrame(tlsOut, TYPE_SPAKE2_MSG, myMsg)
                val theirMsg = readFrame(tlsIn, TYPE_SPAKE2_MSG)
                val keyMaterial = spake2.processMsg(theirMsg)

                val aesKey = PairingCrypto.deriveAesKey(keyMaterial)
                val ourPeerInfo = PairingCrypto.buildOurPeerInfo(ourPublicKeyLine)
                writeFrame(tlsOut, TYPE_PEER_INFO, PairingCrypto.encryptPeerInfo(aesKey, ourPeerInfo))
                val theirWire = readFrame(tlsIn, TYPE_PEER_INFO)
                val theirPeerInfo = PairingCrypto.decryptPeerInfo(aesKey, theirWire)
                val peerIdentity = PairingCrypto.parsePeerInfo(theirPeerInfo)
                return PairingResult(
                    serverCertSha256Hex = serverCertSha256
                        ?: throw PairingException("the TV sent no certificate"),
                    peerIdentity = peerIdentity,
                )
            } catch (e: PairingException) {
                throw e
            } catch (e: Exception) {
                throw PairingException("pairing with $host:$port failed: ${e.message}", e)
            }
        }
    }

    private fun writeFrame(out: OutputStream, type: Byte, payload: ByteArray) {
        require(payload.size <= MAX_PAYLOAD_SIZE) { "pairing payload too large" }
        val header = ByteArray(6)
        header[0] = PROTOCOL_VERSION
        header[1] = type
        header[2] = ((payload.size ushr 24) and 0xff).toByte()
        header[3] = ((payload.size ushr 16) and 0xff).toByte()
        header[4] = ((payload.size ushr 8) and 0xff).toByte()
        header[5] = (payload.size and 0xff).toByte()
        out.write(header)
        out.write(payload)
        out.flush()
    }

    private fun readFrame(input: InputStream, expectedType: Byte): ByteArray {
        val header = ByteArray(6)
        readFully(input, header)
        require(header[0] == PROTOCOL_VERSION) { "pairing protocol version ${header[0]}" }
        require(header[1] == expectedType) {
            "expected pairing message type $expectedType, got ${header[1]}"
        }
        val size = ((header[2].toInt() and 0xff) shl 24) or
            ((header[3].toInt() and 0xff) shl 16) or
            ((header[4].toInt() and 0xff) shl 8) or
            (header[5].toInt() and 0xff)
        require(size in 0..MAX_PAYLOAD_SIZE) { "pairing payload size $size is out of range" }
        val payload = ByteArray(size)
        readFully(input, payload)
        return payload
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val read = input.read(buf, offset, buf.size - offset)
            if (read < 0) throw PairingException("pairing connection closed mid-message")
            offset += read
        }
    }

    // A BC TLS client pinned to TLS 1.3 with ALPN "adb", presenting our self-signed client
    // certificate and accepting the TV's self-signed certificate (its fingerprint is recorded
    // so the trust record can pin it on later connects).
    private class AdbPairingTlsClient(
        private val identity: TlsIdentity,
        private val onServerCert: (String) -> Unit,
    ) : DefaultTlsClient(BcTlsCrypto()) {
        override fun getSupportedVersions(): Array<ProtocolVersion> =
            arrayOf(ProtocolVersion.TLSv13)

        override fun getClientExtensions(): Hashtable<*, *> {
            @Suppress("UNCHECKED_CAST")
            val extensions = super.getClientExtensions() as Hashtable<Any, Any>
            TlsExtensionsUtils.addALPNExtensionClient(
                extensions,
                Vector<ProtocolName>().apply { add(ProtocolName.asUtf8Encoding(ALPN_PROTOCOL)) },
            )
            return extensions
        }

        override fun getAuthentication(): TlsAuthentication = object : TlsAuthentication {
            override fun notifyServerCertificate(serverCertificate: TlsServerCertificate) {
                val der = serverCertificate.certificate.getCertificateAt(0).encoded
                val sha = MessageDigest.getInstance("SHA-256").digest(der)
                onServerCert(sha.joinToString("") { "%02x".format(it) })
            }

            override fun getClientCredentials(request: CertificateRequest): TlsCredentials {
                // The crypto the protocol initialized us with - the same instance that runs
                // the handshake, so the signer below uses the same SecureRandom and parameters.
                val bcCrypto = context.crypto as BcTlsCrypto
                val tlsCert = bcCrypto.createCertificate(identity.certificate.encoded)
                val certificate = Certificate(arrayOf(tlsCert))
                val keyParams = PrivateKeyFactory.createKey(identity.privateKey.encoded)
                val params = TlsCryptoParameters(context)
                return BcDefaultTlsCredentialedSigner(
                    params, bcCrypto, keyParams, certificate,
                    SignatureAndHashAlgorithm.rsa_pss_rsae_sha256,
                )
            }
        }

        fun exportKeyingMaterial(label: String, contextValue: ByteArray?, length: Int): ByteArray =
            context.exportKeyingMaterial(label, contextValue, length)
    }
}
