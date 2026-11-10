package ai.promptstudio.tvclean.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ai.promptstudio.tvclean.core.model.GroupDefault
import ai.promptstudio.tvclean.core.model.Level
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageGroup
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.TvId
import ai.promptstudio.tvclean.core.planner.PlanRequest
import ai.promptstudio.tvclean.core.planner.PutBackAdvice
import ai.promptstudio.tvclean.core.spec.ProfileCategory

// M4 "Switched off (23)": rows by group with "Put back" (one op, 5 s undo snackbar, UX 4.13); the
// Custom level adds the profile's `ask` groups with Off / Kept pills (D2). In a build whose updaters
// always stay on (StoreRules.updatesOff false, the Google Play build) an updater gets no pill and
// putting one back offers no Undo, so "updates go off" is never offered (RF-STORES-030 M2).
//
// "Better back on" (WP4, C6): a group at the top for what this app switched off that the list for
// this TV now keeps on (PutBackAdvice). Each row carries our `reason`; its button is the ordinary
// PutBack. Nothing runs until the person taps.
data class OffRow(val pkg: PackageName, val name: String, val description: String?, val off: Boolean, val reason: String? = null)
data class OffGroup(val id: String, val title: String, val rows: List<OffRow>) {
    companion object {
        const val BETTER_BACK_ON = "better_back_on"
        const val BETTER_BACK_ON_TITLE = "Better back on"
    }
}

data class SwitchedOffState(
    val tvName: String = "",
    val level: Level = Level.SIMPLE,
    val groups: List<OffGroup> = emptyList(),
    val askGroups: List<OffGroup> = emptyList(),
    val busy: PackageName? = null,
    val loading: Boolean = true,
    val count: Int = 0,
)

sealed interface SwitchedOffIntent {
    data class PutBack(val pkg: PackageName) : SwitchedOffIntent
    data class Undo(val pkg: PackageName) : SwitchedOffIntent           // the snackbar's Undo: switch it off again
    data class SetOff(val pkg: PackageName, val off: Boolean) : SwitchedOffIntent   // Custom: the ask groups' pills
    data object Refresh : SwitchedOffIntent
}

class SwitchedOffMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    private val tvId: TvId,
) : Machine<SwitchedOffState, SwitchedOffIntent, ManageEffect>(SwitchedOffState(), scope) {

    private val writer = TvWriter(d)
    private data class Loaded(val s: SwitchedOffState)
    private data class BusyOn(val pkg: PackageName?)
    private var profileGroups: List<PackageGroup> = emptyList()
    // The "Better back on" rows (WP4, G4): their put back offers no Undo, which would switch off
    // again what the list for this TV now keeps on.
    private var advised: Set<PackageName> = emptySet()

    // An updater this build keeps on: no Off pill, no Undo after Put back.
    private fun keptOn(pkg: PackageName): Boolean = !d.store.updatesOff && ProfileCategory.isUpdater(pkg, profileGroups)

    init { scope.launch { load() } }

    override fun reduce(state: SwitchedOffState, event: Any): SwitchedOffState = when (event) {
        is Loaded -> event.s.copy(busy = state.busy)
        is BusyOn -> state.copy(busy = event.pkg)
        else -> state
    }

    override fun handle(event: Any, before: SwitchedOffState, after: SwitchedOffState) {
        if (after.busy != null && event is SwitchedOffIntent && event !is SwitchedOffIntent.Refresh) return
        when (event) {
            is SwitchedOffIntent.PutBack -> change(event.pkg, PlanRequest.PutBack(event.pkg), SnackKind.PUT_BACK, undo = !keptOn(event.pkg) && event.pkg !in advised)
            is SwitchedOffIntent.Undo -> if (!keptOn(event.pkg)) change(event.pkg, PlanRequest.SwitchOff(setOf(event.pkg)), SnackKind.SWITCHED_OFF, undo = false)
            is SwitchedOffIntent.SetOff -> if (event.off) { if (!keptOn(event.pkg)) change(event.pkg, PlanRequest.SwitchOff(setOf(event.pkg)), SnackKind.SWITCHED_OFF, undo = false) }
                else change(event.pkg, PlanRequest.PutBack(event.pkg), SnackKind.PUT_BACK, undo = false)
            SwitchedOffIntent.Refresh -> scope.launch { load() }
            else -> {}
        }
    }

    private fun change(pkg: PackageName, request: PlanRequest, kind: SnackKind, undo: Boolean) {
        scope.launch {
            send(BusyOn(pkg))
            val r = writer.write(tvId, request)
            send(BusyOn(null))
            load()
            when (r) {
                // A switch-off the run skipped changed nothing, so there is nothing to undo:
                // RF-LAUNCHER-250 F1, kept because it runs the TV's inputs; otherwise the TV kept it
                // on (issue #10) and the line is Done's "wouldn't switch off; it's harmless".
                is WriteResult.Done -> effect(when {
                    kind != SnackKind.SWITCHED_OFF || r.finished.skipped.none { (it.op as? Op.Disable)?.pkg == pkg } ->
                        ManageEffect.Snackbar(kind, d.nameOf(pkg), undo, pkg)
                    d.ledger.keptTvInputs(r.plan.id).isNotEmpty() -> ManageEffect.Snackbar(SnackKind.KEPT_TV_INPUT, d.nameOf(pkg), false)
                    else -> ManageEffect.Snackbar(SnackKind.KEPT_ON, d.nameOf(pkg), false)
                })
                WriteResult.GateCancelled, WriteResult.NoTv, is WriteResult.Paused -> {}   // Paused: the open-run card shows (O2)
                else -> effect(ManageEffect.Snackbar(SnackKind.FAILED, d.nameOf(pkg), false))
            }
        }
    }

    private suspend fun load() {
        val tv = d.tv(tvId) ?: return
        val off = d.ledger.switchedOffByUs(tvId)
        val profile = d.profileOf(tv)
        val latest = d.snapshots.latest(tvId)
        val labels = latest?.packages.orEmpty()
        fun row(p: PackageName, isOff: Boolean) = OffRow(p, d.nameOf(p, labels[p]?.label), d.facts.lookup(p)?.description, isOff)
        val groups = profile?.groups.orEmpty()
        profileGroups = groups
        val placed = HashSet<PackageName>()
        // Only what this list shows as switched off by us, and PutBackAdvice's own rule on top.
        val advice = PutBackAdvice.offers(tv.profileId, latest, d.ledger.changes(tvId), profile).filter { it.pkg in off }
        val better = advice.distinctBy { it.pkg }.map { e -> row(e.pkg, true).copy(reason = e.reason) }
        val atTop = better.map { it.pkg }.toSet()
        advised = atTop
        placed += atTop
        val byGroup = groups.mapNotNull { g ->
            val rows = g.packages.map { it.name }.filter { it in off && it !in atTop }.map { placed += it; row(it, true) }
            if (rows.isEmpty()) null else OffGroup(g.id, g.title, rows)
        }
        val rest = off.filterNot { it in placed }.map { row(it, true) }
        val top = if (better.isEmpty()) null else OffGroup(OffGroup.BETTER_BACK_ON, OffGroup.BETTER_BACK_ON_TITLE, better)
        val all = listOfNotNull(top) + byGroup + listOfNotNull(if (rest.isEmpty()) null else OffGroup("other", "", rest))
        val ask = if (tv.level == Level.CUSTOM) groups.filter { it.default == GroupDefault.ASK }.map { g ->
            OffGroup(g.id, g.title, g.packages.filter { labels.containsKey(it.name) && !keptOn(it.name) }.map { row(it.name, it.name in off) })
        }.filter { it.rows.isNotEmpty() } else emptyList()
        send(Loaded(SwitchedOffState(tv.nickname, tv.level, all, ask, null, loading = false, count = off.size)))
    }
}
