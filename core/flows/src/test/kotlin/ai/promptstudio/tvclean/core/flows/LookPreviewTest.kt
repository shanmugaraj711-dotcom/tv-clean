package ai.promptstudio.tvclean.core.flows

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.spec.AppColours
import ai.promptstudio.tvclean.core.spec.BandChoice
import ai.promptstudio.tvclean.core.spec.ButtonsChoice
import ai.promptstudio.tvclean.core.spec.DecodeResult
import ai.promptstudio.tvclean.core.spec.GlowChoice
import ai.promptstudio.tvclean.core.spec.GlowStyle
import ai.promptstudio.tvclean.core.spec.LauncherConfigCodec
import ai.promptstudio.tvclean.core.spec.LauncherConfigDoc
import ai.promptstudio.tvclean.core.spec.LauncherKeys
import ai.promptstudio.tvclean.core.spec.LookKeys
import ai.promptstudio.tvclean.core.spec.LookSpec
import ai.promptstudio.tvclean.core.spec.Movement
import ai.promptstudio.tvclean.core.spec.PictureKeys
import ai.promptstudio.tvclean.core.spec.StyleId
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// look2: the preview is read back from the bytes the push sends, never from the choice. Each
// carried 2.7 key the preview draws reads as LauncherKeys.look reads it, and nothing in a
// LookPreview comes from anywhere but the bytes and the app lists it is given.
class LookPreviewTest {
    private fun app(pkg: String, name: String) = AppChoice(PackageName(pkg), name)
    private val dock4 = listOf(
        app("com.netflix.ninja", "Netflix"), app("com.google.android.youtube.tv", "YouTube"),
        app("com.amazon.amazonvideo.livingroom", "Prime Video"), app("in.startv.hotstar", "JioHotstar"),
    )
    private val others = listOf(app("com.spotify.tv.android", "Spotify"), app("com.plexapp.android", "Plex"), app("org.videolan.vlc", "VLC"))
    private val today = LocalDate.of(2026, 10, 26)
    private val evening = 20 * 60 + 30

    private val none = PictureKeys(wallpaper = false, backgroundColor = "#000000", dim = 0, skyIntensity = 0)
    private val mist = PictureKeys(wallpaper = true, backgroundColor = "#0E1022", dim = 0, skyIntensity = 0)
    private val mountains = PictureKeys(wallpaper = true, backgroundColor = "#000000", dim = 20, skyIntensity = 55)
    private val diwali = PictureKeys(wallpaper = true, backgroundColor = "#120809", dim = 0, skyIntensity = 0,
        festival = "diwali", festivalUntil = "2026-11-10", accentColor = "#FFB23C")

    private fun bytes(spec: LookSpec, picture: PictureKeys = mist, columns: Int = 5, dock: List<AppChoice> = dock4) =
        LauncherConfigCodec.encode(LookKeys.build(spec, picture, columns, dock.map { it.pkg }, cards = true))

    private fun preview(bytes: ByteArray, dock: List<AppChoice> = dock4, others: List<AppChoice> = this.others, inputs: Boolean = true,
        day: LocalDate = today, minute: Int = evening) = LookPreview.of(bytes, null, dock, others, day, minute, inputs)

    private fun decoded(bytes: ByteArray) = (LauncherConfigCodec.decode(bytes) as DecodeResult.Ok).doc

