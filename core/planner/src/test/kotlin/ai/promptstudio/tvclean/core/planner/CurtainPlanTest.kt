package ai.promptstudio.tvclean.core.planner

import ai.promptstudio.tvclean.core.model.BlobRef
import ai.promptstudio.tvclean.core.model.CurtainGroup
import ai.promptstudio.tvclean.core.model.CurtainStage
import ai.promptstudio.tvclean.core.model.EnabledState
import ai.promptstudio.tvclean.core.model.LauncherArtifact
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageEntry
import ai.promptstudio.tvclean.core.model.PackageGroup
import ai.promptstudio.tvclean.core.model.GroupDefault
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.PackageState
import ai.promptstudio.tvclean.core.model.Prior
import ai.promptstudio.tvclean.core.model.Sha256
import ai.promptstudio.tvclean.core.ops.FailurePolicy
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.choices
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.debloatList
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.installedLauncher
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.launcher
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.plan
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.profile
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.setup
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.snapshot
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.stock
import ai.promptstudio.tvclean.core.store.SnapshotCodec
import ai.promptstudio.tvclean.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// RFC-CURTAIN-1: the TV curtain in the Setup plan. Installed after the restore point, started, cued
// once per stage, removed as the last step; every curtain step CONTINUEs and none sits inside the
// guarded section; with the curtain off, refused by the TV or not handed in, the plan is today's.
class CurtainPlanTest {
    private val curtainPkg = PackageName("tv.cleanyour.curtain")
    private val curtainSha = Sha256("e".repeat(64))
    private val curtain = LauncherArtifact(curtainPkg, 1, "0.1", curtainSha, Samples.launcherArtifact().signer, BlobRef(curtainSha, 49_571, "bundled:tvclean-curtain.apk"))
    private val config = BlobRef(Sha256("c".repeat(64)), 100, "blobs/config")
    private val installed = debloatList.filter { it in snapshot().packages }
    // Our own earlier run's install of the curtain, as the change records keep it.
    private val ourInstall = listOf(PlannerFixtures.change(0, Op.InstallStream(curtain.blob, curtain.sha256, curtainPkg, 1, curtain.signer), Prior(present = false)))

    // A fresh planner per plan, so plans with and without the curtain draw the same watchdog token.
    private fun planner() = PlannerFixtures.planner(OverlayTransport())
    private fun withCurtain(req: PlanRequest.Setup = setup()) = req.copy(curtain = curtain)

    private fun label(s: Step): String = when (val op = s.op) {
        is Op.CurtainCue -> "cue ${op.stage} ${op.count}/${op.total}" + if (op.group == CurtainGroup.NONE) "" else " ${op.group} ${op.groupCount}/${op.groupTotal}"
        is Op.StartActivity -> "start ${op.component.flat}" + if (op.wait) " wait" else ""
        else -> op::class.simpleName + (DefaultPlanner.packageOf(op)?.let { " ${it.value}" } ?: "")
    }
    private fun curtainSteps(p: Plan) = p.steps.filter { it.group == DefaultPlanner.GROUP_CURTAIN }
    private fun body(p: Plan) = p.steps.filter { it.group != DefaultPlanner.GROUP_CURTAIN }

    private fun head() = listOf("InstallStream tv.cleanyour.curtain", "start tv.cleanyour.curtain/.CurtainActivity", "cue SCAN 0/18", "cue RESTORE_SAVED 0/0")
    private fun cleaning(group: String) = installed.indices.flatMap { i ->
        val k = i + 1
        listOf("Disable ${installed[i].value}", "cue OFF $k/18 $group $k/18") + if (k == 18) listOf("cue GROUP_DONE 18/18 $group 18/18") else emptyList()
    }

