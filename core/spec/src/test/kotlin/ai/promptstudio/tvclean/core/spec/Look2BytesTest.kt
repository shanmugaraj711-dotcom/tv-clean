package ai.promptstudio.tvclean.core.spec

import org.junit.Test
import ai.promptstudio.tvclean.core.model.PackageName
import java.security.MessageDigest
import kotlin.test.assertEquals

// The byte proof of 0.4.5 (WP5 step 1): every look doc LookKeys.build makes in the
// LookDifferentialTest cross product, encoded by the codec, deduplicated, sorted, and hashed.
// The digest in look2-bytes-0.4.0.sha256 was taken on unmodified 0.4.0 (e42aaf2), so any later
// change that moves a single byte of a look a person could already send fails here. New choices
// at their defaults must leave these bytes alone.
class Look2BytesTest {
    private val dock4 = listOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar")
        .map(::PackageName)
    private fun dock(n: Int) = (dock4 + List(20) { PackageName("com.example.app$it") }).take(n)

    // The same inputs as everyLookDocAgreesWithLauncher27, in the same order, with no extras passed.
    // Also hashes every input's bytes in input order, so a change that only swaps two docs between
    // inputs shows too (look2-bytes-0.4.0-in-order.sha256, taken on e42aaf2 like the other).
    private var inOrder = ""
    private fun distinctBytes(): Pair<Int, List<String>> {
        val texts = HashSet<String>()
        val ordered = MessageDigest.getInstance("SHA-256")
        fun add(bytes: ByteArray) {
            ordered.update(byteArrayOf((bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte()))
            ordered.update(bytes)
            texts += bytes.decodeToString()
        }
        var built = 0
        for ((_, picture) in LookInputs.pictures) {
            for (spec in LookInputs.cross()) {
                add(LauncherConfigCodec.encode(LookKeys.build(spec, picture, columns = 5, dock = dock4, cards = false)))
                built++
            }
            for (style in StyleId.entries) for (columns in 4..8) for (n in listOf(0, 4, 15, 24)) {
                add(LauncherConfigCodec.encode(LookKeys.build(LookKeys.spec(style), picture, columns, dock(n), cards = true)))
                built++
            }
        }
        inOrder = ordered.digest().joinToString("") { "%02x".format(it) }
        return built to texts.sorted()
    }

    // Each doc goes in as a 4 byte big endian length and then its bytes, so no two lists of docs
    // can hash the same input.
    private fun digest(docs: List<String>): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (text in docs) {
            val bytes = text.encodeToByteArray()
            md.update(byteArrayOf((bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte()))
            md.update(bytes)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @Test fun look2BytesUnchangedWithDefaultExtras() {
        val (built, docs) = distinctBytes()
        val got = digest(docs)
        val file = javaClass.getResource("/look2-bytes-0.4.0.sha256")?.readText()
        val want = file?.lineSequence()?.first { it.isNotBlank() && !it.startsWith("#") }?.substringBefore(' ')
        println("look2 bytes: $built builds, ${docs.size} distinct docs, sha256 $got, in input order $inOrder")
        assertEquals(want, got, "the look2 bytes moved from 0.4.0 ($built builds, ${docs.size} distinct docs)")
        val ordered = javaClass.getResource("/look2-bytes-0.4.0-in-order.sha256")?.readText()
            ?.lineSequence()?.first { it.isNotBlank() && !it.startsWith("#") }?.substringBefore(' ')
        assertEquals(ordered, inOrder, "the look2 bytes moved from 0.4.0 for some input, in input order")
    }
}
