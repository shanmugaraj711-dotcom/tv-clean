package ai.promptstudio.tvclean.core.spec

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import ai.promptstudio.tvclean.core.model.PackageName
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// LookKeys.build against spec 7.1 to 7.3, rule by rule (build-plan 4.1). The real 2.7 reader of the
// same docs is LookDifferentialTest's.
class LookKeysTest {
    private val dock4 = listOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar")
        .map(::PackageName)
    private val mist = PictureKeys(wallpaper = true, backgroundColor = "#0E1022", dim = 0, skyIntensity = 0)
    private val none = PictureKeys(wallpaper = false, backgroundColor = "#000000", dim = 0, skyIntensity = 0)

    private fun build(spec: LookSpec, picture: PictureKeys = mist, columns: Int = 5, dock: List<PackageName> = dock4, cards: Boolean = false) =
        LookKeys.build(spec, picture, columns, dock, cards)

    // Every value of a STYLE_KEYS key as the doc holds it: the modelled ones from their fields,
    // the carried ones from `unknown` (null when not written).
    private fun styleValue(d: LauncherConfigDoc, key: String): Any? = when (key) {
        "dock_row" -> d.dockRow
        "dock_title" -> d.dockTitle
        "cinematic_home" -> d.cinematicHome
        else -> (d.unknown[key] as JsonPrimitive?)?.let { p ->
            when {
                p.isString -> p.content
                p.content == "true" || p.content == "false" -> p.content.toBooleanStrict()
                else -> p.content.toInt()
            }
        }
    }
    private fun carried(d: LauncherConfigDoc, key: String) = styleValue(d, key)

    // Spec 7.2, transcribed here on its own (not from LookKeys), column by column. Spotlight's
    // cinematic_scrim and cinematic_art depend on the picture and are checked below.
    private val table: Map<StyleId, Map<String, Any?>> = run {
        val keys = LookKeys.STYLE_KEYS
        fun col(vararg v: Any?) = keys.zip(v.toList()).toMap()
        mapOf(
            //                     row   title  cin    scrim art   ambient glow_style app   glass  op   even   names  iconB  iconI  menu   wifi   white
            StyleId.CLASSIC to col(true, true, false, null, null, true, "radial", false, false, 100, false, false, false, false, false, false, null),
            StyleId.GLASS to col(true, true, false, null, null, true, "horizon", true, true, 100, true, false, true, true, false, true, null),
            StyleId.SPOTLIGHT to col(true, true, true, "pic", "pic", true, "radial", false, false, 100, false, false, false, false, false, false, false),
            StyleId.ALL_APPS to col(false, false, false, null, null, true, "radial", true, false, 100, false, false, false, false, false, false, null),
            StyleId.CALM to col(true, false, false, null, null, false, "radial", false, false, 100, false, false, true, true, false, false, null),
        )
    }

    @Test fun styleKeysAreTable72InOrder() {
        assertEquals(listOf("dock_row", "dock_title", "cinematic_home", "cinematic_scrim", "cinematic_art", "ambient_glow", "glow_style",
            "app_glow", "dock_glass", "dock_opacity", "dock_even_padding", "always_show_names", "icon_only_buttons", "icon_only_inputs",
            "top_menu", "wifi_status_color", "cinematic_white_focus"), LookKeys.STYLE_KEYS)
        assertEquals(listOf(70, 50, 30), LookKeys.SEE_THROUGH)
        assertEquals(50, LookKeys.SEE_THROUGH_DEFAULT)
        assertEquals(55, LookKeys.SKY_STRENGTH_DEFAULT)
        // Every carried style key is a key the codec carries; the rest are modelled.
        for (k in LookKeys.STYLE_KEYS) assertTrue(k in ConfigCodecImpl.CARRIED_27 || k in ConfigCodecImpl.MODELLED, k)
    }

