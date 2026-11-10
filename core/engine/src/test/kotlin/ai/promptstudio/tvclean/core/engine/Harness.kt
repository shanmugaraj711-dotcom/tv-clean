package ai.promptstudio.tvclean.core.engine

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import ai.promptstudio.tvclean.core.model.BlobRef
import ai.promptstudio.tvclean.core.model.Component
import ai.promptstudio.tvclean.core.model.GroupDefault
import ai.promptstudio.tvclean.core.model.LauncherArtifact
import ai.promptstudio.tvclean.core.model.Level
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageEntry
import ai.promptstudio.tvclean.core.model.PackageGroup
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.Platform
import ai.promptstudio.tvclean.core.model.Profile
import ai.promptstudio.tvclean.core.model.ProfileMatch
import ai.promptstudio.tvclean.core.model.Quirk
import ai.promptstudio.tvclean.core.model.Sha256
import ai.promptstudio.tvclean.core.model.SnapshotReason
import ai.promptstudio.tvclean.core.ops.ShellRenderer
import ai.promptstudio.tvclean.core.planner.Choices
import ai.promptstudio.tvclean.core.planner.WizardWifi
import ai.promptstudio.tvclean.core.planner.DefaultPlanner
import ai.promptstudio.tvclean.core.planner.Plan
import ai.promptstudio.tvclean.core.planner.PlanRequest
import ai.promptstudio.tvclean.core.planner.PlanResult
import ai.promptstudio.tvclean.core.spec.ProfileResolution
import ai.promptstudio.tvclean.core.store.GatePass
import ai.promptstudio.tvclean.testing.fixtures.FakeCredentialGate
import ai.promptstudio.tvclean.testing.fixtures.FakeIdGen
import ai.promptstudio.tvclean.testing.fixtures.FakeProfileSource
import ai.promptstudio.tvclean.testing.fixtures.MapFactsTable
import ai.promptstudio.tvclean.testing.fixtures.MemoryDocumentStore
import ai.promptstudio.tvclean.testing.fixtures.MemoryJournal
import ai.promptstudio.tvclean.testing.fixtures.MemorySnapshotStore
import ai.promptstudio.tvclean.testing.fixtures.Samples
import ai.promptstudio.tvclean.testing.fixtures.sha256Of

// Everything an engine test needs: the scripted TV, in-memory stores, the real renderer and planner,
// a clock on the test scheduler and a gate that mints passes.
class Harness(val scope: TestScope, val tv: ScriptedTv = ScriptedTv(Samples.snapshot(packages = Samples.miTvPackages(emptySet())))) {
    val prober = ScriptedProber(tv)
    val journal = MemoryJournal()
    val snapshots = MemorySnapshotStore()
    val documents = MemoryDocumentStore()
    val plans = DocumentPlanStore(documents)
    val clock = SchedulerClock(scope.testScheduler)
    val renderer = ShellRenderer()
    val gate = FakeCredentialGate(clock)
    val idGen = FakeIdGen()
    val facts = MapFactsTable()
    val launcher = Samples.launcherComponent
    val stock = Samples.stockLauncher
    val wizard = PackageName("com.google.android.tungsten.setupwraith")
    val signer = Sha256("b".repeat(64))
    val apkBytes = "fake launcher apk".encodeToByteArray()
    val apkSha: Sha256 = tv.acceptApk(apkBytes, launcher.pkg, 1, signer)
    val artifact = LauncherArtifact(launcher.pkg, 1, "0.1.0", apkSha, signer, BlobRef(apkSha, apkBytes.size.toLong(), "bundled:launcher.apk"))
    val debloat = listOf("com.miui.tv.analytics", "com.xiaomi.statistic", "com.google.android.feedback", "com.google.android.videos", "com.google.android.play.games").map { PackageName(it) }

    fun profile(quirks: Set<Quirk> = emptySet(), setupWraith: PackageName? = null) = Profile(
        "xiaomi-mitv-axso0", ProfileMatch(listOf(tv.facts.model), emptyList(), null, null, null, null), Platform.ANDROID_TV, listOf(stock), null, setupWraith, quirks,
        listOf(PackageGroup("ads", "Adverts and telemetry", GroupDefault.DISABLE, debloat.map { PackageEntry(it, null) })), emptySet(), emptyList(), null, Sha256("1".repeat(64)),
    )

