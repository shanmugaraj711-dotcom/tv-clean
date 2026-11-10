package ai.promptstudio.tvclean.testing.fixtures

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ai.promptstudio.tvclean.core.engine.Engine
import ai.promptstudio.tvclean.core.engine.HomeOutcome
import ai.promptstudio.tvclean.core.engine.Journal
import ai.promptstudio.tvclean.core.engine.JournalRecord
import ai.promptstudio.tvclean.core.engine.NarrationLine
import ai.promptstudio.tvclean.core.engine.NarrationState
import ai.promptstudio.tvclean.core.engine.RunState
import ai.promptstudio.tvclean.core.engine.RunStatus
import ai.promptstudio.tvclean.core.engine.StartRefusal
import ai.promptstudio.tvclean.core.engine.StartResult
import ai.promptstudio.tvclean.core.model.ConnectionState
import ai.promptstudio.tvclean.core.model.PlanId
import ai.promptstudio.tvclean.core.model.Sha256
import ai.promptstudio.tvclean.core.model.TvId
import ai.promptstudio.tvclean.core.ops.OpRenderer
import ai.promptstudio.tvclean.core.ops.Privilege
import ai.promptstudio.tvclean.core.planner.Plan
import ai.promptstudio.tvclean.core.session.TvSession
import ai.promptstudio.tvclean.core.store.Clock
import ai.promptstudio.tvclean.core.store.GatePass
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// Journal in memory with the real read rule (records in append order) and a crash knob:
// crashAfter = n lets n more appends through and then throws, as if the process died mid-run.
class MemoryJournal : Journal {
    private val records = ConcurrentHashMap<PlanId, CopyOnWriteArrayList<JournalRecord>>()
    private val tvOf = ConcurrentHashMap<PlanId, TvId>()
    @Volatile var crashAfter: Int? = null

    override suspend fun append(record: JournalRecord) {
        crashAfter?.let { if (it <= 0) throw IOException("fake journal: crashed") else crashAfter = it - 1 }
        if (record is JournalRecord.PlanStart) tvOf[record.planId] = record.tvId
        records.computeIfAbsent(record.planId) { CopyOnWriteArrayList() } += record
    }

    override suspend fun read(planId: PlanId): List<JournalRecord> = records[planId]?.toList().orEmpty()

    override suspend fun openRuns(tvId: TvId): List<PlanId> = records.entries
        .filter { (id, recs) -> tvOf[id] == tvId && recs.none { it is JournalRecord.End } }
        .map { it.key }.sortedBy { it.value }
}