    // Every style's own spec writes every STYLE_KEYS key with its table value, or leaves it out
    // where the table says "not written", with and without a picture.
    @Test fun everyStyleWritesEveryStyleKeyOrRemovesIt() {
        for (style in StyleId.entries) for (picture in listOf(mist, none)) {
            val d = build(LookKeys.spec(style), picture)
            for ((key, want) in table.getValue(style)) {
                val expected = when {
                    want != "pic" -> want
                    key == "cinematic_scrim" -> if (picture.wallpaper) 45 else 100
                    else -> if (picture.wallpaper) 20 else 50
                }
                assertEquals(expected, styleValue(d, key), "$style ${if (picture.wallpaper) "Mist" else "none"}: $key")
            }
            // styleKeys() is the same table.
            val base = LookKeys.styleKeys(style, picture.wallpaper)
            assertEquals(LookKeys.STYLE_KEYS, base.keys.toList())
            for ((key, v) in base) assertEquals(v, styleValue(d, key), "$style styleKeys $key")
        }
    }

    // The prototype's STYLES[].own, the details "Use {style}'s own settings" puts back.
    @Test fun ownDetailsAreThePrototypes() {
        assertEquals(StyleDetails(true, GlowChoice.SOFT, false, false, BandChoice.SOLID, ButtonsChoice.WITH_NAMES, false), LookKeys.own(StyleId.CLASSIC))
        assertEquals(StyleDetails(true, GlowChoice.FROM_TOP, false, true, BandChoice.GLASS, ButtonsChoice.ROUND, true), LookKeys.own(StyleId.GLASS))
        assertEquals(StyleDetails(true, GlowChoice.SOFT, true, true, BandChoice.SEE_THROUGH, ButtonsChoice.WITH_NAMES, false), LookKeys.own(StyleId.SPOTLIGHT))
        assertEquals(StyleDetails(false, GlowChoice.SOFT, false, true, BandChoice.SOLID, ButtonsChoice.WITH_NAMES, false), LookKeys.own(StyleId.ALL_APPS))
        assertEquals(StyleDetails(false, GlowChoice.NONE, false, false, BandChoice.SOLID, ButtonsChoice.ROUND, false), LookKeys.own(StyleId.CALM))
        val s = LookKeys.spec(StyleId.GLASS)
        assertEquals(LookSpec(StyleId.GLASS, true, GlowChoice.FROM_TOP, false, true, BandChoice.GLASS, 50, ButtonsChoice.ROUND, true,
            names = false, movement = Movement.LIVELY, lift = false), s)
        assertEquals(LookSpec(StyleId.CALM, false, GlowChoice.NONE, false, false, BandChoice.SOLID, 70, ButtonsChoice.ROUND, false,
            names = true, movement = Movement.QUICK, lift = true),
            LookKeys.spec(StyleId.CALM, seeThrough = 70, names = true, movement = Movement.QUICK, lift = true))
    }

    @Test fun idsReadBack() {
        assertEquals(listOf("classic", "glass", "spotlight", "allapps", "calm"), StyleId.entries.map { it.id })
        assertEquals(listOf("lively", "smooth", "quick", "off"), Movement.entries.map { it.id })
        assertEquals(listOf("soft", "top", "none"), GlowChoice.entries.map { it.id })
        assertEquals(listOf("solid", "see", "glass"), BandChoice.entries.map { it.id })
        assertEquals(listOf("names", "round", "menu"), ButtonsChoice.entries.map { it.id })
        for (s in StyleId.entries) assertEquals(s, StyleId.of(s.id))
        for (m in Movement.entries) assertEquals(m, Movement.of(m.id))
        for (g in GlowChoice.entries) assertEquals(g, GlowChoice.of(g.id))
        for (b in BandChoice.entries) assertEquals(b, BandChoice.of(b.id))
        for (b in ButtonsChoice.entries) assertEquals(b, ButtonsChoice.of(b.id))
        assertEquals(null, StyleId.of(null)); assertEquals(null, StyleId.of("CLASSIC")); assertEquals(null, Movement.of("")); assertEquals(null, GlowChoice.of("x"))
    }

