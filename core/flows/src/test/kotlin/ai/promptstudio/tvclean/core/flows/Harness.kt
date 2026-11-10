package ai.promptstudio.tvclean.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import ai.promptstudio.tvclean.core.discovery.DiscoverySource
import ai.promptstudio.tvclean.core.discovery.FoundTv
import ai.promptstudio.tvclean.core.model.Component
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.Profile
import ai.promptstudio.tvclean.core.model.ProfileMatch
import ai.promptstudio.tvclean.core.model.Platform
import ai.promptstudio.tvclean.core.model.RunToken
import ai.promptstudio.tvclean.core.model.TestedInfo
import ai.promptstudio.tvclean.core.planner.GuardedSection
import ai.promptstudio.tvclean.core.planner.Plan
import ai.promptstudio.tvclean.core.planner.PlanKind
import ai.promptstudio.tvclean.core.planner.PlanResult
import ai.promptstudio.tvclean.core.spec.Theme
import ai.promptstudio.tvclean.core.spec.LauncherConfigDoc
import ai.promptstudio.tvclean.testing.fixtures.FakeCredentialGate
import ai.promptstudio.tvclean.testing.fixtures.FakeDiscovery
import ai.promptstudio.tvclean.testing.fixtures.FakeEngine
import ai.promptstudio.tvclean.testing.fixtures.FakeLauncherPin
import ai.promptstudio.tvclean.testing.fixtures.FakePlanner
import ai.promptstudio.tvclean.testing.fixtures.FakeProber
import ai.promptstudio.tvclean.testing.fixtures.FakeProfileSource
import ai.promptstudio.tvclean.testing.fixtures.FakeRestoreNote
import ai.promptstudio.tvclean.testing.fixtures.FakeSessionManager
import ai.promptstudio.tvclean.testing.fixtures.FakeThemeSource
import ai.promptstudio.tvclean.testing.fixtures.FakeTvRestoreFiles
import ai.promptstudio.tvclean.testing.fixtures.FakeTvSession
import ai.promptstudio.tvclean.testing.fixtures.FakeClock
import ai.promptstudio.tvclean.testing.fixtures.MapFactsTable
import ai.promptstudio.tvclean.testing.fixtures.MemoryBlobStore
import ai.promptstudio.tvclean.testing.fixtures.MemoryDocumentStore
import ai.promptstudio.tvclean.testing.fixtures.MemoryJournal
import ai.promptstudio.tvclean.testing.fixtures.MemorySnapshotStore
import ai.promptstudio.tvclean.testing.fixtures.MemoryTvRepository
import ai.promptstudio.tvclean.testing.fixtures.Samples

// Copy that names the key and its arguments, so tests assert on which sentence a machine chose.
object KeyCopy : FlowCopy {
    override fun text(key: CopyKey, args: Map<String, Any>): String =
        if (args.isEmpty()) key.name else key.name + args.entries.sortedBy { it.key }.joinToString(",", "{", "}") { "${it.key}=${it.value}" }
    override fun date(epochMs: Long): String = "date:$epochMs"
    override fun dateTime(epochMs: Long): String = "datetime:$epochMs"
}

const val HOST = "192.0.2.10"
const val TYPED = "192.168.1.50"   // a home-network address the person types (192.0.2.x is not one)

val miTvFound = FoundTv(HOST, "Mi TV 4A", "MiTV-AXSO0", setOf(DiscoverySource.GOOGLE_CAST), port5555Open = false,
    pairingService = null, connectService = null, lastSeenMs = Samples.NOW_MS)

val adPkg = PackageName("com.miui.tv.analytics")
val promoPkg = PackageName("com.xiaomi.mitv.promo")

val miProfile = Profile(
    id = "xiaomi-mitv-axso0", match = ProfileMatch(listOf(Samples.miTvFacts().model), emptyList(), "Xiaomi", null, null, null),
    platform = Platform.ANDROID_TV, stockLaunchers = listOf(Samples.stockLauncher), liveTv = null, setupWraith = null,
    quirks = emptySet(), groups = emptyList(), neverDisable = emptySet(), suggestedDock = emptyList(),
    tested = TestedInfo("user", null, 7, rebootTested = false), sourceSha256 = Samples.zeroHash,
)