    // The Mi TV, Simple, the launcher installed by the plan, no look: FINALE right before the takeover
    // starts the launcher, the curtain's removal after the section.
    @Test fun fullCurtainPlanOnTheMiTv() {
        val p = plan(withCurtain(), planner = planner())
        assertEquals(
            head() + cleaning("OTHER") + listOf(
                // RFC-TRIM-1: the cache clear is the last body step before the settle, after the
                // last switch-off — the TV must have room before the launcher install.
                "TrimCaches", "Settle", "cue INSTALL 0/0", "InstallStream com.gathin.tvhome", "cue INSTALLED 0/0", "cue FINALE 0/0", "Settle",
                "start com.gathin.tvhome/.MainActivity wait", "Mkdir", "ArmHomeWatchdog", "Disable com.google.android.tvlauncher",
                "SetHome", "VerifyHome", "ConfirmHome", "KillWatchdog", "Uninstall tv.cleanyour.curtain",
            ),
            p.steps.map(::label),
        )
        assertCurtainInvariants(p, plan(setup(), planner = planner()))
        // One run token for every cue, its own (not the watchdog's).
        val tokens = p.steps.mapNotNull { (it.op as? Op.CurtainCue)?.token }.toSet()
        assertEquals(1, tokens.size)
        assertTrue(tokens.single() != p.sections.single().token)
        // The narration: the install speaks, the settle speaks, the rest is silent.
        val voiced = curtainSteps(p).filter { it.narration.isNotBlank() }.map { label(it) to it.narration }
        assertEquals(listOf("InstallStream tv.cleanyour.curtain" to "Showing the progress on the TV", "Settle" to "Giving the TV a moment"), voiced)
        assertEquals(Op.Settle(3), curtainSteps(p).first { it.op is Op.Settle }.op)
        assertEquals(Prior(present = false), p.steps.first().prior)
        assertEquals(null, p.steps.last().prior, "the curtain is not on the TV when the run starts")
        assertEquals(Prior(present = true), plan(withCurtain(), snapshot = withPackage(curtainState()), journals = ourInstall, planner = planner()).steps.last().prior)
    }

    // RFC-DOCK-1 applies: the look's first step starts the launcher, so FINALE goes before it.
    @Test fun withTheLookTheFinaleComesBeforeTheLooksStart() {
        val p = plan(withCurtain(setup().copy(config = config)), planner = planner())
        val labels = p.steps.map(::label)
        val finale = labels.indexOf("cue FINALE 0/0")
        assertEquals(listOf("cue FINALE 0/0", "Settle", "start com.gathin.tvhome/.MainActivity wait", "Mkdir", "PushAsset", "RemoveFile", "RestartHome",
            "start com.gathin.tvhome/.MainActivity wait"), labels.subList(finale, finale + 8))
        assertEquals(DefaultPlanner.GROUP_THEME, p.steps[finale + 2].group)
        assertCurtainInvariants(p, plan(setup().copy(config = config), planner = planner()))
    }

    // The curtain is already on the TV with the pinned version (a leftover): no install, just start it.
    @Test fun pinAlreadyInstalledIsStartedOnly() {
        val snap = withPackage(curtainState(versionCode = 1))
        val p = plan(withCurtain(), snapshot = snap, journals = ourInstall, planner = planner())
        assertEquals(listOf("start tv.cleanyour.curtain/.CurtainActivity", "cue SCAN 0/18"), p.steps.take(2).map(::label))
        assertEquals("Uninstall tv.cleanyour.curtain", label(p.steps.last()))
        // An older version is replaced (D39 ladder with the package present).
        val older = plan(withCurtain(), snapshot = withPackage(curtainState(versionCode = 0)), journals = ourInstall, planner = planner())
        assertEquals("InstallStream tv.cleanyour.curtain", label(older.steps.first()))
        assertEquals(Prior(present = true), older.steps.first().prior)
        // Without cmd: push, install with -r, tidy up; only the install speaks.
        val noCmd = snap.copy(facts = snap.facts.copy(adbFeatures = snap.facts.adbFeatures - "cmd")).let { it.copy(contentHash = SnapshotCodec.hash(it)) }
        val pushed = plan(withCurtain(), snapshot = withPackage(curtainState(versionCode = 0), noCmd), journals = ourInstall, planner = planner())
        assertEquals(listOf("Mkdir", "PushAsset", "Install tv.cleanyour.curtain", "RemoveFile"), pushed.steps.take(4).map(::label))
        assertEquals(setOf(ai.promptstudio.tvclean.core.model.InstallFlag.REPLACE), (pushed.steps[2].op as Op.Install).flags)
        assertEquals(listOf("", "", "Showing the progress on the TV", ""), pushed.steps.take(4).map { it.narration })
    }

