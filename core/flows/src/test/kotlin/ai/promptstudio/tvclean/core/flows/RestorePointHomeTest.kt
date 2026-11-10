package ai.promptstudio.tvclean.core.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import ai.promptstudio.tvclean.core.backup.ChangeSummary
import ai.promptstudio.tvclean.core.discovery.DiscoveryEvent
import ai.promptstudio.tvclean.core.model.ChangeRecord
import ai.promptstudio.tvclean.core.model.Component
import ai.promptstudio.tvclean.core.model.HomeClaimant
import ai.promptstudio.tvclean.core.model.HomeResolution
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.planner.DefaultPlanner
import ai.promptstudio.tvclean.core.planner.StockHome
import ai.promptstudio.tvclean.core.store.SnapshotCodec
import ai.promptstudio.tvclean.testing.fixtures.FakeConfigTransport
import ai.promptstudio.tvclean.testing.fixtures.FakeIdGen
import ai.promptstudio.tvclean.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// WP4 item 4 (B5): the stock Home that Setup records (the prior of the switch's SetHome, which the
// journal, the restore point's homeBefore, step R from the journal and Undo everything all read) is
// never the system chooser, setupwraith's RecoveryActivity or a FallbackHome, whatever HOME answered
// when the TV was read. It is the TV's own home screen the switch hands back to.
@OptIn(ExperimentalCoroutinesApi::class)
class RestorePointHomeTest {
    private val stock = Samples.stockLauncher
    private val chooser = Component(PackageName("android"), "com.android.internal.app.ResolverActivity")
    private val recovery = Component(PackageName("com.google.android.tungsten.setupwraith"), ".RecoveryActivity")
    private val fallback = Component(PackageName("com.android.tv.settings"), ".system.FallbackHome")
    private val vendorFallback = Component(PackageName("com.tcl.keycustomfunctionservice"), ".FallbackHome")

    private suspend fun TestScope.toFound(h: Harness, m: SetupMachine) {
        h.discovery.emit(DiscoveryEvent.Found(miTvFound)); settle()
        m.dispatch(SetupIntent.Start); settle()
        h.discovery.open5555[HOST] = true
        settle(2_100); settle(700)
    }

    @Test fun theRecordedStockHomeIsNeverAPlaceholder() = runTest(timeout = 30.seconds) {
        for (resolved in listOf(chooser, recovery, fallback, vendorFallback, null)) {
            val h = Harness()
            h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
            h.prober.snapshotFor = { r ->
                Samples.miTvSnapshot(reason = r).let { s ->
                    // The stock home still claims HOME; the answer to resolve-activity is the placeholder.
                    val claimants = listOfNotNull(HomeClaimant(stock, 1), resolved?.let { HomeClaimant(it, 0) })
                    s.copy(home = HomeResolution(resolved, claimants))
                }.let { it.copy(contentHash = SnapshotCodec.hash(it)) }
            }
            val planner = DefaultPlanner(h.clock, FakeIdGen(), ai.promptstudio.tvclean.core.ops.ShellRenderer(), FakeConfigTransport(), "0.4.5")
            h.planner.respond = { request, snap -> planner.plan(request, snap, h.facts, emptyList()) }
            val m = h.setup(backgroundScope)
            toFound(h, m)
            val plan = assertIs<SetupState.Found>(m.state.value, "${resolved?.flat}").plan
            val changes = plan.steps.filter { it.op !is Op.Read }.map { ChangeRecord(plan.id, it.index, it.op, it.prior, Samples.NOW_MS + it.index) }
            if (resolved == recovery) {
                // The wizard answering Home: Setup skips the switch up front (HOME_SETUPWRAITH), so no
                // home screen is recorded at all. The planner's own record is checked below.
                assertTrue(plan.steps.none { it.op is Op.SetHome }, "the wizard holds Home: no switch")
                assertEquals(null, ChangeSummary.of(changes).homeBefore)
                continue
            }
            if (resolved == vendorFallback) {
                // beta4 (issue #825): a vendor service's FallbackHome holds the switch back on every path.
                assertEquals(HomeBlock.VENDOR_HOME, m.extras.value.homeBlock)
                assertTrue(plan.steps.none { it.op is Op.SetHome }, "a vendor service's FallbackHome: no switch")
                continue
            }
            val setHome = assertNotNull(plan.steps.firstOrNull { it.op is Op.SetHome }, "${resolved?.flat}: the switch is planned")
            val recorded = assertNotNull(setHome.prior?.home, "${resolved?.flat}: a home is recorded")
            assertEquals(stock, recorded, "${resolved?.flat}")
            assertFalse(StockHome.isPlaceholder(recorded))
            // Step R in the plan hands HOME to the same stock home.
            assertTrue(Op.SetHome(stock) in plan.sections.single().rollback, "${resolved?.flat}")
            // The restore point reads the same record.
            assertEquals(stock, ChangeSummary.of(changes).homeBefore, "${resolved?.flat}: restore-point.json homeBefore")
        }
    }

    // The rule itself: what counts as a placeholder, and what a real home screen records as.
    @Test fun placeholdersAreTheChooserFallbackHomesAndTheWizard() {
        for (c in listOf(chooser, recovery, fallback, vendorFallback, Component(PackageName("com.android.provision"), ".DefaultActivity"),
            Component(PackageName("com.xiaomi.mitv.settings"), ".entry.FallbackHome"))) assertTrue(StockHome.isPlaceholder(c), c.flat)
        for (c in listOf(stock, Component(PackageName("com.google.android.apps.tv.launcherx"), ".home.HomeActivity"), Component(PackageName("com.spocky.projengmenu"), ".ui.home.MainActivity")))
            assertFalse(StockHome.isPlaceholder(c), c.flat)
        val other = Component(PackageName("com.spocky.projengmenu"), ".ui.home.MainActivity")
        val s = Samples.miTvSnapshot()
        assertEquals(other, StockHome.recorded(s.copy(home = HomeResolution(other, s.home.claimants)), stock), "a real home screen is recorded as it is")
        assertEquals(stock, StockHome.recorded(s.copy(home = HomeResolution(chooser, s.home.claimants)), stock))
        assertEquals(stock, StockHome.recorded(s.copy(home = HomeResolution(null, s.home.claimants)), stock))
    }
}
