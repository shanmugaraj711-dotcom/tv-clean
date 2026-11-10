package ai.promptstudio.tvclean.adb.wire

import ai.promptstudio.tvclean.core.model.AdbError
import ai.promptstudio.tvclean.core.model.AdbException
import ai.promptstudio.tvclean.core.model.Sha256

// 0.1 shipped plain 5555 only (D9): a TV that sent STLS got this refusal and the S2x dead
// end. 0.2 ships a real upgrader (AndroidTlsUpgrader in :android:platform) and the app
// wires it in; this object stays as the explicit-refusal implementation — the handshake
// test uses it to prove a TV that demands TLS is refused when no upgrader is fitted.
object NotSupportedTlsUpgrader : TlsUpgrader {
    override suspend fun upgrade(socket: AdbSocket, pin: Sha256?): AdbSocket =
        throw AdbException(AdbError.Transport.Protocol("TLS debugging is not supported in this version"))
}
