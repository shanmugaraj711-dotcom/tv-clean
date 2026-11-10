package ai.promptstudio.tvclean.core.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import ai.promptstudio.tvclean.core.model.EnabledState
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.Snapshot
import ai.promptstudio.tvclean.core.planner.PlanResult
import ai.promptstudio.tvclean.core.probe.SavedWifi
import ai.promptstudio.tvclean.core.spec.BundledFactsTable
import ai.promptstudio.tvclean.core.spec.BundledProfileSource
import ai.promptstudio.tvclean.core.spec.ProfileResolution
import ai.promptstudio.tvclean.testing.fixtures.FakeConfigTransport
import ai.promptstudio.tvclean.testing.fixtures.FakeIdGen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// Issue #945 and the review of beta4: PatchWall on while Mi TV+ or its web content is off, and not by
// this app (no Disable record of ours), so the plan cannot switch it back on. Setup offers no home
// screen switch and says why (PATCHWALL_FEEDS). The TV is the Mi TV 4i of issue #1119 (brand set
// Xiaomi, Android 9, R0's claimants) with PatchWall on and the web content off.
@OptIn(ExperimentalCoroutinesApi::class)
class Issue945SetupTest {
    private val report = ReportText.parse(ReportText.fixture("tv-1119.txt"))
    private val patchWall = PackageName("com.mitv.tvhome.atv")
    private val webContent = PackageName("com.xm.webcontent")

    private fun snapshot(): Snapshot {
        val s = report.snapshot
        fun st(p: PackageName, e: EnabledState) = p to s.packages.getValue(p).copy(enabled = e)
        return s.copy(packages = s.packages + st(patchWall, EnabledState.DEFAULT) + st(webContent, EnabledState.DISABLED_USER))
    }

    private fun TestScope.found(snap: Snapshot): Pair<Harness, SetupMachine> {
        val h = Harness(facts = BundledFactsTable())
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        h.prober.facts = report.facts
        h.prober.snapshotFor = { reason -> snap.copy(reason = reason) }
        val source = BundledProfileSource()
        h.profiles.homeResolver = { f, home -> source.resolveNow(f, home = home, packages = snap.packages) }
        val planner = ai.promptstudio.tvclean.core.planner.DefaultPlanner(h.clock, FakeIdGen(), ai.promptstudio.tvclean.core.ops.ShellRenderer(), FakeConfigTransport(), "0.4.6")
        h.planner.respond = { request, s -> planner.plan(request, s, h.facts, emptyList()) }
        val m = SetupMachine(h.setupDeps(savedWifi = { SavedWifi(true, 1, setOf("android.uid.system:1000")) }), backgroundScope, SetupStart.Fresh)
        h.discovery.emit(ai.promptstudio.tvclean.core.discovery.DiscoveryEvent.Found(miTvFound))
        settle()
        m.dispatch(SetupIntent.Start)
        settle()
        h.discovery.open5555[HOST] = true
        settle(2_100)
        settle(700)
        return h to m
    }

    @Test fun aFeedOffNotByThisAppKeepsTheSwitchOutAndSaysWhy() = runTest(timeout = 60.seconds) {
        val snap = snapshot()
        assertTrue(BundledProfileSource().resolveNow(report.facts, home = snap.home, packages = snap.packages) is ProfileResolution.BrandSureSet)
        for (anyBrand in listOf(false, true)) {
            val before = ai.promptstudio.tvclean.core.planner.HomeSwitchRisk.anyBrandForTests
            ai.promptstudio.tvclean.core.planner.HomeSwitchRisk.anyBrandForTests = anyBrand
            try {
                val (h, m) = found(snap)
                assertIs<SetupState.Found>(m.state.value, h.logLines.joinToString("\n"))
                assertEquals(HomeBlock.PATCHWALL_FEEDS, m.extras.value.homeBlock, "anyBrand=$anyBrand")
                assertFalse(m.extras.value.homePossible)
                assertTrue("setup: home switch blocked PATCHWALL_FEEDS" in h.logLines, h.logLines.joinToString("\n"))
            } finally { ai.promptstudio.tvclean.core.planner.HomeSwitchRisk.anyBrandForTests = before }
        }
        // With the web content on as well it is R0's usual case again.
        releaseRule {
            val p = report.snapshot.packages
            val (_, m) = found(report.snapshot.copy(packages = p + (patchWall to p.getValue(patchWall).copy(enabled = EnabledState.DEFAULT)) +
                (webContent to p.getValue(webContent).copy(enabled = EnabledState.DEFAULT))))
            assertEquals(HomeBlock.NONE, m.extras.value.homeBlock)
        }
    }

    // Review of beta4: a VENDOR_HOME block comes from the TV's own claimants, so an earlier Setup's
    // decision keeps its line after an app update.
    @Test fun aVendorHomeDecisionKeepsItsLine() {
        assertTrue(HomeBlock.VENDOR_HOME in HomeDecision.KEEPS_ITS_LINE)
        val d = HomeDecision(false, "VENDOR_HOME", app = "0.4.6-beta4", profile = "brand set Thomson")
        assertFalse(d.earlier("0.4.6", ProfileResolution.None(emptyList())))
        assertFalse(HomeDecision(false, "VENDOR_HOME").earlier("0.4.6", null))
        assertTrue(HomeDecision(false, "OTHER_HOME").earlier("0.4.6", null), "other blocks still tell an earlier decision")
    }
}