// Every fake wired the way the app wires the real things.
class Harness(val clock: FakeClock = FakeClock(Samples.NOW_MS), val facts: ai.promptstudio.tvclean.core.spec.FactsTable = MapFactsTable()) {
    val discovery = FakeDiscovery()
    val session = FakeTvSession()
    val sessions = FakeSessionManager().apply { register(HOST, session) }
    val prober = FakeProber()
    val profiles = FakeProfileSource(listOf(miProfile))
    val planner = FakePlanner()
    val engine = FakeEngine(clock)
    val credential = FakeCredentialGate(clock)
    val gate = GateKeeper(credential, clock, KeyCopy)
    val tvs = MemoryTvRepository()
    val snapshots = MemorySnapshotStore()
    val documents = MemoryDocumentStore()
    val journal = MemoryJournal()
    val ledger = RunLedger(documents, journal)
    val blobs = MemoryBlobStore()
    val runner = PlanRunner(planner, engine, gate, ledger, facts, snapshots, prober)
    val restorePoint = RestorePointWriter(FakeTvRestoreFiles(), blobs, ledger, runner)
    val foreground = AppForeground(true)
    val sessionEnd = SessionEnd(sessions, discovery, tvs, runner, foreground)
    val access = TvAccess(sessions, discovery, tvs, clock)
    val themes = FakeThemeSource(listOf(Theme("aurora", "Aurora", "Navy with a wallpaper", LauncherConfigDoc(), null)))

    var setupPlan: Plan = plan(listOf(Op.Disable(adPkg), Op.Disable(promoPkg)))

    init {
        planner.respond = { request, _ ->
            when (request) {
                is ai.promptstudio.tvclean.core.planner.PlanRequest.Setup -> PlanResult.Ok(setupPlan)
                else -> kindOf(request).let { k -> PlanResult.Ok(plan(emptyList(), kind = k, id = "01FAKEKIND" + k.ordinal.toString().padStart(16, '0'))) }
            }
        }
    }

    fun plan(ops: List<Op>, kind: PlanKind = PlanKind.SETUP, takeover: Boolean = false, id: String = "01FAKEPLAN0000000000000001"): Plan {
        val token = RunToken("00000000000000aa")
        val all = if (takeover) ops + listOf(Op.ArmHomeWatchdog(Samples.stockLauncher, 120, token), Op.SetHome(Launcher.home), Op.VerifyHome(Launcher.home)) else ops
        val sections = if (takeover) listOf(GuardedSection("home_takeover", ops.size, all.size - 1, token, 90_000, Launcher.home, setOf(Samples.stockLauncher.pkg), emptyList())) else emptyList()
        return Samples.plan(all, kind = kind, id = ai.promptstudio.tvclean.core.model.PlanId(id), sections = sections)
    }

    // What the machines wrote to the diagnostics log, in order.
    val logLines = java.util.concurrent.CopyOnWriteArrayList<String>()
    // The app's diagnostics log as Manage sees it; Delete everything clears it.
    var diagnosticsClears = 0
    val diagnostics = object : Diagnostics {
        override suspend fun redactedText() = "redacted"
        override suspend fun clear() { diagnosticsClears++ }
    }
    // The launchable apps the TV reports (null: the TV could not say).
    var launchable: Set<PackageName>? = null

