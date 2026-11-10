package ai.promptstudio.tvclean.core.flows

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import ai.promptstudio.tvclean.core.engine.JournalRecord
import ai.promptstudio.tvclean.core.engine.RunStatus
import ai.promptstudio.tvclean.core.model.EnabledState
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.PackageState
import ai.promptstudio.tvclean.core.model.Prior
import ai.promptstudio.tvclean.core.model.SnapshotReason
import ai.promptstudio.tvclean.core.model.Tv
import ai.promptstudio.tvclean.core.planner.PlanKind
import ai.promptstudio.tvclean.core.planner.PlanRequest
import ai.promptstudio.tvclean.core.planner.PlanResult
import ai.promptstudio.tvclean.core.planner.PutBackAdvice
import ai.promptstudio.tvclean.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// WP4 item 2 (C6): "Better back on". The Switched off list shows the group at the top only for a
// package this app's journal switched off on a TV whose exact profile has an advice entry, and that
// is off now; Manage counts the same rows in one line; tapping "Switch back on" is the ordinary
// PutBack, one plan, followed by the restore point write.
@OptIn(ExperimentalCoroutinesApi::class)
class BetterBackOnTest {
    private val mic = PackageName("com.google.android.tv.remote.service")
    private val g10 = "tcl-g10-google-tv"

    private suspend fun Harness.tvWith(profileId: String, offOnTv: Set<PackageName>): Tv {
        val t = knownTv().copy(profileId = profileId)
        tvs.upsert(t)
        val off = PackageState(EnabledState.DISABLED_USER, true, 1, null, false, false, null, false)
        val on = off.copy(enabled = EnabledState.ENABLED)
        val s = Samples.snapshot(reason = SnapshotReason.POST_PLAN, takenAtMs = Samples.NOW_MS + 1_000,
            packages = Samples.miTvPackages() + listOf(mic, adPkg).associateWith { if (it in offOnTv) off else on })
        snapshots.save(s)
        return t
    }

    // The journal of a finished run that switched `pkgs` off, recorded the way the engine writes it.
    private suspend fun Harness.ranDisable(tv: Tv, pkgs: List<PackageName>) {
        val plan = plan(pkgs.map { Op.Disable(it) }, id = "01FAKERUN0000000000000001")
        ledger.record(plan)
        journal.append(JournalRecord.PlanStart(plan.id, tv.id, Samples.zeroHash, pkgs.size, plan.hash, "0.1.0", 0))
        pkgs.forEachIndexed { i, p ->
            journal.append(JournalRecord.Begin(plan.id, i, Op.Disable(p), listOf("fake"), Prior(packageState = EnabledState.ENABLED), 10L + i))
            journal.append(JournalRecord.Done(plan.id, i, 0, true, 5))
        }
        journal.append(JournalRecord.End(plan.id, RunStatus.COMPLETED))
    }

    private fun betterOf(m: SwitchedOffMachine) = m.state.value.groups.firstOrNull { it.id == OffGroup.BETTER_BACK_ON }
    private fun cardOf(m: ManageMachine, tv: Tv) = m.state.value.cards.single { it.id == tv.id }

