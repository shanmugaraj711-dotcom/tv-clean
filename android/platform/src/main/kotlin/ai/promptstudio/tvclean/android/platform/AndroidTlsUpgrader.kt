// TV CLEAN 0.2 — the TLS upgrade for ADB wireless debugging (STLS connect).
//
// When a TV answers the first CNXN with A_STLS, the handshake (ConnectionCore) sends back
// A_STLS and calls this upgrader, which turns the plain socket into a TLS 1.3 socket.
// The TV is a TLS server presenting its own self-signed certificate (derived from the
// TV's ADB key); the phone is a TLS client presenting its own self-signed certificate
// (the TlsIdentity, whose public key is the phone's ADB key).
//
// Trust: the TV's certificate is self-signed and there is no CA to check it against, so
// the first connect is trust-on-first-use — the certificate is accepted and its SHA-256
// is recorded (lastServerCertSha256) for the session to store in TrustRecord.tlsCertSha256
// and pin on later connects. When a pin is supplied, the server certificate must match it
// exactly, or the upgrade fails.

package ai.promptstudio.tvclean.android.platform

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ai.promptstudio.tvclean.adb.pairing.TlsIdentity
import ai.promptstudio.tvclean.adb.wire.AdbSocket
import ai.promptstudio.tvclean.adb.wire.TlsUpgrader
import ai.promptstudio.tvclean.core.model.AdbError
import ai.promptstudio.tvclean.core.model.AdbException
import ai.promptstudio.tvclean.core.model.Sha256
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

// Upgrades a connected plain ADB socket to TLS 1.3. The identity is resolved lazily (the
// phone's key is made in the background), so the provider is called on each upgrade.
class AndroidTlsUpgrader(
    private val identityProvider: suspend () -> TlsIdentity,
) : TlsUpgrader {

    // SHA-256 of the server certificate from the last upgrade (hex), for the trust record.
    // Null until an upgrade has completed.
    @Volatile var lastServerCertSha256: String? = null
        private set

    override suspend fun upgrade(socket: AdbSocket, pin: Sha256?): AdbSocket = withContext(Dispatchers.IO) {
        val raw = (socket as? TcpAdbSocket)?.socket
            ?: throw AdbException(AdbError.Transport.Protocol("TLS upgrade needs a TCP socket"))
        val identity = identityProvider()
        val sslContext = buildContext(identity, pin)
        val factory: SSLSocketFactory = sslContext.socketFactory
        val tls = try {
            (factory.createSocket(raw, raw.inetAddress.hostAddress, raw.port, true) as SSLSocket).apply {
                useClientMode = true
                // TLS 1.3 only, matching adbd (AOSP tls_connection.cpp pins TLS 1.3 both ways).
                enabledProtocols = arrayOf("TLSv1.3")
                startHandshake()
            }
        } catch (e: Exception) {
            runCatching { raw.close() }
            throw AdbException(AdbError.Transport.Protocol("TLS handshake failed: ${e.javaClass.simpleName}: ${e.message}"))
        }
        val serverCert = tls.session.peerCertificates.firstOrNull() as? X509Certificate
        lastServerCertSha256 = serverCert?.let {
            MessageDigest.getInstance("SHA-256").digest(it.encoded).joinToString("") { b -> "%02x".format(b) }
        }
        TlsAdbSocket(tls)
    }

    private fun buildContext(identity: TlsIdentity, pin: Sha256?): SSLContext {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setKeyEntry("tvclean-adb", identity.privateKey, null, arrayOf(identity.certificate))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, null)
        }
        val trustManager = PinningTrustManager(pin)
        return SSLContext.getInstance("TLSv1.3").apply {
            init(kmf.keyManagers, arrayOf<TrustManager>(trustManager), null)
        }
    }

    // Trust-on-first-use when no pin is given (accepts the self-signed TV certificate);
    // exact SHA-256 pinning when one is given.
    private class PinningTrustManager(private val pin: Sha256?) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val cert = chain.firstOrNull()
                ?: throw CertificateException("the TV sent no certificate")
            if (pin != null) {
                val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                    .joinToString("") { "%02x".format(it) }
                if (!actual.equals(pin.hex, ignoreCase = true)) {
                    throw CertificateException("the TV certificate does not match the pinned fingerprint")
                }
            }
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}

// An AdbSocket over a connected SSLSocket. close() closes the TLS session (and the TCP socket
// underneath it, since the SSLSocket was created with autoClose).
class TlsAdbSocket(private val socket: SSLSocket) : AdbSocket {
    private val input = socket.inputStream
    private val output = socket.outputStream

    override suspend fun read(buf: ByteArray, off: Int, len: Int): Int = withContext(Dispatchers.IO) {
        try { input.read(buf, off, len) } catch (e: java.io.IOException) { if (socket.isClosed) -1 else throw e }
    }

    override suspend fun write(buf: ByteArray, off: Int, len: Int) = withContext(Dispatchers.IO) {
        output.write(buf, off, len)
    }

    override suspend fun flush() = withContext(Dispatchers.IO) { output.flush() }

    override fun close() {
        runCatching { socket.close() }
    }
}
