package ai.promptstudio.tvclean.android.platform

import ai.promptstudio.tvclean.adb.pairing.PairingClient
import ai.promptstudio.tvclean.adb.pairing.PairingCrypto
import ai.promptstudio.tvclean.adb.pairing.PairingException
import ai.promptstudio.tvclean.adb.pairing.TlsIdentity
import ai.promptstudio.tvclean.core.discovery.FoundTv
import ai.promptstudio.tvclean.core.flows.PairingOutcome
import ai.promptstudio.tvclean.core.flows.PairingRunner
import ai.promptstudio.tvclean.core.model.Sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// RFC-PAIRING-1: the app-layer PairingRunner. Runs the ADB TLS pairing client against the TV's
// pairing service (mDNS _adb-tls-pairing._tcp) with the code the TV shows, over the phone's
// own ADB identity: its TLS client certificate and its ADB RSA public key line - the same key
// the TV authorizes when the pairing succeeds. Blocking socket I/O runs on Dispatchers.IO;
// the SetupMachine only ever calls this from a coroutine.
class AndroidPairingRunner(
    private val tlsIdentity: suspend () -> TlsIdentity,
    private val publicKeyLine: suspend () -> String,
) : PairingRunner {

    override suspend fun pair(tv: FoundTv, code: String): PairingOutcome {
        val pairingService = tv.pairingService
            ?: throw PairingException("this TV offers no pairing service")
        val identity = tlsIdentity()
        val line = publicKeyLine()
        val result = withContext(Dispatchers.IO) {
            PairingClient({ identity }).pair(tv.host, pairingService.port, code, line)
        }
        val fingerprint = when (val peer = result.peerIdentity) {
            is PairingCrypto.PeerIdentity.RsaKey -> peer.fingerprint
            is PairingCrypto.PeerIdentity.Guid -> peer.bytes.joinToString("") { "%02x".format(it) }
        }
        return PairingOutcome(
            host = tv.host,
            serverCertSha256 = Sha256(result.serverCertSha256Hex),
            peerFingerprint = fingerprint,
            pairedAtMs = System.currentTimeMillis(),
        )
    }
}
