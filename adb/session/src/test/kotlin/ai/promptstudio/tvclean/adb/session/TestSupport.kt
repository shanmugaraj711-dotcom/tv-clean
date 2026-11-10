package ai.promptstudio.tvclean.adb.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import ai.promptstudio.tvclean.adb.session.AdbTvSession
import ai.promptstudio.tvclean.adb.wire.SoftwareRsaAdbSigner
import ai.promptstudio.tvclean.core.model.BlobRef
import ai.promptstudio.tvclean.core.model.Endpoint
import ai.promptstudio.tvclean.core.model.RawShell
import ai.promptstudio.tvclean.core.model.Sha256
import ai.promptstudio.tvclean.core.model.ShellLine
import ai.promptstudio.tvclean.core.model.TvId
import ai.promptstudio.tvclean.core.session.OpenResult
import ai.promptstudio.tvclean.core.store.Clock
import ai.promptstudio.tvclean.testing.fakeadbd.FakeAdbd
import ai.promptstudio.tvclean.testing.fakeadbd.FakeAdbdSocketFactory
import ai.promptstudio.tvclean.testing.fakeadbd.FakeTv
import ai.promptstudio.tvclean.testing.fakeadbd.Faults
import ai.promptstudio.tvclean.testing.fakeadbd.Personas
import ai.promptstudio.tvclean.testing.fakeadbd.sha256Hex
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.createTempFile
import kotlin.test.assertIs

object Keys {
    val signer: SoftwareRsaAdbSigner by lazy { SoftwareRsaAdbSigner(SoftwareRsaAdbSigner.generateKeyPair()) }
}

// A Clock over the test scheduler so AwaitingAllow.sinceMs and the boot deadline use virtual time.
class TestClock(private val scope: TestScope) : Clock {
    override fun nowMs(): Long = 1_760_000_000_000L + scope.testScheduler.currentTime
    override fun monotonicMs(): Long = scope.testScheduler.currentTime
}

const val HOST = "192.0.2.10"

class Rig(scope: TestScope, tv: FakeTv = Personas.miTv(), val faults: Faults = Faults(), trusted: Boolean = true) {
    val adbd = FakeAdbd(tv, faults, scope.backgroundScope).also { if (trusted) it.trust(Keys.signer) }
    val factory = FakeAdbdSocketFactory(mapOf(HOST to adbd))
    val clock = TestClock(scope)
    val manager = AdbSessionManager(factory, Keys.signer, null, clock, scope.backgroundScope, EmptyCoroutineContext)
    val endpoint = Endpoint.PlainTcp(HOST)
    val tv: FakeTv get() = adbd.tv

    suspend fun open(expected: TvId? = null, onAllow: suspend (String) -> Unit = {}): AdbTvSession =
        assertIs<OpenResult.Ok>(manager.open(endpoint, expected, onAllow)).session as AdbTvSession
}

// Lets background work (the fake adbd, the session's reconnects) run for a while of virtual
// time. advanceUntilIdle stops as soon as only background tasks remain, so it is not enough here.
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.settle(ms: Long = 2_000) {
    advanceTimeBy(ms)
    runCurrent()
}

// What the fake saw, for failure messages.
fun Rig.diag(): String = "events=" + adbd.events.joinToString(" | ") +
    " connections=" + adbd.connections.joinToString { "closed=${it.closed} opened=${it.openedServices}" } +
    " attempts=" + factory.attempts.size

@OptIn(RawShell::class)
fun line(text: String): ShellLine = ShellLine(text)

// A temp file with the given bytes and its BlobRef.
fun blob(bytes: ByteArray, size: Long = bytes.size.toLong(), sha: String = sha256Hex(bytes)): BlobRef {
    val f = createTempFile("cyt-blob").toFile()
    f.deleteOnExit()
    f.writeBytes(bytes)
    return BlobRef(Sha256(sha), size, f.absolutePath)
}

fun BlobRef.file(): File = File(path)
