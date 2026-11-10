package ai.promptstudio.tvclean.core.flows

import ai.promptstudio.tvclean.core.spec.BandChoice
import ai.promptstudio.tvclean.core.spec.BundledThemeSource
import ai.promptstudio.tvclean.core.spec.ButtonsChoice
import ai.promptstudio.tvclean.core.spec.GlowChoice
import ai.promptstudio.tvclean.core.spec.LauncherConfigCodec
import ai.promptstudio.tvclean.core.spec.Movement
import ai.promptstudio.tvclean.core.spec.StyleId
import ai.promptstudio.tvclean.testing.fixtures.FakeClock
import ai.promptstudio.tvclean.testing.fixtures.Samples
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

// The flows side of the byte proof (0.4.5): a look2 choice saved by 0.4.0, sent again through the
// drafts (LookDrafts.docOf, as Restore mine, a dock change and the cards push send it) to a TV on
// home screen 3.0 or of unknown version, with no extras, gives the bytes 0.4.0 sent. The digests in
// look2-drafts-0.4.0.sha256 were taken on unmodified e42aaf2: one over the bytes of every input in
// input order, one over the sorted distinct bytes. Look2BytesTest covers LookKeys.build alone.
class LookDraftBytesTest {
    private val looks = BundledThemeSource()
    private val drafts = LookDrafts(looks, looks, ConfigEncoder.Codec, FakeClock(Samples.NOW_MS))

    // Choices across every style, picture, apps in a row, dock size and a spread of details and
    // movement, on three TVs that read the 3.0 keys (versionCode 9, not known, and 9 with 1 GB).
    private fun inputs(): Sequence<Pair<String, ByteArray>> = sequence {
        val pictures = listOf(LookPicture.NONE_ID, LookPicture.MINE_ID) + looks.pictures().map { it.id }
        val details = buildList {
            for (glow in listOf(null) + GlowChoice.entries) for (band in listOf<Pair<BandChoice?, Int?>>(null to null, BandChoice.SOLID to null,
                BandChoice.SEE_THROUGH to 30, BandChoice.SEE_THROUGH to 70, BandChoice.GLASS to null)) for (buttons in listOf(null) + ButtonsChoice.entries)
                add(LookDetails(glow = glow, band = band.first, seeThrough = band.second, buttons = buttons))
            add(LookDetails(bigName = false, ring = true, greenWifi = true, spotColour = false))
            add(LookDetails(dim = 40, sky = true, skyStrength = 85))
            add(LookDetails(sky = false, bigName = true, ring = false))
        }
        val moves = listOf(Triple(Movement.LIVELY.id, false, false), Triple(Movement.QUICK.id, true, true), Triple(null, true, false),
            Triple(Movement.OFF.id, false, true))
        val tvs = listOf(LookTv(9L, 2048, true), LookTv(), LookTv(9L, 1024, false))
        for (tv in tvs) for (style in StyleId.entries) for (picture in pictures) for (columns in listOf(4, 6, 8)) for (n in listOf(4, 15))
            for (d in details) for ((movement, lift, names) in moves) {
                val dock = LegacyCases.fifteen.take(n)
                val choice = HomeChoice(dock, null, cards = n == 15, picture = picture, dim = if (picture == LookPicture.MINE_ID) 30 else null,
                    columns = columns, style = style.id, movement = movement, lift = lift, names = names, details = d)
                val (doc, _) = drafts.docOf(choice, dock, cards = n == 15, mine = LegacyCases.mine, tv = tv)
                yield("$tv $style $picture $columns $n $d $movement $lift $names" to LauncherConfigCodec.encode(doc))
            }
    }

    private fun MessageDigest.add(bytes: ByteArray) {
        update(byteArrayOf((bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte()))
        update(bytes)
    }
    private fun hex(md: MessageDigest) = md.digest().joinToString("") { "%02x".format(it) }

    @Test fun aSavedLook2ChoiceSendsThe040BytesWithDefaultExtras() {
        val ordered = MessageDigest.getInstance("SHA-256")
        val distinct = HashSet<String>()
        var n = 0
        for ((_, bytes) in inputs()) {
            ordered.add(bytes)
            distinct += bytes.decodeToString()
            n++
        }
        val sorted = MessageDigest.getInstance("SHA-256")
        for (t in distinct.sorted()) sorted.add(t.encodeToByteArray())
        val got = mapOf("in-order" to hex(ordered), "distinct" to hex(sorted))
        println("look2 drafts bytes: $n inputs, ${distinct.size} distinct docs, $got")
        val want = javaClass.getResource("/look2-drafts-0.4.0.sha256")?.readText().orEmpty().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.associate { it.substringAfter("  ").substringBefore(' ') to it.substringBefore(' ') }
        assertEquals(want, got, "the drafts' bytes moved from 0.4.0 ($n inputs, ${distinct.size} distinct docs)")
    }
}