    fun setupDeps(dock: DockPicker = DockPicker { _, _, _, _, _ -> listOf(PackageName("com.netflix.ninja")) }, curtainPin: ai.promptstudio.tvclean.core.trust.CurtainPin? = null,
                  savedWifi: ai.promptstudio.tvclean.core.probe.SavedWifiOwners? = null, store: StoreRules = StoreRules.FULL,
                  candidates: CandidateLists? = null, diagnostics: Diagnostics = Diagnostics { "diagnostics" },
                  about: AppAbout? = null, connectDetail: () -> String? = { null },
                  themes: ai.promptstudio.tvclean.core.spec.ThemeSource = this.themes,
                  sessions: ai.promptstudio.tvclean.core.session.SessionManager = this.sessions,
                  wrongFocus: (ai.promptstudio.tvclean.core.model.TvId) -> ai.promptstudio.tvclean.core.model.Component? = { null },
                  homeHelper: Boolean = false,
                  pairing: PairingRunner? = null) = SetupDeps(
        discovery, sessions, prober, profiles, facts, runner, engine, gate, tvs, snapshots, ledger,
        FakeLauncherPin(Samples.launcherArtifact()), themes, restorePoint, FakeRestoreNote(), sessionEnd, access, clock, KeyCopy,
        blobs = blobs, dock = dock, screenLock = { true }, diagnostics = diagnostics,
        launchable = ai.promptstudio.tvclean.core.probe.LaunchableApps { launchable ?: throw ai.promptstudio.tvclean.core.probe.ProbeException(ai.promptstudio.tvclean.core.probe.ProbeStage.LAUNCHER, "no answer") },
        log = FlowLog { logLines += it },
        homePrefs = HomePrefs(documents),
        curtainPin = curtainPin,
        savedWifi = savedWifi,
        store = store,
        homeDecisions = HomeDecisions(documents),
        candidates = candidates,
        about = about,
        connectDetail = connectDetail,
        documents = documents,
        wrongFocus = wrongFocus,
        homeHelper = homeHelper,
        pairing = pairing,
    )

    fun setup(scope: CoroutineScope, start: SetupStart = SetupStart.Fresh, store: StoreRules = StoreRules.FULL) = SetupMachine(setupDeps(store = store), scope, start)

    val registry = ai.promptstudio.tvclean.testing.fixtures.FakeRegistry()
    val fetcher = ai.promptstudio.tvclean.testing.fixtures.FakeArtifactFetcher(blobs)
    var inspector: ai.promptstudio.tvclean.core.trust.ApkInspector = ai.promptstudio.tvclean.testing.fixtures.FakeApkInspector()
    val bundleWriter = ai.promptstudio.tvclean.testing.fixtures.FakeRestoreBundleWriter()
    val configTransport = ai.promptstudio.tvclean.testing.fixtures.FakeConfigTransport()
    var cardAnswer: CardAnswer? = null
    val keyInfo = FakeKeyInfo()
    class FakeKeyInfo : KeyInfo {
        var resets = 0
        override val fingerprint: String get() = "AB:CD:7F:2A:$resets"
        override val backing: String = "TEE"
        override suspend fun reset() { resets++ }
    }

    fun manageDeps(fetcherSources: ai.promptstudio.tvclean.core.registry.ArtifactFetcher = fetcher, encoder: ConfigEncoder = ConfigEncoder { "{}".encodeToByteArray() },
                   launcherPin: ai.promptstudio.tvclean.core.trust.LauncherPin = FakeLauncherPin(Samples.launcherArtifact()),
                   themes: ai.promptstudio.tvclean.core.spec.ThemeSource = this.themes, store: StoreRules = StoreRules.FULL,
                   updates: UpdateChecker = UpdateChecker { "0.2.0" }, profiles: ai.promptstudio.tvclean.core.spec.ProfileSource = this.profiles,
                   about: AppAbout = AppAbout("0.2.0", "full", "", ""), homeHelper: Boolean = false) = ManageDeps(
        tvs = tvs, sessions = sessions, discovery = discovery, access = access, gate = gate, runner = runner, engine = engine,
        ledger = ledger, prober = prober, profiles = profiles, facts = facts, snapshots = snapshots, documents = documents,
        blobs = blobs, themes = themes, launcherPin = launcherPin, configTransport = configTransport,
        restorePoint = restorePoint, bundleWriter = bundleWriter, registry = registry, fetcher = fetcherSources, inspector = inspector,
        cardDetector = CardAnswerDetector { _, _ -> cardAnswer }, sessionEnd = sessionEnd, homePrefs = HomePrefs(documents),
        appPrefs = AppPrefs(documents), keyInfo = keyInfo, updates = updates, diagnostics = diagnostics,
        clock = clock, copy = KeyCopy, encoder = encoder, store = store, appVersion = about.version, about = about,
        log = FlowLog { logLines += it },
        homeHelper = homeHelper,
    )

