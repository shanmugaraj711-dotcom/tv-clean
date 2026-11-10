package ai.promptstudio.tvclean.core.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import ai.promptstudio.tvclean.core.model.EnabledState
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.planner.PlanRequest
import ai.promptstudio.tvclean.core.planner.PlanResult
import ai.promptstudio.tvclean.core.probe.SavedWifi
import ai.promptstudio.tvclean.core.spec.BundledFactsTable
import ai.promptstudio.tvclean.core.spec.BundledProfileSource
import ai.promptstudio.tvclean.testing.fixtures.FakeConfigTransport
import ai.promptstudio.tvclean.testing.fixtures.FakeIdGen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// Issue #1119 (kjntl007, Xiaomi MiTV4I magnolia, Android 9): 0.4.6-beta2 blocked the switch
// WIZARD_STAYS_ON because a home screen app the person installed claims Home (priority 0) next to
// tvlauncher (2) and FallbackHome. The packages are the TV report of the issue (0.4.0), the Home
// and what this app switched off since are the newest problem report's (0.4.6-beta2). Setup now
// plans the switch with their app left installed and on. The Mi TV 4I has had an exact profile
// since 2026-11-10, so the R0 path no longer applies to it. An app the person installed that
// answers Home keeps their choice on every path (the guard in SetupMachine.homeBlock).
@OptIn(ExperimentalCoroutinesApi::class)
class Issue1119SetupTest {
    private fun snapshot(): Pair<ReportText.Parsed, ai.promptstudio.tvclean.core.model.Snapshot> {
        val tvReport = ReportText.parse(ReportText.fixture("tv-1119.txt"))
        val problem = ReportText.parse(ReportText.fixture("problem-1119.txt"))
        val off = problem.switchedOff.associateWith { p -> tvReport.snapshot.packages.getValue(p).copy(enabled = EnabledState.DISABLED_USER) }
        val theirs = problem.personHomes.associateWith { problem.snapshot.packages.getValue(it) }
        val packages = tvReport.snapshot.packages + off + theirs
        val snap = tvReport.snapshot.copy(packages = packages, home = problem.snapshot.home, facts = problem.facts)
        return problem to snap
    }

    @Test fun theReportIsReadAsTheAppMadeIt() {
        val (problem, snap) = snapshot()
        assertEquals("candidate xiaomi-mitv4i-magnolia", problem.profileLine)
        assertEquals("blocked WIZARD_STAYS_ON", problem.homeLine)
        assertEquals(listOf("com.google.android.tvlauncher" to 2, "person.home1" to 0, "com.android.tv.settings" to -1000),
            snap.home.claimants.map { it.component.pkg.value to it.priority })
        assertEquals(false, snap.packages.getValue(PackageName("person.home1")).system)
        assertEquals(EnabledState.DEFAULT, snap.packages.getValue(PackageName("com.google.android.tungsten.setupwraith")).enabled)
    }

