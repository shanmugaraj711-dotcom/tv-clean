package ai.promptstudio.tvclean.core.flows

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ai.promptstudio.tvclean.core.engine.StartRefusal
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.TvId
import ai.promptstudio.tvclean.core.ops.Privilege
import ai.promptstudio.tvclean.core.planner.Plan
import ai.promptstudio.tvclean.core.planner.PlanKind
import ai.promptstudio.tvclean.core.store.Clock
import ai.promptstudio.tvclean.core.store.CredentialGate
import ai.promptstudio.tvclean.core.store.GateMethod
import ai.promptstudio.tvclean.core.store.GatePass
import ai.promptstudio.tvclean.core.store.GateResult

// D17: one system credential prompt per TV session, raised when the first write is about to start;
// a passed prompt covers that TV for 10 minutes (the pass carries its own expiry, R0: 6). Every
// machine asks here, so "Make it clean", "Put back", "Add to Mi TV" and the rest share one prompt.
// Phones without a screen lock get a NONE pass and the grey line instead of a prompt.
class GateKeeper(
    private val gate: CredentialGate,
    private val clock: Clock,
    private val copy: FlowCopy,
) {
    private val passes = HashMap<TvId, GatePass>()
    private val lock = Mutex()

    // A cached pass that is still live, without prompting.
    fun cached(tvId: TvId): GatePass? = synchronized(passes) { passes[tvId]?.takeIf { it.expiresAtMs > clock.nowMs() } }

    // The live pass for this TV, prompting once if there is none. Null when the person cancelled.
    suspend fun pass(tvId: TvId, tvName: String): GatePass? = passWithSubtitle(tvId, copy.text(CopyKey.GATE_SUBTITLE, mapOf("tv" to tvName)))

    // App-level actions ("Reset my key", "Delete everything") bring their own subtitle and always
    // prompt. Their APP_GATE_ID pass is never cached (RFC-W8-4 ruling), so nothing can reuse it.
    suspend fun passWithSubtitle(tvId: TvId, subtitle: String, forcePrompt: Boolean = false): GatePass? = lock.withLock {
        if (!forcePrompt) cached(tvId)?.let { return it }
        when (val r = gate.confirm(tvId, copy.text(CopyKey.GATE_TITLE), subtitle)) {
            is GateResult.Passed -> r.pass.also { remember(it) }
            is GateResult.NotAvailable -> r.pass.also { remember(it) }
            GateResult.Failed -> null
        }
    }

    // A pass handed in from outside (SetupIntent.CredentialPassed), kept like a prompted one. An
    // app-level pass is never kept.
    fun remember(pass: GatePass) { if (pass.tvId != APP_GATE_ID) synchronized(passes) { passes[pass.tvId] = pass } }

    fun forget(tvId: TvId) { synchronized(passes) { passes.remove(tvId) } }

    fun clear() { synchronized(passes) { passes.clear() } }

    fun noScreenLock(tvId: TvId): Boolean = cached(tvId)?.method == GateMethod.NONE

    companion object {
        private val GATED = setOf(Privilege.PROFILE_STEP, Privilege.COMMUNITY, Privilege.TAKEOVER)

        // READ and SESSION_END plans (debug_off at Finish) need no pass (R0: 6), and neither does the
        // restart test (RFC-W8-5: a REBOOT plan of Reboot steps only, the engine's own rule, D-W3-5).
        fun needsPass(plan: Plan): Boolean =
            !(plan.kind == PlanKind.REBOOT && plan.steps.all { it.op == Op.Reboot }) && plan.steps.any { it.rendered.privilege in GATED }

        fun isGateRefusal(r: StartRefusal): Boolean = r == StartRefusal.GATE_REQUIRED || r == StartRefusal.GATE_EXPIRED
    }
}