    // A package under our name with another signer is someone else's: no curtain, never touched.
    @Test fun anotherSignerUnderOurNameIsLeftAlone() {
        val p = plan(withCurtain(), snapshot = withPackage(curtainState(signer = Sha256("9".repeat(64)))), planner = planner())
        assertTrue(curtainSteps(p).isEmpty())
        assertEquals(plan(setup(), planner = planner()).steps.map { it.op }, p.steps.map { it.op })
    }

    // RF-CURTAIN-1 F2: "ours" is the journal rule of removeModule. A curtain package with no install
    // of ours on record (a squatter, or ours after the phone app lost its records) is never started,
    // cued or removed: Setup plans no curtain at all and Leftovers leaves it. Our own leftover (an
    // install in the records, or installedByUs) is reused by Setup and removed by Leftovers.
    @Test fun onlyACurtainWeInstalledIsTouched() {
        val squat = withPackage(curtainState())
        val setupSquat = plan(withCurtain(), snapshot = squat, planner = planner())
        assertTrue(curtainSteps(setupSquat).isEmpty(), setupSquat.steps.map(::label).toString())
        assertEquals(plan(setup(), snapshot = squat, planner = planner()).steps.map { it.op }, setupSquat.steps.map { it.op })
        assertTrue(plan(PlanRequest.Leftovers(emptySet()), snapshot = squat).steps.isEmpty())
        // Our own interrupted run left it: the install is in the records.
        val reused = plan(withCurtain(), snapshot = squat, journals = ourInstall, planner = planner())
        assertEquals("start tv.cleanyour.curtain/.CurtainActivity", label(reused.steps.first()))
        assertEquals(listOf("Uninstall tv.cleanyour.curtain"), plan(PlanRequest.Leftovers(emptySet()), snapshot = squat, journals = ourInstall).steps.map(::label))
        // Marked installed by us in the snapshot does too.
        val marked = withPackage(curtainState().copy(installedByUs = true))
        assertEquals(listOf("Uninstall tv.cleanyour.curtain"), plan(PlanRequest.Leftovers(emptySet()), snapshot = marked).steps.map(::label))
        // Our own run on a clean TV: installed, used and removed by the plan itself.
        val own = plan(withCurtain(), planner = planner())
        assertEquals("InstallStream tv.cleanyour.curtain", label(own.steps.first()))
        assertEquals("Uninstall tv.cleanyour.curtain", label(own.steps.last()))
    }

    @Test fun installRefusalToggleOffAndNoPinGiveTodaysPlan() {
        val today = plan(setup(), planner = planner())
        assertEquals(today.steps.map { it.op }, plan(withCurtain(setup(choices = choices().copy(curtain = false))), planner = planner()).steps.map { it.op })
        assertEquals(today.hash, plan(withCurtain(setup(choices = choices().copy(curtain = false))), planner = planner()).hash)
        val s = snapshot()
        val refusing = s.copy(facts = s.facts.copy(installRefusal = true)).let { it.copy(contentHash = SnapshotCodec.hash(it)) }
        val r = plan(withCurtain(setup(choices = choices(installLauncher = false, takeover = false))), snapshot = refusing, planner = planner())
        assertTrue(curtainSteps(r).isEmpty(), "a TV that refuses installs gets no curtain")
        // A plan with nothing to do shows nothing either.
        val asleep = snapshot(disabled = installed.map { it.value }.toSet(), home = launcher, claimants = listOf(launcher, stock), extra = mapOf(installedLauncher()))
        assertTrue(plan(withCurtain(), snapshot = asleep, planner = planner()).steps.isEmpty())
    }

