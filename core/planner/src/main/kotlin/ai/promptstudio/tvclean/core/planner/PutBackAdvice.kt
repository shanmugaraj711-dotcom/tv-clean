package ai.promptstudio.tvclean.core.planner

import ai.promptstudio.tvclean.core.model.ChangeRecord
import ai.promptstudio.tvclean.core.model.EnabledState
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.Profile
import ai.promptstudio.tvclean.core.model.Snapshot
import ai.promptstudio.tvclean.core.ops.Denylist

// "Better back on" (WP4, C6): packages an earlier list switched off that upstream's list for the
// same TV now keeps on, each with our reason. After the b06ab21 bump they are commented-out (kept)
// lines in that folder's debloat.txt, so they leave the profile; a TV this app already cleaned may
// still have them off. A test reads the pinned lists so this table cannot drift from upstream.
//
// The offer is only ever an offer: the advice lists a package when the TV's exact profile has an
// entry for it, the package is off now, and this app's own change records for the TV show that we
// switched it off (the last change of ours to it is a Disable, made while it was on). What the
// factory or the owner switched off is never offered. Nothing runs until the person taps "Switch
// back on", which is the ordinary PutBack plan (one Enable, or the inverse of our switch-off),
// journaled with its prior and followed by the usual restore point write. Its snackbar offers no
// Undo, which would switch it off again.
object PutBackAdvice {
    data class Entry(val profileId: String, val pkg: PackageName, val reason: String)

    const val REASON_REMOTE_MIC = "Your remote's mic button needs this."
    const val REASON_TCL_UPDATE_DNUM = "TCL's software update needs this to find your TV."
    const val REASON_TCL_UPDATES = "This brings TCL's software updates."
    const val REASON_BOOT_LOOP = "With the TV's own home screen and setup wizard both off, this TV can restart over and over."

    val entries: List<Entry> = listOf(
        Entry("tcl-g10-google-tv", PackageName("com.google.android.tv.remote.service"), REASON_REMOTE_MIC),
        Entry("tcl-g03-google-tv", PackageName("com.tcl.xian.StartandroidService"), REASON_TCL_UPDATE_DNUM),
        Entry("tcl-ak30a5", PackageName("com.tcl.versionUpdateApp"), REASON_TCL_UPDATES),
        Entry("kalley-r10g", PackageName("com.google.android.apps.tv.launcherx"), REASON_BOOT_LOOP),
        Entry("kalley-r10g", PackageName("com.google.android.tungsten.setupwraith"), REASON_BOOT_LOOP),
    )

    fun forProfile(profileId: String?): List<Entry> = if (profileId == null) emptyList() else entries.filter { it.profileId == profileId }

    // The entries to offer on this TV now. `profileId` is the TV's exact profile (Tv.profileId, null
    // without one); `snapshot` the latest read of the TV (null: unknown, nothing is offered);
    // `changes` this app's change records for the TV; `profile` that profile itself, for the rule
    // below (null: not known, nothing is held back).
    //
    // A row whose tap the planner is known to refuse is never offered: on a TV whose setup wizard
    // takes the Home button back (the HOME_TAKEOVER_SETUPWRAITH quirk), DefaultPlanner.putBack
    // refuses the wizard while our home screen is in charge. On Kalley R10G that leaves the stock
    // home's row, and putting launcherx back alone already ends the restart risk, which needs both off.
    fun offers(profileId: String?, snapshot: Snapshot?, changes: List<ChangeRecord>, profile: Profile? = null): List<Entry> {
        if (snapshot == null) return emptyList()
        return forProfile(profileId).filter { e ->
            val off = snapshot.packages[e.pkg]?.enabled.let { it == EnabledState.DISABLED_USER || it == EnabledState.DISABLED }
            off && ourDisableOfAnOnPackage(e.pkg, changes) && !wizardHeldBack(e.pkg, profile, snapshot)
        }
    }

    // DefaultPlanner.putBack's wizard rule (R1 R-3), the same facts: the wizard comes back on only
    // with the TV's own home screen, never under the new one.
    fun wizardHeldBack(pkg: PackageName, profile: Profile?, snapshot: Snapshot): Boolean =
        profile != null && Denylist.isWizardAllowed(pkg, profile) && snapshot.home.resolved?.pkg == SwitchOffRule.OUR_LAUNCHER

    // The last change of ours to it is a Disable, and that Disable's own prior says it was on
    // (ENABLED or DEFAULT): one that was off already, or with no prior, was not ours to offer.
    private fun ourDisableOfAnOnPackage(pkg: PackageName, changes: List<ChangeRecord>): Boolean {
        val last = changes.filter { c ->
            when (val op = c.op) {
                is Op.Disable -> op.pkg == pkg
                is Op.Enable -> op.pkg == pkg
                is Op.DefaultState -> op.pkg == pkg
                is Op.Uninstall -> op.pkg == pkg
                else -> false
            }
        }.maxWithOrNull(compareBy<ChangeRecord> { it.atMs }.thenBy { it.step })
        val before = last?.prior?.packageState
        return last?.op is Op.Disable && (before == EnabledState.ENABLED || before == EnabledState.DEFAULT)
    }
}
