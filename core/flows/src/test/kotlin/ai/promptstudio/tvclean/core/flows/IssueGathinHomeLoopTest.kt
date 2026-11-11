package ai.promptstudio.tvclean.core.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import ai.promptstudio.tvclean.core.model.Component
import ai.promptstudio.tvclean.core.model.EnabledState
import ai.promptstudio.tvclean.core.model.HomeClaimant
import ai.promptstudio.tvclean.core.model.HomeResolution
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.PackageState
import ai.promptstudio.tvclean.core.model.Sha256
import ai.promptstudio.tvclean.core.planner.PlanRequest
import ai.promptstudio.tvclean.core.planner.PlanResult
import ai.promptstudio.tvclean.core.probe.SavedWifi
import ai.promptstudio.tvclean.core.spec.BundledFactsTable
import ai.promptstudio.tvclean.core.spec.BundledProfileSource
import ai.promptstudio.tvclean.testing.fixtures.FakeConfigTransport
import ai.promptstudio.tvclean.testing.fixtures.FakeIdGen
import ai.promptstudio.tvclean.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// The friend's Mi TV 4I (magnolia, Android 9) after the 0.4.8 runs: our bundled launcher
// com.gathin.tvhome is installed and Home already answers to it. That is the state the
// "another home screen app, Gathin Home" stop card looped on in 0.4.8 (the rebrand had renamed
// the launcher package constants to ai.promptstudio.tvclean.launcher while the bundled APK
// stayed com.gathin.tvhome, so every check named our own launcher as someone else's).
// In the fixed build the same TV must plan with no OTHER_HOME block: Home answering to our
// launcher with our key is "already on", and with a key the probe could not read it stays ours.
// A launcher under our name with ANOTHER key is not ours: HOME_CHANGED, never OTHER_HOME.
@OptIn(ExperimentalCoroutinesApi::class)
class IssueGathinHomeLoopTest {
    private fun snapshot(): Pair<ReportText.Parsed, ai.promptstudio.tvclean.core.model.Snapshot> {
        val tvReport = ReportText.parse(ReportText.fixture("tv-1119.txt"))
        val problem = ReportText.parse(ReportText.fixture("problem-1119.txt"))
        val off = problem.switchedOff.associateWith { p -> tvReport.snapshot.packages.getValue(p).copy(enabled = EnabledState.DISABLED_USER) }
        val theirs = problem.personHomes.associateWith { problem.snapshot.packages.getValue(it) }
        val packages = tvReport.snapshot.packages + off + theirs
        val snap = tvReport.snapshot.copy(packages = packages, home = problem.snapshot.home, facts = problem.facts)
        return problem to snap
    }

    // The friend's TV now: Home answers to our launcher, tvlauncher still claims it at 2 and
    // Settings' FallbackHome at -1000, as the #1119 report's claimants. `signer`: the installed
    // launcher's key (null: the probe could not read it; another hash: installed another way).
    private fun ourHomeSnapshot(signer: Sha256?): Pair<ReportText.Parsed, ai.promptstudio.tvclean.core.model.Snapshot> {
        val (problem, base) = snapshot()
        val ours = PackageName("com.gathin.tvhome")
        val gathin = PackageState(EnabledState.DEFAULT, system = false, versionCode = 9, signer = signer,
            persistent = true, tvInput = false, label = "Gathin Home", installedByUs = false)
        val home = HomeResolution(
            resolved = Component(ours, ".MainActivity"),
            claimants = listOf(
                HomeClaimant(Component(PackageName("com.google.android.tvlauncher"), ".MainActivity"), 2),
                HomeClaimant(Component(ours, ".MainActivity"), 0),
                HomeClaimant(Component(PackageName("com.android.tv.settings"), ".system.FallbackHome"), -1000),
            ),
        )
        return problem to base.copy(packages = base.packages + (ours to gathin), home = home)
    }

    private fun kotlinx.coroutines.test.TestScope.found(problem: ReportText.Parsed, snap: ai.promptstudio.tvclean.core.model.Snapshot, wifi: SavedWifi,
                                                      onPlan: (ai.promptstudio.tvclean.core.planner.Plan) -> Unit = {}): Pair<Harness, SetupMachine> {
        val h = Harness(facts = BundledFactsTable())
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        h.prober.facts = problem.facts
        h.prober.snapshotFor = { reason -> snap.copy(reason = reason) }
        val source = BundledProfileSource()
        h.profiles.homeResolver = { f, home -> source.resolveNow(f, home = home, packages = snap.packages) }
        val planner = ai.promptstudio.tvclean.core.planner.DefaultPlanner(h.clock, FakeIdGen(), ai.promptstudio.tvclean.core.ops.ShellRenderer(), FakeConfigTransport(), "0.5.0")
        h.planner.respond = { request, s -> planner.plan(request, s, h.facts, emptyList()).also { if (request is PlanRequest.Setup && it is PlanResult.Ok) onPlan(it.plan) } }
        val m = SetupMachine(h.setupDeps(savedWifi = { wifi }), backgroundScope, SetupStart.Fresh)
        h.discovery.emit(ai.promptstudio.tvclean.core.discovery.DiscoveryEvent.Found(miTvFound))
        settle()
        m.dispatch(SetupIntent.Start)
        settle()
        h.discovery.open5555[HOST] = true
        settle(2_100)
        settle(700)
        return h to m
    }

    @Test fun homeAnsweringToOurLauncherIsAlreadyOn() = runTest(timeout = 60.seconds) {
        releaseRule {
            for (signer in listOf<Sha256?>(Samples.zeroHash, null)) {
                val (problem, snap) = ourHomeSnapshot(signer)
                var plan: ai.promptstudio.tvclean.core.planner.Plan? = null
                val (h, m) = found(problem, snap, SavedWifi(true, 1, setOf("android.uid.system:1000"))) { plan = it }
                assertEquals(HomeBlock.NONE, m.extras.value.homeBlock, "signer=$signer")
                assertEquals(HomeDecision(false, alreadyOn = true, profile = "exact xiaomi-mitv4i-magnolia"),
                    HomeDecisions(h.documents).load(snap.tvId), "signer=$signer")
                assertTrue("setup: home switch not needed (the new home screen is on)" in h.logLines, h.logLines.joinToString("\n"))
                assertTrue(plan!!.sections.isEmpty(), "already ours: no switch section, signer=$signer")
                assertTrue(plan!!.steps.none { it.op is Op.SetHome }, "already ours: no SetHome, signer=$signer")
            }
        }
    }

    @Test fun homeAnsweringToOurLauncherWithAnotherKeyIsHomeChanged() = runTest(timeout = 60.seconds) {
        releaseRule {
            val (problem, snap) = ourHomeSnapshot(Sha256("1".repeat(64)))
            var plan: ai.promptstudio.tvclean.core.planner.Plan? = null
            val (h, m) = found(problem, snap, SavedWifi(true, 1, setOf("android.uid.system:1000"))) { plan = it }
            assertEquals(HomeBlock.HOME_CHANGED, m.extras.value.homeBlock)
            assertEquals(HomeDecision(false, block = "HOME_CHANGED", profile = "exact xiaomi-mitv4i-magnolia"),
                HomeDecisions(h.documents).load(snap.tvId))
            assertTrue("setup: home switch blocked HOME_CHANGED" in h.logLines, h.logLines.joinToString("\n"))
            assertTrue(plan!!.sections.isEmpty() && plan!!.steps.none { it.op is Op.SetHome })
        }
    }
}
