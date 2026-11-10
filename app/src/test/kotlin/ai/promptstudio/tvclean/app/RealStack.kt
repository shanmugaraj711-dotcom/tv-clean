package ai.promptstudio.tvclean.app

import kotlinx.coroutines.test.TestScope
import ai.promptstudio.tvclean.adb.session.AdbSessionManager
import ai.promptstudio.tvclean.adb.wire.SoftwareRsaAdbSigner
import ai.promptstudio.tvclean.core.engine.DefaultEngine
import ai.promptstudio.tvclean.core.engine.DocumentPlanStore
import ai.promptstudio.tvclean.core.flows.CopyKey
import ai.promptstudio.tvclean.core.flows.FlowCopy
import ai.promptstudio.tvclean.core.model.Endpoint
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.TvId
import ai.promptstudio.tvclean.core.ops.ShellRenderer
import ai.promptstudio.tvclean.core.planner.Plan
import ai.promptstudio.tvclean.core.planner.PlanKind
import ai.promptstudio.tvclean.core.session.OpenResult
import ai.promptstudio.tvclean.core.session.TvSession
import ai.promptstudio.tvclean.core.store.Clock
import ai.promptstudio.tvclean.core.store.GateIssuer
import ai.promptstudio.tvclean.core.store.GateMethod
import ai.promptstudio.tvclean.core.store.GatePass
import ai.promptstudio.tvclean.testing.fakeadbd.FakeAdbd
import ai.promptstudio.tvclean.testing.fakeadbd.FakeAdbdSocketFactory
import ai.promptstudio.tvclean.testing.fakeadbd.FakeTv
import ai.promptstudio.tvclean.testing.fakeadbd.Faults
import ai.promptstudio.tvclean.testing.fakeadbd.Personas
import ai.promptstudio.tvclean.testing.fixtures.FakeProber
import ai.promptstudio.tvclean.testing.fixtures.MemoryDocumentStore
import ai.promptstudio.tvclean.testing.fixtures.MemoryJournal
import ai.promptstudio.tvclean.testing.fixtures.MemorySnapshotStore
import ai.promptstudio.tvclean.testing.fixtures.MemoryTvRepository
import ai.promptstudio.tvclean.testing.fixtures.Samples
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertIs

const val TV_HOST = "192.0.2.10"

object TestKeys {
    val signer: SoftwareRsaAdbSigner by lazy { SoftwareRsaAdbSigner(SoftwareRsaAdbSigner.generateKeyPair()) }
}

// The objects the graph wires, over the fake adbd and the test scheduler: the real
// AdbSessionManager, AdbTvSession, ShellRenderer and DefaultEngine, and the graph's
// LiveSessionEngine in front of the engine. The probe is the fixtures' FakeProber (the reference
// TV's snapshot): W4's batched probe scripts are tested against recordings, not the fake adbd.
class RealStack(val test: TestScope, tv: FakeTv = Personas.miTv()) {
    val adbd = FakeAdbd(tv, Faults(), test.backgroundScope).also { it.trust(TestKeys.signer) }
    val clock = object : Clock {
        override fun nowMs(): Long = Samples.NOW_MS + test.testScheduler.currentTime
        override fun monotonicMs(): Long = test.testScheduler.currentTime
    }
    val sessions = AdbSessionManager(FakeAdbdSocketFactory(mapOf(TV_HOST to adbd)), TestKeys.signer, null, clock, test.backgroundScope, EmptyCoroutineContext)
    val endpoint = Endpoint.PlainTcp(TV_HOST)
    val renderer = ShellRenderer()
    val prober = FakeProber()
    val journal = MemoryJournal()
    val snapshots = MemorySnapshotStore()
    val documents = MemoryDocumentStore()
    val tvs = MemoryTvRepository()
    val raw = DefaultEngine(renderer, prober, journal, snapshots, DocumentPlanStore(documents), clock, test.backgroundScope)
    val engine = LiveSessionEngine(raw, sessions, tvs)

    suspend fun open(): TvSession = assertIs<OpenResult.Ok>(sessions.open(endpoint, null) {}).session

    // The TV record the flows keep after the first connect (its address is what a re-open uses).
    suspend fun remember(tvId: TvId) = tvs.upsert(Samples.tv(id = tvId).copy(endpoints = listOf(endpoint)))

    // A one-step plan as the planner hands it over, rendered by the real renderer.
    fun plan(tvId: TvId, id: String = "01JT7PLAN00000000000000001"): Plan =
        Samples.plan(listOf(Op.Disable(PackageName("com.xiaomi.floatingframe"))), tvId = tvId, renderer = renderer,
            kind = PlanKind.SWITCH_OFF, id = ai.promptstudio.tvclean.core.model.PlanId(id))

    @OptIn(GateIssuer::class)
    fun pass(tvId: TvId): GatePass = GatePass(tvId, GateMethod.NONE, clock.nowMs(), clock.nowMs() + 3_600_000)
}

object PlainCopy : FlowCopy {
    override fun text(key: CopyKey, args: Map<String, Any>): String = key.name
    override fun date(epochMs: Long): String = epochMs.toString()
    override fun dateTime(epochMs: Long): String = epochMs.toString()
}