    // Big name: dock_title is the switch, except All apps (false) and Spotlight (true).
    @Test fun bigName() {
        for (style in StyleId.entries) for (on in listOf(true, false)) {
            val want = when (style) { StyleId.ALL_APPS -> false; StyleId.SPOTLIGHT -> true; else -> on }
            assertEquals(want, build(LookKeys.spec(style).copy(bigName = on)).dockTitle, "$style big name $on")
        }
    }

    // Glow, outside Spotlight: None writes ambient_glow false and keeps the style's glow_style;
    // Soft is true and radial; From the top is true and horizon.
    @Test fun glowOutsideSpotlight() {
        for (style in StyleId.entries - StyleId.SPOTLIGHT) {
            val styleGlow = table.getValue(style).getValue("glow_style")
            for (glow in GlowChoice.entries) {
                val d = build(LookKeys.spec(style).copy(glow = glow))
                val (ambient, shape) = when (glow) {
                    GlowChoice.NONE -> false to styleGlow
                    GlowChoice.SOFT -> true to "radial"
                    GlowChoice.FROM_TOP -> true to "horizon"
                }
                assertEquals(ambient, carried(d, "ambient_glow"), "$style $glow")
                assertEquals(shape, carried(d, "glow_style"), "$style $glow")
                // App colour across the top is Spotlight's alone.
                assertEquals(d, build(LookKeys.spec(style).copy(glow = glow, spotColour = !LookKeys.own(style).spotColour)))
            }
        }
        assertEquals("horizon", carried(build(LookKeys.spec(StyleId.GLASS).copy(glow = GlowChoice.NONE)), "glow_style"))
    }

    // Spotlight: ambient_glow is App colour across the top, glow_style radial, the shade and art by
    // the picture, cinematic_white_focus the ring switch turned round, app_glow false. The Glow
    // choice does nothing there.
    @Test fun spotlight() {
        for (spot in listOf(true, false)) for (ring in listOf(true, false)) for (glow in GlowChoice.entries) for (picture in listOf(mist, none)) {
            val d = build(LookKeys.spec(StyleId.SPOTLIGHT).copy(spotColour = spot, ring = ring, glow = glow), picture)
            assertEquals(spot, carried(d, "ambient_glow"))
            assertEquals("radial", carried(d, "glow_style"))
            assertEquals(if (picture.wallpaper) 45 else 100, carried(d, "cinematic_scrim"))
            assertEquals(if (picture.wallpaper) 20 else 50, carried(d, "cinematic_art"))
            assertEquals(!ring, carried(d, "cinematic_white_focus"))
            assertEquals(false, carried(d, "app_glow"))
            assertEquals(true, d.cinematicHome)
        }
    }

    // Ring: app_glow is the switch in every other style, and the cinematic keys are not written.
    @Test fun ringOutsideSpotlight() {
        for (style in StyleId.entries - StyleId.SPOTLIGHT) for (ring in listOf(true, false)) {
            val d = build(LookKeys.spec(style).copy(ring = ring))
            assertEquals(ring, carried(d, "app_glow"), "$style ring $ring")
            for (k in listOf("cinematic_white_focus", "cinematic_scrim", "cinematic_art")) assertFalse(k in d.unknown, "$style writes $k")
            assertEquals(false, d.cinematicHome)
        }
    }

    // Band, outside All apps and Spotlight: dock_glass for Glass, dock_opacity the See-through
    // amount for See-through and 100 otherwise, dock_even_padding the style's. All apps and
    // Spotlight keep the style's band keys whatever is chosen.
    @Test fun band() {
        for (style in StyleId.entries) for ((band, see) in LookInputs.bands) {
            val d = build(LookKeys.spec(style).copy(band = band, seeThrough = see))
            val t = table.getValue(style)
            val fixed = style == StyleId.ALL_APPS || style == StyleId.SPOTLIGHT
            assertEquals(if (fixed) t["dock_glass"] else band == BandChoice.GLASS, carried(d, "dock_glass"), "$style $band")
            assertEquals(if (!fixed && band == BandChoice.SEE_THROUGH) see else 100, carried(d, "dock_opacity"), "$style $band $see")
            assertEquals(t["dock_even_padding"], carried(d, "dock_even_padding"), "$style $band")
        }
        // An amount outside the launcher's 30 to 100 is brought into it, so the TV never falls back to 100.
        assertEquals(30, carried(build(LookKeys.spec(StyleId.CLASSIC).copy(band = BandChoice.SEE_THROUGH, seeThrough = 10)), "dock_opacity"))
        // The amount does nothing without See-through.
        assertEquals(build(LookKeys.spec(StyleId.CLASSIC).copy(band = BandChoice.GLASS, seeThrough = 70)),
            build(LookKeys.spec(StyleId.CLASSIC).copy(band = BandChoice.GLASS, seeThrough = 30)))
    }

