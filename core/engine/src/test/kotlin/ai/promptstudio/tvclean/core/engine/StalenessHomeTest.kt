package ai.promptstudio.tvclean.core.engine

import ai.promptstudio.tvclean.core.model.Component
import ai.promptstudio.tvclean.core.model.HomeResolution
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.Prior
import ai.promptstudio.tvclean.core.ops.ShellRenderer
import ai.promptstudio.tvclean.core.planner.Step
import ai.promptstudio.tvclean.core.ops.FailurePolicy
import ai.promptstudio.tvclean.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// WP4 item 4 (B5): the planner records the stock home as the SetHome prior while HOME answers with
// the chooser, a placeholder or nothing (StockHome.recorded). The staleness check before step 0
// then takes that same answer as no change, and still sees a real home screen that took over.
class StalenessHomeTest {
    // G4: step R from the journal alone never hands HOME to a placeholder an old journal recorded
    // as the SetHome prior; it goes to the stock home the watchdog was armed with.
    @Test fun journalStepRNeverRestoresAPlaceholder() {
        val id = ai.promptstudio.tvclean.core.model.PlanId("01FAKEPLAN0000000000000001")
        val token = ai.promptstudio.tvclean.core.model.RunToken("00000000000000aa")
        val chooser = Component(PackageName("android"), "com.android.internal.app.ResolverActivity")
        fun records(prior: Component) = listOf(
            JournalRecord.PlanStart(id, Samples.miTvId(), Samples.zeroHash, 3, Samples.zeroHash, "0.4.0", 0),
            JournalRecord.Section(id, "home_takeover", token, SectionState.ARMED, 0),
            JournalRecord.Begin(id, 0, Op.ArmHomeWatchdog(stock, 150, token), emptyList(), null, 1),
            JournalRecord.Begin(id, 1, Op.SetHome(launcher), emptyList(), Prior(home = prior), 2),
        )
        val r = JournalStepR.build(records(chooser), Samples.miTvId())!!.sections.single().rollback
        assertTrue(Op.SetHome(stock) in r && Op.VerifyHome(stock) in r, "$r")
        assertTrue(r.none { (it as? Op.SetHome)?.component == chooser })
        val other = Component(PackageName("com.spocky.projengmenu"), ".ui.home.MainActivity")
        assertTrue(Op.SetHome(other) in JournalStepR.build(records(other), Samples.miTvId())!!.sections.single().rollback, "a real prior is kept")
    }

    private val stock = Samples.stockLauncher
    private val launcher = Samples.launcherComponent
    private val step = Step(0, Op.SetHome(launcher), ShellRenderer().render(Op.SetHome(launcher)), Prior(home = stock), "home", "", FailurePolicy.STOP_AND_ROLLBACK)
    private fun home(resolved: Component?) = Samples.miTvSnapshot().let { it.copy(home = HomeResolution(resolved, it.home.claimants)) }

    @Test fun aPlaceholderAnswerIsNoChangeAndARealOneIs() {
        for (placeholder in listOf(
            Component(PackageName("android"), "com.android.internal.app.ResolverActivity"),
            Component(PackageName("com.android.tv.settings"), ".system.FallbackHome"),
            Component(PackageName("com.google.android.tungsten.setupwraith"), ".RecoveryActivity"),
            null,
        )) assertEquals(emptyList(), Staleness.diff(listOf(step), home(placeholder)), "${placeholder?.flat}")
        assertEquals(emptyList(), Staleness.diff(listOf(step), home(stock)))
        // Only while the recorded home still claims HOME: one that lost its claim is a change.
        val chooser = Component(PackageName("android"), "com.android.internal.app.ResolverActivity")
        val lost = Samples.miTvSnapshot().copy(home = HomeResolution(chooser, emptyList()))
        assertEquals(listOf("home: was ${stock.flat}, now ${chooser.flat}"), Staleness.diff(listOf(step), lost))
        val other = Component(PackageName("com.spocky.projengmenu"), ".ui.home.MainActivity")
        assertEquals(listOf("home: was ${stock.flat}, now ${other.flat}"), Staleness.diff(listOf(step), home(other)))
    }
}
