package ai.promptstudio.tvclean.core.flows

import ai.promptstudio.tvclean.core.model.TvId
import ai.promptstudio.tvclean.core.spec.LauncherConfigDoc
import ai.promptstudio.tvclean.core.spec.LiveTvKeys

// What a Manage push adds to a look's doc on this TV, after the look itself: the Home button
// helper's keys it keeps (HomeHelperFlows.carry), then the TV's Live TV app and no placed app hidden
// (LiveTvKeys), from the newest snapshot and the TV's profile. Without a snapshot the Live TV app is
// not known, so live_tv_package is not written; the hidden list still never hides a placed app.
// Setup does the same from its own snapshot (SetupMachine.replan).
internal object LookCarry {
    suspend fun of(d: ManageDeps, tvId: TvId): (LauncherConfigDoc) -> LauncherConfigDoc {
        val helper = HomeHelperFlows.carry(d, tvId)
        val snap = attempt { d.snapshots.latest(tvId) }.getOrNull()
        val profile = d.tv(tvId)?.let { attempt { d.profileOf(it) }.getOrNull() }
        val liveTv = snap?.let { LiveTvKeys.pick(profile, it.packages) }
        return { doc -> LiveTvKeys.apply(helper(doc), liveTv) }
    }
}