    // Buttons: One menu button writes top_menu true and keeps the style's icon keys and Wi-Fi
    // colour; otherwise top_menu false, both icon keys Round icons, and the Wi-Fi colour the switch.
    @Test fun buttonsAndGreenWifi() {
        for (style in StyleId.entries) for (buttons in ButtonsChoice.entries) for (wifi in listOf(true, false)) {
            val d = build(LookKeys.spec(style).copy(buttons = buttons, greenWifi = wifi))
            val t = table.getValue(style)
            if (buttons == ButtonsChoice.MENU) {
                assertEquals(true, carried(d, "top_menu"))
                assertEquals(t["icon_only_buttons"], carried(d, "icon_only_buttons"), "$style menu")
                assertEquals(t["icon_only_inputs"], carried(d, "icon_only_inputs"), "$style menu")
                assertEquals(t["wifi_status_color"], carried(d, "wifi_status_color"), "$style menu: the style's own Wi-Fi colour")
            } else {
                assertEquals(false, carried(d, "top_menu"))
                assertEquals(buttons == ButtonsChoice.ROUND, carried(d, "icon_only_buttons"), "$style $buttons")
                assertEquals(buttons == ButtonsChoice.ROUND, carried(d, "icon_only_inputs"), "$style $buttons")
                assertEquals(wifi, carried(d, "wifi_status_color"), "$style $buttons wifi $wifi")
            }
        }
    }

    @Test fun names() {
        for (style in StyleId.entries) for (on in listOf(true, false))
            assertEquals(on, carried(build(LookKeys.spec(style, names = on)), "always_show_names"), "$style names $on")
    }

    // The movement table of build-plan 4.1 (spec 7.3), with Lift and zoom.
    @Test fun movement() {
        for (m in Movement.entries) for (lift in listOf(true, false)) {
            val d = build(LookKeys.spec(StyleId.CLASSIC, movement = m, lift = lift))
            val want = when (m) {
                Movement.LIVELY -> listOf(true, false, true, lift)
                Movement.SMOOTH -> listOf(true, false, false, lift)
                Movement.QUICK -> listOf(true, true, false, false)
                Movement.OFF -> listOf(false, true, false, false)
            }
            assertEquals(want, listOf(d.animations, d.reducedMotion, carried(d, "focus_overshoot"), d.advancedEffects), "$m lift $lift")
        }
        assertEquals(listOf("animations", "reduced_motion", "focus_overshoot", "advanced_effects"), LookKeys.MOVEMENT_KEYS)
    }

    // Picture keys, wallpaper_rotation_minutes 0, and the ranges the launcher reads.
    @Test fun picture() {
        val diwali = LookInputs.pictures.toMap().getValue("Diwali")
        val d = build(LookKeys.spec(StyleId.CLASSIC), diwali)
        assertEquals(listOf(true, "#120809", 0, 0, "diwali", "2026-11-10", "#FFB23C", 0),
            listOf(d.wallpaper, d.backgroundColor, d.wallpaperDim, d.skyIntensity, d.festival, d.festivalUntil, d.accentColor, d.wallpaperRotationMinutes))
        val photo = build(LookKeys.spec(StyleId.CALM), PictureKeys(wallpaper = true, backgroundColor = "#000000", dim = 30, skyIntensity = 55))
        assertEquals(listOf(true, "#000000", 30, 55, null, null, null, 0),
            listOf(photo.wallpaper, photo.backgroundColor, photo.wallpaperDim, photo.skyIntensity, photo.festival, photo.festivalUntil, photo.accentColor, photo.wallpaperRotationMinutes))
        val plain = build(LookKeys.spec(StyleId.CLASSIC), none)
        assertEquals(false, plain.wallpaper)
        val wild = build(LookKeys.spec(StyleId.CLASSIC), PictureKeys(wallpaper = true, backgroundColor = "#000000", dim = 120, skyIntensity = -5))
        assertEquals(90 to 0, wild.wallpaperDim to wild.skyIntensity)
    }

