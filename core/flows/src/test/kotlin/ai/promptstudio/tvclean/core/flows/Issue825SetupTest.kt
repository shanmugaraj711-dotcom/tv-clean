package ai.promptstudio.tvclean.core.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.Snapshot
import ai.promptstudio.tvclean.core.planner.HomeSwitchRisk
import ai.promptstudio.tvclean.core.planner.Plan
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

// Issue #825, the incident of 0.4.6-beta2: a Thomson SPPL 2K (sauve, Android 11, Realtek) on the
// Thomson brand set took R0 in the test build (any brand) and failed to start after a few restarts.
// Its claimants are tvlauncher (2), Realtek's key service FallbackHome (-999) and Settings'
// FallbackHome (-1000); the packages are the TV report of the issue. beta4: Setup offers no switch
// there in either build, and both say why (VENDOR_HOME).
@OptIn(ExperimentalCoroutinesApi::class)
class Issue825SetupTest {
    private val report = ReportText.parse(ReportText.fixture("tv-825.txt"))
    private val notOwner = SavedWifi(true, 1, setOf("android.uid.system:1000"))

    @Test fun theReportIsReadAsTheAppMadeIt() {
        assertEquals("brand set Thomson", report.profileLine)
        assertEquals(listOf("com.google.android.tvlauncher/.MainActivity" to 2, "com.realtek.keyintercept/.fallbackhome.FallbackHome" to -999,
            "com.android.tv.settings/.system.FallbackHome" to -1000), report.snapshot.home.claimants.map { it.component.flat to it.priority })
        assertEquals(30, report.facts.sdk)
    }

    private fun TestScope.found(snap: Snapshot, wifi: SavedWifi, onPlan: (Plan) -> Unit): Pair<Harness, SetupMachine> {
        val h = Harness(facts = BundledFactsTable())
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        h.prober.facts = report.facts
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

    @Test fun setupOffersNoSwitchInEitherBuild() = runTest(timeout = 60.seconds) {
        for ((anyBrand, block) in listOf(true to HomeBlock.VENDOR_HOME, false to HomeBlock.VENDOR_HOME)) {
            val before = HomeSwitchRisk.anyBrandForTests
            HomeSwitchRisk.anyBrandForTests = anyBrand
            try {
                var plan: Plan? = null
                val (h, m) = found(report.snapshot, notOwner) { plan = it }
                assertIs<SetupState.Found>(m.state.value, h.logLines.joinToString("\n"))
                assertEquals(block, m.extras.value.homeBlock, "anyBrand=$anyBrand")
                assertTrue(!m.extras.value.homePossible, "anyBrand=$anyBrand")
                assertTrue("setup: home switch blocked ${block.name}" in h.logLines, h.logLines.joinToString("\n"))
                val ops = plan!!.steps.map { it.op }
                assertTrue(plan!!.sections.isEmpty() && ops.none { it is Op.SetHome || it is Op.ArmHomeWatchdog }, "anyBrand=$anyBrand: $ops")
                assertTrue(ops.none { it is Op.Disable && it.pkg.value == "com.google.android.tvlauncher" }, "tvlauncher stays on")
            } finally { HomeSwitchRisk.anyBrandForTests = before }
        }
    }
}