    // The preview's look2 fields as LauncherKeys.look and the codec read the same bytes.
    private fun assertReadsAsTheTv(bytes: ByteArray, what: String) {
        val doc = decoded(bytes)
        val k = LauncherKeys.look(doc)
        val e = LauncherConfigCodec.effective(doc, LauncherConfigCodec.builtInDefaults)
        val p = preview(bytes)
        assertEquals(k.alwaysShowNames, p.names, "$what names")
        assertEquals(if (!k.ambientGlow) PreviewGlow.NONE else if (k.glowStyle == GlowStyle.HORIZON) PreviewGlow.HORIZON else PreviewGlow.RADIAL, p.glow, "$what glow")
        assertEquals(k.appGlow || e.cinematicHome && !k.cinematicWhiteFocus, p.ring, "$what ring")
        assertEquals(k.dockOpacity, p.bandOpacity, "$what dock_opacity")
        assertEquals(k.dockEvenPadding, p.evenPadding, "$what dock_even_padding")
        assertEquals(
            when { !e.dockRow -> PreviewBand.NONE; k.dockGlass -> PreviewBand.GLASS; e.cinematicHome || k.dockOpacity < 100 -> PreviewBand.SEE_THROUGH; else -> PreviewBand.SOLID },
            p.band, "$what band",
        )
        assertEquals(if (k.topMenu) PreviewButtons.MENU else if (k.iconOnlyButtons) PreviewButtons.ROUND else PreviewButtons.LABELLED, p.buttons, "$what buttons")
        assertEquals(k.iconOnlyInputs, p.roundInputs, "$what icon_only_inputs")
        assertEquals(k.wifiStatusColor, p.wifiGreen, "$what wifi_status_color")
        assertEquals(k.cinematicDots, p.dots, "$what cinematic_dots")
        assertEquals(k.cinematicScrim, p.scrim, "$what cinematic_scrim")
        assertEquals(k.cinematicArt, p.art, "$what cinematic_art")
        assertEquals(
            when { !e.animations -> Movement.OFF; e.reducedMotion -> Movement.QUICK; k.focusOvershoot -> Movement.LIVELY; else -> Movement.SMOOTH },
            p.movement, "$what movement",
        )
        assertEquals(e.animations && e.advancedEffects, p.lift, "$what lift")
        assertEquals(e.columns, p.columns, "$what columns")
        assertEquals(e.dockRow, p.dockRow, "$what dock_row")
        assertEquals(e.dockTitle, p.dockTitle, "$what dock_title")
        assertEquals(e.cinematicHome, p.cinematic, "$what cinematic_home")
        assertEquals(e.wallpaperDim, p.dim, "$what wallpaper_dim")
        assertEquals(e.skyIntensity, p.sky, "$what sky_intensity")
        assertEquals(e.backgroundColorArgb, p.backgroundArgb, "$what background_color")
    }

    @Test fun everyStyleAndDetailReadsAsTheTvReadsTheBytes() {
        var n = 0
        for (style in StyleId.entries) for (picture in listOf(none, mist, mountains, diwali)) {
            val own = LookKeys.own(style)
            val variants = listOf(own) +
                GlowChoice.entries.map { own.copy(glow = it) } +
                BandChoice.entries.map { own.copy(band = it) } +
                ButtonsChoice.entries.map { own.copy(buttons = it) } +
                listOf(own.copy(ring = !own.ring), own.copy(greenWifi = !own.greenWifi), own.copy(spotColour = !own.spotColour), own.copy(bigName = !own.bigName))
            for (d in variants) for (see in LookKeys.SEE_THROUGH) for (m in Movement.entries) {
                val spec = LookKeys.spec(style, d, see, names = m == Movement.SMOOTH, movement = m, lift = m != Movement.QUICK)
                assertReadsAsTheTv(bytes(spec, picture), "$style $picture $d $see $m")
                n++
            }
        }
        assertTrue(n > 1000, "$n looks")
    }

