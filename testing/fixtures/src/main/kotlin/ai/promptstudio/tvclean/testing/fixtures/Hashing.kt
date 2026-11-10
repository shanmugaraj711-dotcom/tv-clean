package ai.promptstudio.tvclean.testing.fixtures

import ai.promptstudio.tvclean.core.model.Sha256
import java.security.MessageDigest

fun sha256Of(bytes: ByteArray): Sha256 =
    Sha256(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })

fun sha256Of(text: String): Sha256 = sha256Of(text.encodeToByteArray())