    @Test fun theGroupShowsAtTheTopWithOurJournalEntry() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val tv = h.tvWith(g10, offOnTv = setOf(mic, adPkg))
        h.ranDisable(tv, listOf(adPkg, mic))
        val m = SwitchedOffMachine(h.manageDeps(), backgroundScope, tv.id)
        settle()
        val groups = m.state.value.groups
        assertEquals(OffGroup.BETTER_BACK_ON, groups.first().id, "at the top")
        assertEquals(OffGroup.BETTER_BACK_ON_TITLE, groups.first().title)
        val row = groups.first().rows.single()
        assertEquals(mic, row.pkg)
        assertEquals(PutBackAdvice.REASON_REMOTE_MIC, row.reason)
        assertEquals(listOf(mic, adPkg), groups.flatMap { g -> g.rows.map { it.pkg } }, "each package once")
        assertEquals(2, m.state.value.count)
        val manage = ManageMachine(h.manageDeps(), backgroundScope, tv.id)
        settle()
        assertEquals(1, cardOf(manage, tv).betterBackOn)
    }

    // Nothing is offered for what the factory or the owner switched off, for a package that is on
    // now, or on a TV whose profile has no entry for it.
    @Test fun noGroupWithoutOurSwitchOffOrWhenItIsOnOrOnAnotherTv() = runTest(timeout = 30.seconds) {
        data class Case(val name: String, val profile: String, val offOnTv: Set<PackageName>, val ours: List<PackageName>)
        for (c in listOf(
            Case("off by the factory", g10, setOf(mic, adPkg), listOf(adPkg)),
            Case("on now", g10, setOf(adPkg), listOf(adPkg, mic)),
            Case("another profile", "xiaomi-mitv-axso0", setOf(mic, adPkg), listOf(adPkg, mic)),
        )) {
            val h = Harness()
            val tv = h.tvWith(c.profile, c.offOnTv)
            h.ranDisable(tv, c.ours)
            val m = SwitchedOffMachine(h.manageDeps(), backgroundScope, tv.id)
            settle()
            assertEquals(null, betterOf(m), c.name)
            assertTrue(m.state.value.groups.none { g -> g.rows.any { it.reason != null } }, c.name)
            val manage = ManageMachine(h.manageDeps(), backgroundScope, tv.id)
            settle()
            assertEquals(0, cardOf(manage, tv).betterBackOn, c.name)
        }
    }

    // Kalley R10G (HOME_TAKEOVER_SETUPWRAITH): while our home screen is in charge the planner refuses
    // the wizard's put back, so its row is never offered; the stock home's row stays, since putting
    // launcherx back alone ends the restart risk. With the TV's own home screen in charge both show.
    @Test fun aWizardRowThePlannerWouldRefuseIsNotOffered() = runTest(timeout = 30.seconds) {
        val launcherx = PackageName("com.google.android.apps.tv.launcherx")
        val wizard = PackageName("com.google.android.tungsten.setupwraith")
        val kalley = ai.promptstudio.tvclean.core.spec.BundledProfileSource.bundled.profiles.single { it.id == "kalley-r10g" }
        val ours = ai.promptstudio.tvclean.core.model.HomeResolution(Launcher.home, listOf(ai.promptstudio.tvclean.core.model.HomeClaimant(Launcher.home, 2)))
        val theirs = ai.promptstudio.tvclean.core.model.HomeResolution(Samples.stockLauncher, listOf(ai.promptstudio.tvclean.core.model.HomeClaimant(Samples.stockLauncher, 2)))
        for ((home, expected) in listOf(ours to listOf(launcherx), theirs to listOf(launcherx, wizard))) {
            val h = Harness()
            val t = h.knownTv().copy(profileId = kalley.id)
            h.tvs.upsert(t)
            val off = PackageState(EnabledState.DISABLED_USER, true, 1, null, false, false, null, false)
            h.snapshots.save(Samples.snapshot(reason = SnapshotReason.POST_PLAN, takenAtMs = Samples.NOW_MS + 1_000, home = home,
                packages = Samples.miTvPackages() + mapOf(launcherx to off, wizard to off)))
            h.ranDisable(t, listOf(launcherx, wizard))
            val deps = h.manageDeps(profiles = ai.promptstudio.tvclean.testing.fixtures.FakeProfileSource(listOf(miProfile, kalley)))
            val m = SwitchedOffMachine(deps, backgroundScope, t.id)
            settle()
            val name = if (home === ours) "ours is Home" else "the TV's own is Home"
            assertEquals(expected, betterOf(m)?.rows?.map { it.pkg }, name)
            assertEquals(PutBackAdvice.REASON_BOOT_LOOP, betterOf(m)!!.rows.first().reason)
            val manage = ManageMachine(deps, backgroundScope, t.id)
            settle()
            assertEquals(expected.size, cardOf(manage, t).betterBackOn, name)
        }
    }

    // "Switch back on" is the existing PutBack: exactly one PutBack plan for that package, nothing
    // planned before the tap, and the restore point is written after it.
    @Test fun tappingPlansExactlyOnePutBack() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val tv = h.tvWith(g10, offOnTv = setOf(mic))
        h.ranDisable(tv, listOf(mic))
        val previous = h.planner.respond
        h.planner.respond = { r, s -> if (r is PlanRequest.PutBack) PlanResult.Ok(h.plan(listOf(Op.Enable(mic)), kind = PlanKind.PUT_BACK, id = "01FAKEPUTB00000000000000001")) else previous(r, s) }
        val m = SwitchedOffMachine(h.manageDeps(), backgroundScope, tv.id)
        settle()
        assertTrue(h.planner.requests.isEmpty(), "nothing is planned until the person taps")
        m.effects.test {
            m.dispatch(SwitchedOffIntent.PutBack(betterOf(m)!!.rows.single().pkg))
            settle()
            // No Undo on the snackbar: it would switch off what the list now keeps on.
            val snack = kotlin.test.assertIs<ManageEffect.Snackbar>(drain().single())
            assertEquals(SnackKind.PUT_BACK, snack.kind)
            assertEquals(false, snack.undo)
        }
        assertEquals(listOf<PlanRequest>(PlanRequest.PutBack(mic)), h.planner.requests.filter { it !is PlanRequest.WriteRestorePoint })
        assertTrue(h.planner.requests.any { it is PlanRequest.WriteRestorePoint }, "D44: the restore point after the put back")
    }
}