    // A TV this phone already set up, connected, with a stored snapshot.
    suspend fun knownTv(): ai.promptstudio.tvclean.core.model.Tv {
        val t = Samples.tv()
        tvs.upsert(t)
        snapshots.save(Samples.miTvSnapshot())
        sessions.open(ai.promptstudio.tvclean.core.model.Endpoint.PlainTcp(HOST), t.id) {}
        return t
    }

    private fun kindOf(r: ai.promptstudio.tvclean.core.planner.PlanRequest): PlanKind = when (r) {
        is ai.promptstudio.tvclean.core.planner.PlanRequest.Setup -> PlanKind.SETUP
        is ai.promptstudio.tvclean.core.planner.PlanRequest.PutBack -> PlanKind.PUT_BACK
        is ai.promptstudio.tvclean.core.planner.PlanRequest.SwitchOff -> PlanKind.SWITCH_OFF
        is ai.promptstudio.tvclean.core.planner.PlanRequest.Leftovers -> PlanKind.LEFTOVERS
        is ai.promptstudio.tvclean.core.planner.PlanRequest.ApplyTheme -> PlanKind.THEME
        is ai.promptstudio.tvclean.core.planner.PlanRequest.AddModule -> PlanKind.ADD_MODULE
        is ai.promptstudio.tvclean.core.planner.PlanRequest.RemoveModule -> PlanKind.REMOVE_MODULE
        is ai.promptstudio.tvclean.core.planner.PlanRequest.UseStockHome -> PlanKind.USE_STOCK_HOME
        is ai.promptstudio.tvclean.core.planner.PlanRequest.UndoEverything -> PlanKind.UNDO_EVERYTHING
        ai.promptstudio.tvclean.core.planner.PlanRequest.BeforeFactoryReset -> PlanKind.BEFORE_FACTORY_RESET
        ai.promptstudio.tvclean.core.planner.PlanRequest.DebugOff -> PlanKind.DEBUG_OFF
        is ai.promptstudio.tvclean.core.planner.PlanRequest.WriteRestorePoint -> PlanKind.WRITE_RESTORE_POINT
        ai.promptstudio.tvclean.core.planner.PlanRequest.Reboot -> PlanKind.REBOOT
        is ai.promptstudio.tvclean.core.planner.PlanRequest.UpdateLauncher -> PlanKind.UPDATE_LAUNCHER
        ai.promptstudio.tvclean.core.planner.PlanRequest.HomeHelperOff -> PlanKind.HOME_HELPER_OFF
    }
}

// Lets launched work and short delays run without draining the endless poll and tick loops.
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.settle(ms: Long = 50) { runCurrent(); advanceTimeBy(ms); runCurrent() }

val stockComponent: Component get() = Samples.stockLauncher

// Everything a Turbine collected so far, without waiting: the machines run endless poll and tick
// loops in the background scope, so a waiting awaitItem would spin the test scheduler.
suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.drain(): List<T> =
    cancelAndConsumeRemainingEvents().filterIsInstance<app.cash.turbine.Event.Item<T>>().map { it.value }

// 0.4.6: a test written for the release rule (R0 on Xiaomi only) runs with it in a test build too
// (-Pcyt.wizardStaysAnyBrand=true, which widens R0 to every brand); PlannerFixtures.releaseRule.
inline fun releaseRule(block: () -> Unit) {
    val before = ai.promptstudio.tvclean.core.planner.HomeSwitchRisk.anyBrandForTests
    ai.promptstudio.tvclean.core.planner.HomeSwitchRisk.anyBrandForTests = false
    try { block() } finally { ai.promptstudio.tvclean.core.planner.HomeSwitchRisk.anyBrandForTests = before }
}
