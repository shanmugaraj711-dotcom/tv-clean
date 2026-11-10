package ai.promptstudio.tvclean.core.flows

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import ai.promptstudio.tvclean.core.engine.Engine
import ai.promptstudio.tvclean.core.engine.RunState
import ai.promptstudio.tvclean.core.engine.StartRefusal
import ai.promptstudio.tvclean.core.engine.StartResult
import ai.promptstudio.tvclean.core.model.Snapshot
import ai.promptstudio.tvclean.core.model.SnapshotReason
import ai.promptstudio.tvclean.core.model.Tv
import ai.promptstudio.tvclean.core.planner.Plan
import ai.promptstudio.tvclean.core.planner.PlanRequest
import ai.promptstudio.tvclean.core.planner.PlanResult
import ai.promptstudio.tvclean.core.planner.Planner
import ai.promptstudio.tvclean.core.probe.Prober
import ai.promptstudio.tvclean.core.session.TvSession
import ai.promptstudio.tvclean.core.spec.FactsTable
import ai.promptstudio.tvclean.core.store.GatePass
import ai.promptstudio.tvclean.core.store.SnapshotStore

sealed interface RunOutcome {
    data class Finished(val state: RunState.Finished) : RunOutcome
    data class Paused(val state: RunState.Paused) : RunOutcome
    data class StartRefused(val refusal: StartRefusal) : RunOutcome
    data object GateCancelled : RunOutcome
}

fun RunState.planOrNull(): Plan? = when (this) {
    is RunState.Running -> plan
    is RunState.AwaitingConfirm -> plan
    is RunState.Paused -> plan
    is RunState.RollingBack -> plan
    is RunState.InDoubt -> plan
    is RunState.Finished -> plan
    RunState.Idle -> null
}

// Plan, gate, start, watch: the shape every write outside the wizard has (Put back, Switch off,
// leftovers, theme, modules, undo everything). The pass comes from the GateKeeper only when the
// plan has a gated step (D17); a refused gate is prompted once more; the run then lives in the
// engine's scope and this call follows runState until that plan finishes or pauses.
class PlanRunner(
    private val planner: Planner,
    private val engine: Engine,
    private val gate: GateKeeper,
    private val ledger: RunLedger,
    private val facts: FactsTable,
    private val snapshots: SnapshotStore,
    private val prober: Prober,
) {
    // The newest stored snapshot, or a fresh MANUAL one when this TV has none yet.
    suspend fun snapshotFor(tv: Tv, session: TvSession): Snapshot =
        snapshots.latest(tv.id) ?: prober.snapshot(session, SnapshotReason.MANUAL).also { snapshots.save(it) }

    // A fresh snapshot, for re-planning after the TV changed under a run (STALE) or after a run.
    suspend fun freshSnapshot(session: TvSession): Snapshot =
        prober.snapshot(session, SnapshotReason.MANUAL).also { snapshots.save(it) }

    // A read of the TV that is not stored: a check, not a new base for plans (SessionEnd reads adb_enabled back).
    suspend fun readSnapshot(session: TvSession): Snapshot = prober.snapshot(session, SnapshotReason.MANUAL)

    suspend fun plan(tv: Tv, request: PlanRequest, snapshot: Snapshot): PlanResult =
        planner.plan(request, snapshot, facts, ledger.changes(tv.id))

    // `onStarted` runs once the engine accepted the plan, before following it: the point from which a
    // write may have reached the TV (SessionEnd only switches debugging off on TVs a write touched).
    suspend fun run(tv: Tv, plan: Plan, session: TvSession, given: GatePass? = null, onStarted: () -> Unit = {}, onState: (RunState) -> Unit = {}): RunOutcome {
        var pass = given
        if (pass == null && GateKeeper.needsPass(plan)) pass = gate.pass(tv.id, tv.nickname) ?: return RunOutcome.GateCancelled
        var result = engine.start(plan, session, plan.hash, pass)
        if (result is StartResult.Refused && GateKeeper.isGateRefusal(result.reason)) {
            gate.forget(tv.id)
            pass = gate.pass(tv.id, tv.nickname) ?: return RunOutcome.GateCancelled
            result = engine.start(plan, session, plan.hash, pass)
        }
        if (result is StartResult.Refused) return RunOutcome.StartRefused(result.reason)
        ledger.record(plan)
        onStarted()
        return follow(tv, plan, onState = onState)
    }

    // Follows a run to its end or pause. After continueRun or undoRun the flow still holds the
    // old Paused state, so `resumed` skips states until the run has moved again.
    suspend fun follow(tv: Tv, plan: Plan, resumed: Boolean = false, onState: (RunState) -> Unit = {}): RunOutcome {
        var moving = !resumed
        val end = engine.runState(tv.id).onEach { if (it.planOrNull()?.id == plan.id) onState(it) }
            .first {
                val mine = it.planOrNull()?.id == plan.id
                if (mine && it !is RunState.Paused) moving = true
                moving && mine && (it is RunState.Finished || it is RunState.Paused)
            }
        return when (end) {
            is RunState.Finished -> RunOutcome.Finished(end)
            is RunState.Paused -> RunOutcome.Paused(end)
            else -> error("unreachable")
        }
    }
}
