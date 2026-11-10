package ai.promptstudio.tvclean.core.planner

import ai.promptstudio.tvclean.core.model.Level
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.ops.Denylist
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.releaseRule
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.artifact
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.choices
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.plan
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.refused
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.snapshot
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.wizard
import ai.promptstudio.tvclean.core.spec.BundledProfileSource
import ai.promptstudio.tvclean.core.spec.KnownLaunchers
import ai.promptstudio.tvclean.core.spec.ProfileResolution
import ai.promptstudio.tvclean.core.spec.SureSet
import ai.promptstudio.tvclean.core.store.SnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ai.promptstudio.tvclean.core.spec.SureSetGenerator

// D26 amendment (0.3.5): the careful sets of brands with one or two lists. Their rows pass every
// rule the planner and the engine apply, and a TV on one gets the home screen decision of a TV with
// no set at all, since its stock launchers are the generic ones.
class CarefulSetSafetyTest {
    // The reference TV, its setup wizard installed and on, under another maker: the careful sets are
    // other brands', and the Xiaomi itself meets R0 (0.4.6).
    private val tv = PlannerFixtures.otherMaker(snapshot())
    private val noWizard = tv.copy(packages = tv.packages - wizard).let { it.copy(contentHash = SnapshotCodec.hash(it)) }
    private val careful: List<SureSet> = BundledProfileSource.bundled.let { b ->
        val lists = SureSetGenerator.feeding(b.profiles.filter { !it.isCandidate }).groupBy { it.match.brand!!.lowercase() }
        b.sureSets.filter { lists.getValue(it.brand.lowercase()).size < 3 }
    }

    private fun setup(r: ProfileResolution, wizardWifi: WizardWifi) = PlanRequest.Setup(r, choices(wizardWifi = wizardWifi), artifact(), Level.SIMPLE)
    private fun of(s: SureSet) = ProfileResolution.BrandSureSet(s.brand, s.groups, s.stockLaunchers)
    private val none = ProfileResolution.None(KnownLaunchers.generic)

    @Test fun everyCarefulRowPassesTheDenylistAndTheSwitchOffRule() {
        assertTrue(careful.size >= 20, "careful sets: ${careful.map { it.brand }}")
        val refusedRows = mutableListOf<String>()
        for (s in careful) for (g in s.groups) for (e in g.packages) {
            Denylist.staticReason(e.name.value)?.let { refusedRows += "${s.brand} ${e.name.value}: $it" }
            SwitchOffRule.reason(e.name, null, null, null, s.stockLaunchers)?.let { refusedRows += "${s.brand} ${e.name.value}: $it" }
            SwitchOffRule.reason(e.name, tv.packages[e.name], null, tv, s.stockLaunchers)?.let { refusedRows += "${s.brand} ${e.name.value} on the TV: $it" }
        }
        assertEquals(emptyList(), refusedRows)
    }

    @Test fun aCarefulSetGetsTheHomeScreenDecisionOfNoSet() = releaseRule {
        for (s in careful) {
            assertEquals(KnownLaunchers.generic, s.stockLaunchers, s.brand)
            // With the setup wizard on, the switch is refused for the same reason as without a set.
            for (w in WizardWifi.entries) assertEquals(refused(setup(none, w), tv), refused(setup(of(s), w), tv), "${s.brand} $w")
            // Without it, the same home screen steps are planned; only the careful rows are added,
            // with the pause that lets the TV settle after a clean-up and before the switch.
            val rows = s.groups.flatMap { g -> g.packages.map { it.name } }.toSet()
            val withSet = plan(setup(of(s), WizardWifi.NOT_OWNER), snapshot = noWizard)
            val without = plan(setup(none, WizardWifi.NOT_OWNER), snapshot = noWizard)
            assertEquals(without.steps.map { it.op }, withSet.steps.map { it.op }.filterNot { (it is Op.Disable && it.pkg in rows) || it is Op.Settle }, s.brand)
            fun home(p: Plan) = p.sections.map { listOf(it.name, it.expectedHome, it.rollbackIfFocus, it.rollback) }
            assertTrue(without.sections.isNotEmpty(), s.brand)
            assertEquals(home(without), home(withSet), s.brand)
        }
        assertNull(HomeSwitchRisk.of(noWizard.facts, null, noWizard, WizardWifi.NOT_OWNER))
    }
}
