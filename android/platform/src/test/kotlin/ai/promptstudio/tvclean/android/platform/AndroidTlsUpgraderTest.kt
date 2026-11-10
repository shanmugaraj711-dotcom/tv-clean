package ai.promptstudio.tvclean.android.platform

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import ai.promptstudio.tvclean.adb.pairing.TlsIdentity
import ai.promptstudio.tvclean.core.model.AdbException
import ai.promptstudio.tvclean.core.model.Sha256
import java.net.Socket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.Timeout

// Plain JVM test: the upgrader and the TLS sockets use java.net / javax.net.ssl only, so no
// Robolectric is needed. A loopback SSLServerSocket stands in for the TV: it presents its own
// self-signed certificate (a real TlsIdentity) and asks for the phone's client certificate, the
// same two things a TV's adbd does in an STLS connect.
class AndroidTlsUpgraderTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds(60)

    private fun keyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA").also { it.initialize(2048) }.generateKeyPair()

    // A TLS server that accepts one connection, shakes hands (asking for the client certificate),
    // then echoes back whatever the client sends, prefixed with "echo:".
    private fun startEchoServer(serverIdentity: TlsIdentity): SSLServerSocket {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setKeyEntry("tv", serverIdentity.privateKey, null, arrayOf(serverIdentity.certificate))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, null) }
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ctx = SSLContext.getInstance("TLSv1.3").apply {
            init(kmf.keyManagers, arrayOf<TrustManager>(trustAll), null)
        }
        val server = ctx.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        server.needClientAuth = true
        thread(name = "tls-echo-server", isDaemon = true) {
            runCatching {
                val socket = server.accept() as SSLSocket
                socket.startHandshake()
                val buf = ByteArray(64)
                val n = socket.inputStream.read(buf)
                val reply = "echo:".toByteArray() + buf.copyOf(n)
                socket.outputStream.write(reply)
                socket.outputStream.flush()
                socket.close()
            }
        }
        return server
    }

    private fun upgradeTo(server: SSLServerSocket, identity: TlsIdentity, pin: Sha256?): AndroidTlsUpgrader = runBlocking {
        val upgrader = AndroidTlsUpgrader { identity }
        val raw = Socket("127.0.0.1", server.localPort)
        val tls = upgrader.upgrade(TcpAdbSocket(raw), pin)
        val write = "hello".toByteArray()
        withTimeout(10_000) {
            tls.write(write, 0, write.size)
            tls.flush()
            val buf = ByteArray(64)
            var read = 0
            while (read < 10) {
                val n = tls.read(buf, read, buf.size - read)
                if (n <= 0) break
                read += n
            }
            assertEquals("echo:hello", String(buf, 0, read))
        }
        tls.close()
        upgrader
    }

    @Test fun upgradeWithNoPinSucceedsAndRecordsTheServerFingerprint() {
        val client = TlsIdentity(keyPair())
        val serverIdentity = TlsIdentity(keyPair())
        val server = startEchoServer(serverIdentity)
        val upgrader = upgradeTo(server, client, null)
        assertEquals(serverIdentity.certificateSha256Hex, upgrader.lastServerCertSha256)
        server.close()
    }

    @Test fun upgradeWithTheRightPinSucceeds() {
        val client = TlsIdentity(keyPair())
        val serverIdentity = TlsIdentity(keyPair())
        val server = startEchoServer(serverIdentity)
        upgradeTo(server, client, Sha256(serverIdentity.certificateSha256Hex))
        server.close()
    }

    @Test fun upgradeWithTheWrongPinFailsAndClosesTheSocket() {
        val client = TlsIdentity(keyPair())
        val serverIdentity = TlsIdentity(keyPair())
        val server = startEchoServer(serverIdentity)
        val upgrader = AndroidTlsUpgrader { client }
        val raw = Socket("127.0.0.1", server.localPort)
        assertFailsWith<AdbException> {
            runBlocking<Unit> { upgrader.upgrade(TcpAdbSocket(raw), Sha256("0".repeat(64))) }
        }
        assertTrue(raw.isClosed, "a failed upgrade closes the plain socket")
        server.close()
    }

    @Test fun upgradeOfANonTcpSocketFails() {
        val client = TlsIdentity(keyPair())
        val upgrader = AndroidTlsUpgrader { client }
        val notTcp = object : ai.promptstudio.tvclean.adb.wire.AdbSocket {
            override suspend fun read(buf: ByteArray, off: Int, len: Int): Int = -1
            override suspend fun write(buf: ByteArray, off: Int, len: Int) {}
            override suspend fun flush() {}
            override fun close() {}
        }
        assertFailsWith<AdbException> {
            runBlocking<Unit> { upgrader.upgrade(notTcp, null) }
        }
    }
}
