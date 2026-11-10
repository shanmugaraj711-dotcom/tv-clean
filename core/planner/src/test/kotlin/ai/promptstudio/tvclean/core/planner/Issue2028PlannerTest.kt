package ai.promptstudio.tvclean.core.planner

import ai.promptstudio.tvclean.core.model.Component
import ai.promptstudio.tvclean.core.model.HomeClaimant
import ai.promptstudio.tvclean.core.model.HomeResolution
import ai.promptstudio.tvclean.core.model.Level
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.Snapshot
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.artifact
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.choices
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.otherMaker
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.plan
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.profile
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.refused
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.releaseRule
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.setup
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.snapshot
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.wizard
import ai.promptstudio.tvclean.core.spec.BundledProfileSource
import ai.promptstudio.tvclean.core.spec.KnownLaunchers
import ai.promptstudio.tvclean.core.spec.ProfileResolution
import ai.promptstudio.tvclean.core.store.SnapshotCodec
import ai.promptstudio.tvclean.testing.fixtures.Issue2028
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Issue #2028 (a Jio JMSB200A, no list): Home answered to the operator's launcher at priority 2 while
// tvlauncher claimed it at 0. 0.4.6.1 planned the switch with tvlauncher as the stock home, switched it
// off, and Android kept Home on the operator's launcher, above ours. The switch is now not planned on
// such a TV, on any path; an ordinary Android TV whose Home is its stock launcher still gets it.
class Issue2028PlannerTest {
    // Our home screen on the TV, signed with the key the plan expects, so the takeover is reached.
    private val jio = Issue2028.snapshot(ourSigner = artifact().signer)
    private val none = ProfileResolution.None(KnownLaunchers.generic)
    private fun takeover(r: ProfileResolution) =
        PlanRequest.Setup(r, choices(installLauncher = false, takeover = true), artifact(), Level.SIMPLE)

    private fun withHome(s: Snapshot, home: HomeResolution): Snapshot = s.copy(home = home).let { it.copy(contentHash = SnapshotCodec.hash(it)) }

    @Test fun issue2028HasNoListAndItsStockHomeWouldBeTvlauncher() {
        assertIs<ProfileResolution.None>(BundledProfileSource().resolveNow(jio.facts))
        assertEquals(Issue2028.tvlauncher, StockHome.of(jio, KnownLaunchers.generic), "what 0.4.6.1 switched off")
        assertTrue(StockHome.unknownAboveOurs(jio, KnownLaunchers.generic))
    }

    @Test fun issue2028TheSwitchIsRefusedWithNoList() = releaseRule {
        assertEquals(null, HomeSwitchRisk.of(jio.facts, null, jio, WizardWifi.NOT_OWNER), "no risk of its own: the wizard is not there")
        assertEquals(listOf(DefaultPlanner.REASON_UNKNOWN_HOME_ABOVE), refused(takeover(none), jio))
        // The clean-up alone is planned as before, and switches tvlauncher off nowhere.
        val p = plan(PlanRequest.Setup(none, choices(installLauncher = false, takeover = false), artifact(), Level.SIMPLE), jio)
        assertTrue(p.sections.isEmpty())
        assertTrue(p.steps.none { it.op is Op.SetHome || it.op == Op.Disable(Issue2028.tvlauncher.pkg) })
    }

    // A tested list whose stock homes do not include the higher one gets the same refusal; one that
    // names it as the TV's own home screen does not count it as unknown.
    @Test fun issue2028TheSwitchIsRefusedOnAListThatDoesNotNameThatHome() = releaseRule {
        val list = profile()   // stock home: tvlauncher
        assertEquals(listOf(DefaultPlanner.REASON_UNKNOWN_HOME_ABOVE), refused(takeover(ProfileResolution.Exact(list)), jio))
        assertFalse(StockHome.unknownAboveOurs(jio, listOf(Issue2028.jio, Issue2028.tvlauncher)))
    }

    // An ordinary Android TV (Leanback), Home on its stock launcher: the switch is planned, with no
    // list (another maker, no setup wizard) and with one.
    @Test fun anOrdinaryLeanbackTvStillGetsTheSwitch() = releaseRule {
        val base = otherMaker(snapshot(claimants = listOf(PlannerFixtures.stock, Component(PackageName("com.android.tv.settings"), ".system.FallbackHome"))))
        val tv = base.copy(packages = base.packages - wizard).let { it.copy(contentHash = SnapshotCodec.hash(it)) }
        assertFalse(StockHome.unknownAboveOurs(tv, KnownLaunchers.generic))
        val generic = plan(setup(profile = null), tv)
        assertTrue(generic.steps.any { it.op == Op.SetHome(PlannerFixtures.launcher) })
        assertEquals(1, generic.sections.size)
        val listed = plan(setup(), snapshot())
        assertTrue(listed.steps.any { it.op == Op.SetHome(PlannerFixtures.launcher) })
    }

    // Google TV: launcherx is Home at priority 2, the wizard's RecoveryActivity and FallbackHome claim
    // it too. Neither is an unknown home above ours.
    @Test fun googleTvsLauncherxIsNoUnknownHome() {
        val launcherx = KnownLaunchers.LAUNCHERX
        val recovery = Component(KnownLaunchers.SETUPWRAITH, ".RecoveryActivity")
        val s = withHome(jio, HomeResolution(launcherx, listOf(HomeClaimant(launcherx, 2), HomeClaimant(recovery, 1), HomeClaimant(Issue2028.fallbackHome, -1000))))
        assertFalse(StockHome.unknownAboveOurs(s, KnownLaunchers.generic))
    }

    // What other rules decide is left to them: Home on the setup wizard (WIZARD_STAYS_ON), on a
    // FallbackHome (VENDOR_HOME for a vendor's), on the chooser, or on ours.
    @Test fun placeholdersAndOursAreNotUnknownHomes() {
        val recovery = Component(KnownLaunchers.SETUPWRAITH, ".RecoveryActivity")
        val keyFallback = Component(PackageName("com.realtek.keyintercept"), ".fallbackhome.FallbackHome")
        val chooser = Component(PackageName("android"), "com.android.internal.app.ResolverActivity")
        for (home in listOf(recovery, keyFallback, chooser, Issue2028.ours)) {
            val s = withHome(jio, HomeResolution(home, listOf(HomeClaimant(home, 5), HomeClaimant(Issue2028.tvlauncher, 2), HomeClaimant(Issue2028.ours, 0))))
            assertFalse(StockHome.unknownAboveOurs(s, KnownLaunchers.generic), home.flat)
        }
    }

    // Issue #1119: a home screen app the person installed, Home on it at priority 0 like ours, is not
    // above ours, so the switch stays as it was (set-home-activity decides between equals).
    @Test fun aPersonsLauncherAtOursPriorityIsNotAbove() {
        val s = withHome(jio, HomeResolution(Issue2028.personal, listOf(HomeClaimant(Issue2028.tvlauncher, 2), HomeClaimant(Issue2028.personal, 0), HomeClaimant(Issue2028.ours, 0))))
        assertFalse(StockHome.unknownAboveOurs(s, KnownLaunchers.generic))
        // An unknown Home the claimants give no priority for counts as above, the safe side.
        val unlisted = withHome(jio, HomeResolution(Issue2028.jio, listOf(HomeClaimant(Issue2028.tvlauncher, 2), HomeClaimant(Issue2028.ours, 0))))
        assertTrue(StockHome.unknownAboveOurs(unlisted, KnownLaunchers.generic))
    }
}