    var profiles = FakeProfileSource(listOf(profile()))
    val transport = FileOverlayTransport(prober)
    val planner = DefaultPlanner(clock, idGen, renderer, transport, "0.1.0")

    fun engine(
        liveCheckEveryMs: Long = 2_000, thumbnailEveryMs: Long = 5_000, journal: Journal = this.journal, log: (String) -> Unit = {},
        onWrongFocus: (ai.promptstudio.tvclean.core.model.TvId, Component) -> Unit = { _, _ -> },
    ) = DefaultEngine(renderer, prober, journal, snapshots, plans, clock, scope.backgroundScope, profiles, liveCheckEveryMs, thumbnailEveryMs, log = log, onWrongFocus = onWrongFocus)

    fun pass(): GatePass = gate.mint(tv.tvId)

    fun plan(request: PlanRequest, planner: DefaultPlanner = this.planner): Plan {
        val snap = tv.snapshot(SnapshotReason.BASELINE)
        return when (val r = planner.plan(request, snap, facts, emptyList())) {
            is PlanResult.Ok -> r.plan
            is PlanResult.Refused -> throw AssertionError("refused: ${r.reasons}")
        }
    }

    fun setupPlan(takeover: Boolean = true, installLauncher: Boolean = true, profile: Profile = profile(), planner: DefaultPlanner = this.planner): Plan = plan(
        // The TV's Wi-Fi was not saved by its setup wizard (D3), so a wizard quirk profile switches it off.
        PlanRequest.Setup(ProfileResolution.Exact(profile), Choices(emptySet(), emptySet(), null, null, installLauncher, takeover, false, false,
            wizardWifi = WizardWifi.NOT_OWNER), artifact, Level.SIMPLE),
        planner,
    )

    // A plan from explicit ops, rendered by the real renderer, like Samples.plan; its id comes from
    // the harness's IdGen like every planner plan, because a plan runs once (R1).
    fun opsPlan(vararg ops: Op): Plan = Samples.plan(ops.toList(), tvId = tv.tvId, renderer = renderer, id = idGen.planId(), base = tv.snapshot(SnapshotReason.BASELINE).contentHash)

    suspend fun records(plan: Plan): List<JournalRecord> = journal.read(plan.id)
    suspend fun types(plan: Plan): List<String> = records(plan).map { r ->
        when (r) {
            is JournalRecord.PlanStart -> "plan"
            is JournalRecord.Begin -> "begin${r.i}"
            is JournalRecord.Done -> "done${r.i}" + if (r.resumed) "r" else ""
            is JournalRecord.Failed -> "failed${r.i}"
            is JournalRecord.Section -> "section:${r.state.name}"
            is JournalRecord.Rollback -> "rollback${r.from}"
            is JournalRecord.Paused -> "paused:${r.reason.name}@${r.at}"
            is JournalRecord.End -> "end:${r.status.name}"
        }
    }

    // advanceUntilIdle stops when only background tasks remain, and the run lives in backgroundScope,
    // so tests drive the scheduler a second at a time until the run settles (or waits for the user).
    suspend fun settle(engine: Engine, maxSeconds: Int = 2_000): RunState {
        var guard = 0
        while (true) {
            scope.runCurrent()
            val s = engine.runState(tv.tvId).value
            if (s is RunState.Finished || s is RunState.Paused || s is RunState.Idle || s is RunState.AwaitingConfirm) return s
            scope.advanceTimeBy(1_000)
            check(guard++ < maxSeconds) { "run did not settle: $s" }
        }
    }

    companion object {
        fun sha(text: String) = sha256Of(text.encodeToByteArray())
        val fallbackHome = Component(PackageName("com.android.settings"), ".FallbackHome")
    }
}

// Every coroutine test gets an explicit wall-clock limit (the virtual clock skips the delays).
val TEST_TIMEOUT = kotlin.time.Duration.parse("30s")