    // Columns, fill_dock false, DockLayers (dock_apps, rows_below_dock, dock_title off at three
    // rows, rows 0 on the cinematic shelf) and module_cards.
    @Test fun columnsDockAndCards() {
        val fifteen = dock4 + List(11) { PackageName("com.example.app$it") }
        for (columns in 4..8) {
            val d = build(LookKeys.spec(StyleId.CLASSIC), columns = columns, dock = fifteen, cards = true)
            assertEquals(columns, d.columns)
            assertEquals(false, d.fillDock)
            assertEquals(true, d.moduleCards)
            val kept = DockLayers.trim(fifteen, columns)
            assertEquals(kept, d.dockApps)
            assertEquals(DockLayers.rowsBelowDock(kept.size, columns), d.rowsBelowDock)
            assertEquals(d.rowsBelowDock != 2, d.dockTitle, "columns $columns: big name off at three rows")
        }
        val spot = build(LookKeys.spec(StyleId.SPOTLIGHT), columns = 4, dock = fifteen)
        assertEquals(0 to true, spot.rowsBelowDock to spot.dockTitle)
        val empty = build(LookKeys.spec(StyleId.CLASSIC), dock = emptyList())
        assertEquals(null to null, empty.dockApps to empty.rowsBelowDock)
        assertEquals(false, build(LookKeys.spec(StyleId.CLASSIC), cards = false).moduleCards)
        assertEquals(8, build(LookKeys.spec(StyleId.CLASSIC), columns = 9).columns)
        assertEquals(1, build(LookKeys.spec(StyleId.CLASSIC), columns = 0).columns)
    }

    // Every resolved combination: no modelled key in `unknown`, only carried ones there, and the
    // bytes under the launcher's 64 KB cap even with 24 apps of the longest package names.
    @Test fun everyCombinationIsCarriedAndUnder64Kb() {
        val long = List(24) { i -> PackageName(List(3) { "a" + "b".repeat(62) }.joinToString(".") + ".app$i") }
        var n = 0
        var biggest = 0
        for ((_, picture) in LookInputs.pictures) for (spec in LookInputs.cross()) {
            val d = build(spec, picture, columns = 8, dock = long, cards = true)
            for (k in d.unknown.keys) {
                assertFalse(k in ConfigCodecImpl.MODELLED, "$spec puts the modelled key $k in unknown")
                assertTrue(k in ConfigCodecImpl.CARRIED_27, "$spec writes $k, which the codec does not carry")
            }
            biggest = maxOf(biggest, LauncherConfigCodec.encode(d).size)
            n++
        }
        assertEquals(5 * 57_600, n)
        assertTrue(biggest <= ConfigCodecImpl.MAX_BYTES, "$biggest bytes")
        println("LookKeys: $n combinations, the biggest $biggest bytes")
    }

    // A push is the same bytes for the same inputs, and the person's values never change a key
    // their style does not use (Spotlight's switch outside Spotlight, the band in All apps).
    @Test fun buildIsAFunctionOfWhatMatters() {
        val a = build(LookKeys.spec(StyleId.ALL_APPS).copy(band = BandChoice.GLASS))
        val b = build(LookKeys.spec(StyleId.ALL_APPS).copy(band = BandChoice.SEE_THROUGH, seeThrough = 30))
        assertEquals(a, b)
        assertEquals(LauncherConfigCodec.encode(a).decodeToString(), LauncherConfigCodec.encode(b).decodeToString())
    }
}