    // No takeover and no look: FINALE is the last cue, then the settle, then the removal.
    @Test fun noTakeoverEndsWithTheFinale() {
        val p = plan(withCurtain(setup(choices = choices(takeover = false))), planner = planner())
        assertEquals(listOf("cue INSTALLED 0/0", "cue FINALE 0/0", "Settle", "Uninstall tv.cleanyour.curtain"), p.steps.takeLast(4).map(::label))
        assertTrue(p.sections.isEmpty())
        val debloatOnly = plan(withCurtain(setup(choices = choices(takeover = false, installLauncher = false))), planner = planner())
        assertTrue(debloatOnly.steps.none { (it.op as? Op.CurtainCue)?.stage == CurtainStage.INSTALL })
        assertEquals(listOf("cue GROUP_DONE 18/18 OTHER 18/18", "cue FINALE 0/0", "Settle", "Uninstall tv.cleanyour.curtain"), debloatOnly.steps.takeLast(4).map(::label))
    }

    // The group words: a profile's category ids map to the fixed vocabulary; counts restart per group,
    // the overall count runs on; the custom switch-offs are "other".
    @Test fun groupsMapToTheFixedVocabulary() {
        val pk = installed
        val groups = listOf(
            PackageGroup("ads", "Adverts and sponsored rows", GroupDefault.DISABLE, pk.take(2).map { PackageEntry(it, null) }),
            PackageGroup("tracking-optional", "More tracking services", GroupDefault.ASK, pk.drop(2).take(1).map { PackageEntry(it, null) }),
            PackageGroup("preloads", "Apps nobody asked for", GroupDefault.DISABLE, pk.drop(3).take(1).map { PackageEntry(it, null) }),
            PackageGroup("brand", "Xiaomi extras", GroupDefault.DISABLE, pk.drop(4).take(1).map { PackageEntry(it, null) }),
            PackageGroup("demo", "Shop and demo mode", GroupDefault.DISABLE, pk.drop(5).take(1).map { PackageEntry(it, null) }),
        )
        val req = setup(profile = profile().copy(groups = groups), choices = choices(extraOff = setOf(pk[2], pk[6]), takeover = false, installLauncher = false))
        val cues = plan(withCurtain(req), planner = planner()).steps.mapNotNull { it.op as? Op.CurtainCue }.filter { it.stage == CurtainStage.OFF || it.stage == CurtainStage.GROUP_DONE }
        assertEquals(listOf(
            "OFF 1/7 ADVERTS 1/2", "OFF 2/7 ADVERTS 2/2", "GROUP_DONE 2/7 ADVERTS 2/2", "OFF 3/7 TRACKING 1/1", "GROUP_DONE 3/7 TRACKING 1/1",
            "OFF 4/7 NOBODY_ASKED 1/1", "GROUP_DONE 4/7 NOBODY_ASKED 1/1", "OFF 5/7 BRAND_EXTRAS 1/1", "GROUP_DONE 5/7 BRAND_EXTRAS 1/1",
            "OFF 6/7 SHOP_DEMO 1/1", "GROUP_DONE 6/7 SHOP_DEMO 1/1", "OFF 7/7 OTHER 1/1", "GROUP_DONE 7/7 OTHER 1/1",
        ), cues.map { "${it.stage} ${it.count}/${it.total} ${it.group} ${it.groupCount}/${it.groupTotal}" })
    }

    // A curtain left by an interrupted run: Leftovers removes it first. DebugOff stays as it is (it
    // must not need a pass), so the curtain is removed by Leftovers or by the next Setup.
    @Test fun leftoverIsRemovedFirstByLeftovers() {
        val snap = withPackage(curtainState())
        val p = plan(PlanRequest.Leftovers(setOf(installed.first())), snapshot = snap, journals = ourInstall)
        assertEquals(listOf("Uninstall tv.cleanyour.curtain", "Disable ${installed.first().value}"), p.steps.map(::label))
        assertEquals("Removing the progress screen from the TV", p.steps.first().narration)
        assertEquals(FailurePolicy.CONTINUE, p.steps.first().onFailure)
        assertEquals(listOf("Disable ${installed.first().value}"), plan(PlanRequest.Leftovers(setOf(installed.first()))).steps.map(::label))
        val off = plan(PlanRequest.DebugOff, snapshot = snap, journals = ourInstall)
        assertTrue(off.steps.none { it.op is Op.Uninstall }, "debug_off never needs a pass")
        // Someone else's app under our name is not a leftover.
        val theirs = plan(PlanRequest.Leftovers(emptySet()), snapshot = withPackage(curtainState(signer = Sha256("9".repeat(64)))), journals = ourInstall,
            planner = DefaultPlanner(PlannerFixtures.clock, ai.promptstudio.tvclean.testing.fixtures.FakeIdGen(), ai.promptstudio.tvclean.core.ops.ShellRenderer(), OverlayTransport(), "0.1.0", curtainSigner = curtain.signer))
        assertTrue(theirs.steps.isEmpty())
    }

