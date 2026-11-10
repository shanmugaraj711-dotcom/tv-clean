package ai.promptstudio.tvclean.core.flows

import ai.promptstudio.tvclean.core.discovery.FoundTv
import ai.promptstudio.tvclean.core.model.Sha256

// RFC-PAIRING-1: the thin edge between Setup and the ADB TLS pairing client. `:core:flows`
// must not know the pairing transport (SPAKE2, Bouncy Castle TLS), so it only sees a runner
// that pairs a found TV with the code the TV shows and reports what the trust record needs.
// The real runner lives in the app layer and adapts the pairing client; a null runner in
// SetupDeps keeps the old behaviour (a pairing-only TV stays on the PairingNeeded screen).
// The outcome's `host` is the TV that was paired, so a stale result never applies to another TV.
data class PairingOutcome(
    val host: String,
    val serverCertSha256: Sha256,
    val peerFingerprint: String,
    val pairedAtMs: Long,
)

fun interface PairingRunner {
    /**
     * Pair with [tv] using the 6-digit [code] the TV shows on its wireless debugging screen.
     * Throws on a wrong code, an unreachable pairing port, or a TLS failure; the machine
     * turns the failure into plain words for the PairingNeeded screen.
     */
    suspend fun pair(tv: FoundTv, code: String): PairingOutcome
}