// Engine stand-in with the 5.9 start contract (refusal order RUN_OPEN, NOT_CONNECTED, WRONG_TV,
// HASH_MISMATCH, GATE_REQUIRED, GATE_EXPIRED) and scripted progress: after Started it runs onStart,
// which by default finishes the run COMPLETED. Tests drive states with emit() and finish().
// With a renderer it also re-renders every step and compares lines and planHash, like W3's engine.
class FakeEngine(
    private val clock: Clock = FakeClock(),
    private val renderer: OpRenderer? = null,
) : Engine {
    sealed interface Call {
        data class Start(val plan: Plan, val confirmed: Sha256, val pass: GatePass?, val result: StartResult) : Call
        data class Resume(val tvId: TvId) : Call
        data class ConfirmHome(val tvId: TvId) : Call
        data class RejectHome(val tvId: TvId) : Call
        data class Stop(val tvId: TvId) : Call
        data class ContinueRun(val tvId: TvId, val pass: GatePass?, val refused: StartRefusal?) : Call
        data class UndoRun(val tvId: TvId, val pass: GatePass?, val refused: StartRefusal?) : Call
        data class Abandon(val tvId: TvId) : Call
    }

    private val states = ConcurrentHashMap<TvId, MutableStateFlow<RunState>>()
    val calls = CopyOnWriteArrayList<Call>()
    var onStart: suspend FakeEngine.(Plan) -> Unit = { finish(it, RunStatus.COMPLETED) }
    // What abandon does after it is recorded; by default the run ends ABANDONED. A test sets it to
    // leave the run paused, as DefaultEngine does when step R cannot put the TV's own home back.
    var onAbandon: suspend FakeEngine.(TvId) -> Unit = { id -> planOf(flow(id).value)?.let { finish(it, RunStatus.ABANDONED) } }

    private fun flow(tvId: TvId) = states.computeIfAbsent(tvId) { MutableStateFlow(RunState.Idle) }
    override fun runState(tvId: TvId): StateFlow<RunState> = flow(tvId).asStateFlow()

    fun emit(tvId: TvId, state: RunState) { flow(tvId).value = state }
    fun finish(plan: Plan, status: RunStatus, home: HomeOutcome? = null) {
        emit(plan.tvId, RunState.Finished(plan, status, emptyList(), home))
    }

    private fun planOf(state: RunState): Plan? = when (state) {
        is RunState.Running -> state.plan
        is RunState.AwaitingConfirm -> state.plan
        is RunState.Paused -> state.plan
        is RunState.RollingBack -> state.plan
        is RunState.InDoubt -> state.plan
        is RunState.Finished -> state.plan
        RunState.Idle -> null
    }

    private fun gate(plan: Plan, pass: GatePass?): StartRefusal? {
        val gated = plan.steps.any { it.rendered.privilege in GATED } &&
            !(plan.kind == ai.promptstudio.tvclean.core.planner.PlanKind.REBOOT && plan.steps.all { it.op == ai.promptstudio.tvclean.core.model.Op.Reboot })   // as DefaultEngine (RFC-W8-5)
        return when {
            !gated -> null
            pass == null || pass.tvId != plan.tvId -> StartRefusal.GATE_REQUIRED
            pass.expiresAtMs <= clock.nowMs() -> StartRefusal.GATE_EXPIRED
            else -> null
        }
    }

    private fun hashOk(plan: Plan, confirmed: Sha256): Boolean {
        if (confirmed != plan.hash) return false
        val r = renderer ?: return true
        val again = plan.steps.map { r.render(it.op) }
        val sameLines = again.zip(plan.steps).all { (a, s) -> a.lines.map { it.text } == s.rendered.lines.map { it.text } }
        return sameLines && r.planHash(again) == plan.hash
    }

    override suspend fun start(plan: Plan, session: TvSession, confirmed: Sha256, pass: GatePass?): StartResult {
        val current = flow(plan.tvId).value
        val refusal = when {
            current !is RunState.Idle && current !is RunState.Finished -> StartRefusal.RUN_OPEN
            session.state.value !is ConnectionState.Connected -> StartRefusal.NOT_CONNECTED
            plan.tvId != session.tvId -> StartRefusal.WRONG_TV
            !hashOk(plan, confirmed) -> StartRefusal.HASH_MISMATCH
            else -> gate(plan, pass)
        }
        val result = refusal?.let { StartResult.Refused(it) } ?: StartResult.Started
        calls += Call.Start(plan, confirmed, pass, result)
        if (refusal == null) {
            emit(plan.tvId, RunState.Running(plan, 0, plan.steps.map { NarrationLine(it.narration, NarrationState.UPCOMING) }, 0, null))
            onStart(plan)
        }
        return result
    }

    override suspend fun resume(tvId: TvId, session: TvSession) { calls += Call.Resume(tvId) }
    override suspend fun confirmHome(tvId: TvId) { calls += Call.ConfirmHome(tvId) }
    override suspend fun rejectHome(tvId: TvId) { calls += Call.RejectHome(tvId) }
    override suspend fun stop(tvId: TvId) { calls += Call.Stop(tvId) }

    override suspend fun continueRun(tvId: TvId, pass: GatePass?) {
        calls += Call.ContinueRun(tvId, pass, planOf(flow(tvId).value)?.let { gate(it, pass) })
    }

    override suspend fun undoRun(tvId: TvId, pass: GatePass?) {
        calls += Call.UndoRun(tvId, pass, planOf(flow(tvId).value)?.let { gate(it, pass) })
    }

    override suspend fun abandon(tvId: TvId) {
        calls += Call.Abandon(tvId)
        onAbandon(tvId)
    }

    companion object {
        val GATED = setOf(Privilege.PROFILE_STEP, Privilege.COMMUNITY, Privilege.TAKEOVER)
    }
}