    // One carried key at a time, written over Classic's bytes as anything could write it: the
    // preview follows the reader, including its defaults for a wrong type or an out of range value.
    @Test fun eachCarriedKeyAloneChangesWhatThePreviewReads() {
        val classic = decoded(bytes(LookKeys.spec(StyleId.CLASSIC)))
        val spot = decoded(bytes(LookKeys.spec(StyleId.SPOTLIGHT)))
        fun with(doc: LauncherConfigDoc, key: String, v: JsonPrimitive) = LauncherConfigCodec.encode(doc.copy(unknown = doc.unknown + (key to v)))
        val cases = listOf(
            Triple(classic, "always_show_names", JsonPrimitive(true)),
            Triple(classic, "ambient_glow", JsonPrimitive(false)),
            Triple(classic, "glow_style", JsonPrimitive(" HORIZON ")),
            Triple(classic, "glow_style", JsonPrimitive("sideways")),
            Triple(classic, "app_glow", JsonPrimitive(true)),
            Triple(classic, "app_glow", JsonPrimitive("true")),
            Triple(classic, "dock_glass", JsonPrimitive(true)),
            Triple(classic, "dock_opacity", JsonPrimitive(40)),
            Triple(classic, "dock_opacity", JsonPrimitive(29)),
            Triple(classic, "dock_opacity", JsonPrimitive(45.7)),
            Triple(classic, "dock_even_padding", JsonPrimitive(true)),
            Triple(classic, "icon_only_buttons", JsonPrimitive(true)),
            Triple(classic, "icon_only_inputs", JsonPrimitive(true)),
            Triple(classic, "top_menu", JsonPrimitive(true)),
            Triple(classic, "wifi_status_color", JsonPrimitive(true)),
            Triple(classic, "focus_overshoot", JsonPrimitive(false)),
            Triple(spot, "cinematic_white_focus", JsonPrimitive(true)),
            Triple(spot, "cinematic_dots", JsonPrimitive(false)),
            Triple(spot, "cinematic_scrim", JsonPrimitive(30)),
            Triple(spot, "cinematic_art", JsonPrimitive(101)),
        )
        for ((doc, key, v) in cases) assertReadsAsTheTv(with(doc, key, v), "$key=$v")
        val p = preview(with(classic, "dock_opacity", JsonPrimitive(40)))
        assertEquals(PreviewBand.SEE_THROUGH to 40, p.band to p.bandOpacity)
        assertEquals(PreviewGlow.HORIZON, preview(with(classic, "glow_style", JsonPrimitive(" HORIZON "))).glow)
        assertFalse(preview(with(spot, "cinematic_white_focus", JsonPrimitive(true))).ring)
    }

    @Test fun theStylesDrawAsTheirTableSays() {
        val classic = preview(bytes(LookKeys.spec(StyleId.CLASSIC)))
        assertEquals(listOf(PreviewGlow.RADIAL, PreviewBand.SOLID, PreviewButtons.LABELLED), listOf(classic.glow, classic.band, classic.buttons))
        assertFalse(classic.ring)
        val glass = preview(bytes(LookKeys.spec(StyleId.GLASS)))
        assertEquals(listOf(PreviewGlow.HORIZON, PreviewBand.GLASS, PreviewButtons.ROUND), listOf(glass.glow, glass.band, glass.buttons))
        assertTrue(glass.ring && glass.evenPadding && glass.wifiGreen && glass.roundInputs)
        val spot = preview(bytes(LookKeys.spec(StyleId.SPOTLIGHT)))
        assertTrue(spot.cinematic && spot.ring && spot.dots && spot.band == PreviewBand.SEE_THROUGH)
        assertEquals(45 to 20, spot.scrim to spot.art)
        val spotBlack = preview(bytes(LookKeys.spec(StyleId.SPOTLIGHT), none))
        assertEquals(100 to 50, spotBlack.scrim to spotBlack.art)
        // All apps: no dock row, so no band at all, and the ring.
        val all = preview(bytes(LookKeys.spec(StyleId.ALL_APPS)))
        assertEquals(PreviewBand.NONE, all.band)
        assertTrue(all.ring && !all.dockRow)
        val calm = preview(bytes(LookKeys.spec(StyleId.CALM)))
        assertEquals(PreviewGlow.NONE, calm.glow)
        assertFalse(calm.dockTitle)
    }

    @Test fun movementAndLiftComeFromTheFourKeys() {
        for (m in Movement.entries) for (lift in listOf(false, true)) {
            val p = preview(bytes(LookKeys.spec(StyleId.CLASSIC, movement = m, lift = lift)))
            assertEquals(m, p.movement, "$m $lift")
            assertEquals(lift && (m == Movement.LIVELY || m == Movement.SMOOTH), p.lift, "$m $lift")
        }
    }