    private fun kotlinx.coroutines.test.TestScope.found(problem: ReportText.Parsed, snap: ai.promptstudio.tvclean.core.model.Snapshot, wifi: SavedWifi,
                                                      onPlan: (ai.promptstudio.tvclean.core.planner.Plan) -> Unit = {}): Pair<Harness, SetupMachine> {
        val h = Harness(facts = BundledFactsTable())
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        h.prober.facts = problem.facts
        h.prober.snapshotFor = { reason -> snap.copy(reason = reason) }
        val source = BundledProfileSource()
        h.profiles.homeResolver = { f, home -> source.resolveNow(f, home = home, packages = snap.packages) }
        val planner = ai.promptstudio.tvclean.core.planner.DefaultPlanner(h.clock, FakeIdGen(), ai.promptstudio.tvclean.core.ops.ShellRenderer(), FakeConfigTransport(), "0.4.6")
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

    // The same TV with that app listed as a system app (as a vendor home ships): the exact profile
    // plans the switch, and the set-home tie at priority 0 is decided by the app's own SetHome,
    // with the system app left installed and on; VerifyHome rolls back if their app keeps Home.
    // (Before the profile, R0 could not take a system app, so the reason told was the other home
    // screen app.)
    @Test fun systemHomeAppIsLeftAloneByThePlannedSwitch() = runTest(timeout = 60.seconds) {
        val (problem, base) = snapshot()
        val theirs = PackageName("person.home1")
        val snap = base.copy(packages = base.packages + (theirs to base.packages.getValue(theirs).copy(system = true)))
        releaseRule {
            for (wifi in listOf(SavedWifi(true, 1, setOf("android.uid.system:1000")), SavedWifi(true, 1, setOf("com.google.android.tungsten.setupwraith:10010")))) {
                var plan: ai.promptstudio.tvclean.core.planner.Plan? = null
                val (h, m) = found(problem, snap, wifi) { plan = it }
                assertEquals(HomeBlock.NONE, m.extras.value.homeBlock, "${wifi.owners}")
                assertEquals(HomeDecision(true, profile = "exact xiaomi-mitv4i-magnolia"), HomeDecisions(h.documents).load(snap.tvId))
                assertTrue("setup: home switch planned" in h.logLines, h.logLines.joinToString("\n"))
                val ops = plan!!.steps.map { it.op }
                assertTrue(ops.none { (it is Op.Disable && it.pkg == theirs) || (it is Op.Uninstall && it.pkg == theirs) }, "their app stays")
                assertTrue(ops.any { it is Op.SetHome && it.component.pkg == Launcher.pkg } && ops.any { it is Op.VerifyHome && it.expected.pkg == Launcher.pkg })
                assertTrue(theirs in plan!!.sections.single().rollbackIfFocus)
            }
        }
    }

    // Review of beta3: Home already answering to their app is their choice. It stays: no switch, and
    // the reason told is the other home screen app.
    @Test fun homeAnsweringToTheirAppKeepsTheirChoice() = runTest(timeout = 60.seconds) {
        val (problem, base) = snapshot()
        val theirs = ai.promptstudio.tvclean.core.model.Component(PackageName("person.home1"), ".Main")
        val snap = base.copy(home = base.home.copy(resolved = theirs))
        releaseRule {
            var plan: ai.promptstudio.tvclean.core.planner.Plan? = null
            val (h, m) = found(problem, snap, SavedWifi(true, 1, setOf("android.uid.system:1000"))) { plan = it }
            assertEquals(HomeBlock.OTHER_HOME, m.extras.value.homeBlock)
            assertTrue("setup: home switch blocked OTHER_HOME" in h.logLines, h.logLines.joinToString("\n"))
            assertTrue(plan!!.sections.isEmpty() && plan!!.steps.none { it.op is Op.SetHome || (it.op is Op.Disable && (it.op as Op.Disable).pkg == theirs.pkg) })
        }
    }

    @Test fun setupPlansTheSwitchAndLeavesTheirHomeAppAlone() = runTest(timeout = 60.seconds) {
        val (problem, snap) = snapshot()
        releaseRule {
            for (wifi in listOf(SavedWifi(true, 1, setOf("android.uid.system:1000")), SavedWifi(true, 1, setOf("com.google.android.tungsten.setupwraith:10010")))) {
                val h = Harness(facts = BundledFactsTable())
                h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
                h.prober.facts = problem.facts
                h.prober.snapshotFor = { reason -> snap.copy(reason = reason) }
                val source = BundledProfileSource()
                h.profiles.homeResolver = { f, home -> source.resolveNow(f, home = home, packages = snap.packages) }
                val planner = ai.promptstudio.tvclean.core.planner.DefaultPlanner(h.clock, FakeIdGen(), ai.promptstudio.tvclean.core.ops.ShellRenderer(), FakeConfigTransport(), "0.4.6")
                var plan: ai.promptstudio.tvclean.core.planner.Plan? = null
                h.planner.respond = { request, s -> planner.plan(request, s, h.facts, emptyList()).also { if (request is PlanRequest.Setup && it is PlanResult.Ok) plan = it.plan } }
                val m = SetupMachine(h.setupDeps(savedWifi = { wifi }), backgroundScope, SetupStart.Fresh)
                h.discovery.emit(ai.promptstudio.tvclean.core.discovery.DiscoveryEvent.Found(miTvFound))
                settle()
                m.dispatch(SetupIntent.Start)
                settle()
                h.discovery.open5555[HOST] = true
                settle(2_100)
                settle(700)
                assertIs<SetupState.Found>(m.state.value, h.logLines.joinToString("\n"))
                assertEquals(HomeBlock.NONE, m.extras.value.homeBlock, "${wifi.owners}")
                val ops = plan!!.steps.map { it.op }
                val theirs = PackageName("person.home1")
                assertTrue(ops.none { (it is Op.Disable && it.pkg == theirs) || (it is Op.Uninstall && it.pkg == theirs) }, "their app stays")
                assertTrue(ops.any { it is Op.SetHome && it.component.pkg == Launcher.pkg } && ops.any { it is Op.VerifyHome && it.expected.pkg == Launcher.pkg })
                assertTrue(theirs in plan!!.sections.single().rollbackIfFocus)
            }
        }
    }
}
