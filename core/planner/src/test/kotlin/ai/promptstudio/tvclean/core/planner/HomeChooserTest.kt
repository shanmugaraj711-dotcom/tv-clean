package ai.promptstudio.tvclean.core.planner

import ai.promptstudio.tvclean.core.model.Component
import ai.promptstudio.tvclean.core.model.EnabledState
import ai.promptstudio.tvclean.core.model.HomeClaimant
import ai.promptstudio.tvclean.core.model.HomeResolution
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.PackageState
import ai.promptstudio.tvclean.core.model.Snapshot
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.plan
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.profile
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.refused
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.snapshot
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.stock
import ai.promptstudio.tvclean.core.spec.KnownLaunchers
import ai.promptstudio.tvclean.core.store.SnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// WP4 item 3 (B5): HOME answers with the system chooser (`resolve-activity` gives
// android/com.android.internal.app.ResolverActivity when two home screens tie) or with nothing at
// all. Then every HOME claimant, and the TV's stock home too, must still be protected outside the
// home screen switch: neither SwitchOff nor Leftovers may plan one, and the engine's live check
// (the same SwitchOffRule) refuses one.
class HomeChooserTest {
    private val chooser = Component(PackageName("android"), "com.android.internal.app.ResolverActivity")
    private val launcherx = KnownLaunchers.LAUNCHERX
    private val vendorHome = Component(PackageName("com.mitv.tvhome"), ".MainActivity")
    private val persons = Component(PackageName("com.spocky.projengmenu"), ".ui.home.MainActivity")
    private fun sys(label: String) = PackageState(EnabledState.ENABLED, true, 1, null, false, false, label, false)
    private val extra = mapOf(
        launcherx.pkg to sys("Google TV Home"), vendorHome.pkg to sys("PatchWall"),
        persons.pkg to PackageState(EnabledState.ENABLED, false, 1, null, false, false, "Projectivy", false),
    )

    private fun tv(resolved: Component?, claimants: List<Component>): Snapshot = snapshot(extra = extra).let { s ->
        s.copy(home = HomeResolution(resolved, claimants.mapIndexed { i, c -> HomeClaimant(c, 10 - i) })).let { it.copy(contentHash = SnapshotCodec.hash(it)) }
    }

    @Test fun everyClaimantAndTheStockHomeAreProtectedWhileHomeIsTheChooserOrNothing() {
        val claimants = listOf(stock, launcherx, vendorHome, persons)
        for (resolved in listOf(chooser, null)) {
            val s = tv(resolved, claimants)
            for (stockLaunchers in listOf(listOf(stock), emptyList())) {
                val p = profile().copy(stockLaunchers = stockLaunchers)
                for (c in claimants) {
                    assertNotNull(SwitchOffRule.reason(c.pkg, s.packages[c.pkg], p, s, stockLaunchers), "${c.flat} with HOME ${resolved?.flat}, stock $stockLaunchers")
                    assertNotNull(SwitchOffRule.reason(c.pkg, s.packages[c.pkg], null, s, emptyList()), "${c.flat} with no profile")
                }
                assertNotNull(SwitchOffRule.reason(stock.pkg, s.packages[stock.pkg], p, s, stockLaunchers), "the stock home")
            }
            // The planner (no profile source: no stock launchers known) refuses each one.
            for (c in claimants) assertTrue(refused(PlanRequest.SwitchOff(setOf(c.pkg)), snapshot = s).single().contains("cannot be switched off"), c.flat)
            // Leftovers skips nothing silently: a home screen there is a refusal, never a step.
            val r = PlannerFixtures.planner().plan(PlanRequest.Leftovers(claimants.map { it.pkg }.toSet()), s, PlannerFixtures.facts, emptyList())
            if (r is PlanResult.Ok) assertTrue(r.plan.steps.none { it.op is Op.Disable })
        }
    }

    // Only a claimant is known to be a home screen, so a known TV home screen that HOME does not
    // list (it lost the claim, or the list read short) is protected too while HOME is undecided.
    @Test fun aKnownStockHomeThatIsNotAClaimantIsProtectedWhileHomeIsUndecided() {
        for (resolved in listOf(chooser, null, Component(PackageName("com.android.tv.settings"), ".system.FallbackHome"))) {
            val s = tv(resolved, listOf(persons))
            for (known in listOf(KnownLaunchers.TVLAUNCHER, launcherx)) {
                assertNotNull(SwitchOffRule.reason(known.pkg, s.packages[known.pkg], null, s, emptyList()), "${known.flat} with HOME ${resolved?.flat}")
            }
        }
        // With a real answer the rule is as before: a known launcher HOME does not name stays a choice
        // only where the profile does not list it as stock.
        val decided = tv(persons, listOf(persons))
        assertNull(SwitchOffRule.reason(launcherx.pkg, decided.packages[launcherx.pkg], null, decided, emptyList()))
        assertNotNull(SwitchOffRule.reason(launcherx.pkg, decided.packages[launcherx.pkg], null, decided, listOf(launcherx)))
    }

    // Inside the switch the TV's own home screen still goes off as before, the chooser answering HOME.
    // WP4 item 4: the SetHome's prior (the recorded stock Home) is the stock home, never the chooser,
    // setupwraith's RecoveryActivity, a FallbackHome or nothing.
    @Test fun theSwitchStillTakesOverFromTheChooserAndRecordsTheStockHome() {
        val placeholders = listOf(chooser, Component(PackageName("com.google.android.tungsten.setupwraith"), ".RecoveryActivity"),
            Component(PackageName("com.android.tv.settings"), ".system.FallbackHome"), null)
        for (resolved in placeholders) {
            val p = plan(PlannerFixtures.setup(), snapshot = tv(resolved, listOf(stock)))
            assertTrue(Op.Disable(stock.pkg) in p.steps.map { it.op }, "${resolved?.flat}")
            assertTrue(Op.SetHome(stock) in p.sections.single().rollback, "${resolved?.flat}")
            assertEquals(stock, p.steps.single { it.op is Op.SetHome }.prior?.home, "${resolved?.flat}")
        }
        // A real home screen answering HOME is recorded as it is.
        val p = plan(PlannerFixtures.setup(), snapshot = tv(stock, listOf(stock)))
        assertEquals(stock, p.steps.single { it.op is Op.SetHome }.prior?.home)
    }
}