    // Undo everything after a finished run has nothing of the curtain to remove; after a run cut off
    // before its last step, it removes the curtain with the other apps we installed.
    @Test fun undoAfterAFinishedAndAnInterruptedRun() {
        val p = plan(withCurtain(), planner = planner())
        val finished = ModelTv(snapshot()).also { it.apply(p) }
        assertTrue(curtainPkg !in finished.packages)
        val undoFinished = plan(PlanRequest.UndoEverything(finished.changes, removeLauncher = true), snapshot = finished.snapshot(), journals = finished.changes)
        assertTrue(undoFinished.steps.none { DefaultPlanner.packageOf(it.op) == curtainPkg }, undoFinished.steps.map(::label).toString())
        val cut = p.copy(steps = p.steps.take(p.steps.indexOfFirst { it.op is Op.Settle && it.group == DefaultPlanner.GROUP_LAUNCHER }))
        val interrupted = ModelTv(snapshot()).also { it.apply(cut) }
        assertTrue(curtainPkg in interrupted.packages)
        val undoCut = plan(PlanRequest.UndoEverything(interrupted.changes, removeLauncher = true), snapshot = interrupted.snapshot(), journals = interrupted.changes)
        assertTrue(undoCut.steps.any { it.op == Op.Uninstall(curtainPkg) }, undoCut.steps.map(::label).toString())
        val back = ModelTv(snapshot())
        interrupted.apply(undoCut)
        assertEquals(back.state(), interrupted.state())
    }

    // Everything the curtain adds is CONTINUE and outside the guarded section, the last step removes
    // it, and taking the curtain steps out leaves exactly the plan without it (same ops, priors,
    // policies, narration and section).
    private fun assertCurtainInvariants(p: Plan, without: Plan) {
        val c = curtainSteps(p)
        assertTrue(c.isNotEmpty())
        assertTrue(c.all { it.onFailure == FailurePolicy.CONTINUE }, c.filter { it.onFailure != FailurePolicy.CONTINUE }.toString())
        assertTrue(c.none { s -> p.sections.any { s.index in it.from..it.to } }, "no curtain step inside a guarded section")
        assertEquals(Op.Uninstall(curtainPkg), p.steps.last().op)
        assertEquals(p.steps.indices.toList(), p.steps.map { it.index })
        fun strip(steps: List<Step>) = steps.map { listOf(it.op, it.prior, it.group, it.narration, it.onFailure, it.rendered.lines.map { l -> l.text }) }
        assertEquals(strip(without.steps), strip(body(p)))
        assertEquals(without.sections.map { s -> s.copy(from = 0, to = 0) }, p.sections.map { s -> s.copy(from = 0, to = 0) })
        for ((a, b) in without.sections.zip(p.sections)) {
            assertEquals(without.steps[a.from].op, p.steps[b.from].op); assertEquals(without.steps[a.to].op, p.steps[b.to].op)
        }
        assertTrue(p.hash != without.hash)
    }

    private fun curtainState(versionCode: Long = 1, signer: Sha256? = null) =
        PackageState(EnabledState.ENABLED, false, versionCode, signer, false, false, null, false)

    private fun withPackage(state: PackageState, base: ai.promptstudio.tvclean.core.model.Snapshot = snapshot()) =
        base.copy(packages = base.packages + (curtainPkg to state)).let { it.copy(contentHash = SnapshotCodec.hash(it)) }
}