    // The focused app's colour is the first dock app's, from the table; grey for an app it does
    // not know; in All apps (no dock row) it is still the first app of the grid, the dock's first.
    @Test fun theFocusColourIsTheFirstDockAppsColour() {
        assertEquals(AppColours.of(PackageName("com.netflix.ninja")), preview(bytes(LookKeys.spec(StyleId.CLASSIC))).focusArgb)
        val browser = listOf(app("com.example.tvbrowser", "Browser")) + dock4
        assertEquals(AppColours.NEUTRAL, preview(bytes(LookKeys.spec(StyleId.CLASSIC), dock = browser), dock = browser).focusArgb)
        assertEquals(AppColours.of(PackageName("com.netflix.ninja")), preview(bytes(LookKeys.spec(StyleId.ALL_APPS))).focusArgb)
        val empty = preview(bytes(LookKeys.spec(StyleId.CLASSIC), dock = emptyList()), dock = emptyList())
        assertEquals(AppColours.of(PackageName("com.spotify.tv.android")), empty.focusArgb)
    }

    // The dock is the bytes' dock_apps (cut at three rows of `columns`), named from the given apps;
    // the others are every other given app the bytes do not hide, in order.
    @Test fun theDockIsTheBytesDockAndTheOthersAreTheRest() {
        val long = (1..15).map { app("com.example.app$it", "App $it") }
        val p = preview(bytes(LookKeys.spec(StyleId.CLASSIC), columns = 4, dock = long), dock = long)
        assertEquals(long.take(12).map { it.name }, p.dock)
        assertEquals(long.drop(12).map { it.name } + others.map { it.name }, p.others)
        assertEquals(2, p.rowsBelowDock)

        val doc = decoded(bytes(LookKeys.spec(StyleId.CLASSIC)))
        val hiding = LauncherConfigCodec.encode(doc.copy(hiddenApps = listOf(PackageName("com.plexapp.android"))))
        assertEquals(listOf("Spotify", "VLC"), preview(hiding).others)
        // A dock app the given lists do not name (gone or switched off) is left out, as the TV skips it.
        val unnamed = preview(bytes(LookKeys.spec(StyleId.CLASSIC)), dock = dock4.take(2), others = emptyList())
        assertEquals(listOf("Netflix", "YouTube"), unnamed.dock)
    }

    // Changing what is not in the bytes changes only the fields it is for.
    @Test fun nothingButTheBytesAndTheGivenListsMakesThePreview() {
        val b = bytes(LookKeys.spec(StyleId.GLASS, names = true))
        val p = preview(b)
        assertEquals(p, preview(b))
        assertEquals(p.copy(inputs = false), preview(b, inputs = false))
        assertEquals(p.copy(minuteOfDay = 600), preview(b, minute = 600))
        assertEquals(p.copy(today = today.plusDays(1)), preview(b, day = today.plusDays(1)))
        val q = preview(b, others = emptyList())
        assertEquals(p.copy(others = emptyList(), othersArgb = emptyList()), q)
        // Bytes the TV would not read draw what the TV draws for an empty file: its defaults.
        assertEquals(preview(LauncherConfigCodec.encode(LauncherConfigDoc())).copy(dock = dock4.map { it.name }), preview("{".toByteArray()))
    }

    @Test fun diwaliIsFestiveWithItsGoldUntilItEnds() {
        val b = bytes(LookKeys.spec(StyleId.CLASSIC), diwali)
        val on = preview(b)
        assertTrue(on.festive)
        assertEquals(0xFFFFB23C.toInt(), on.accentArgb)
        assertEquals("Happy Diwali", on.greeting)
        assertFalse(preview(b, day = LocalDate.of(2026, 11, 11)).festive)
    }

    // The 0.4.0 overload still reads the 0.4.0 fields the same way.
    @Test fun theOldOverloadKeepsItsFields() {
        val b = bytes(LookKeys.spec(StyleId.CLASSIC))
        val old = LookPreview.of(b, null, dock4.map { it.name }, today, evening)
        val new = preview(b)
        assertEquals(old.copy(others = new.others, focusArgb = new.focusArgb, inputs = new.inputs, today = new.today,
            dockArgb = new.dockArgb, othersArgb = new.othersArgb), new.copy(movement = old.movement, lift = old.lift))
    }
}
