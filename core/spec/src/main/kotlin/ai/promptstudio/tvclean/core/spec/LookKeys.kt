package ai.promptstudio.tvclean.core.spec

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import ai.promptstudio.tvclean.core.model.PackageName

// The five styles (spec 6.1). `id` is the home.txt value.
enum class StyleId(val id: String) {
    CLASSIC("classic"), GLASS("glass"), SPOTLIGHT("spotlight"), ALL_APPS("allapps"), CALM("calm");
    companion object { fun of(id: String?): StyleId? = entries.firstOrNull { it.id == id } }
}

// Movement (spec 6.4, 7.3). Lift and zoom is a separate switch.
enum class Movement(val id: String) {
    LIVELY("lively"), SMOOTH("smooth"), QUICK("quick"), OFF("off");
    companion object { fun of(id: String?): Movement? = entries.firstOrNull { it.id == id } }
}

enum class GlowChoice(val id: String) {
    SOFT("soft"), FROM_TOP("top"), NONE("none");
    companion object { fun of(id: String?): GlowChoice? = entries.firstOrNull { it.id == id } }
}

enum class BandChoice(val id: String) {
    SOLID("solid"), SEE_THROUGH("see"), GLASS("glass");
    companion object { fun of(id: String?): BandChoice? = entries.firstOrNull { it.id == id } }
}

enum class ButtonsChoice(val id: String) {
    WITH_NAMES("names"), ROUND("round"), MENU("menu");
    companion object { fun of(id: String?): ButtonsChoice? = entries.firstOrNull { it.id == id } }
}

// A style's own details (spec 7.2), what "Use {style}'s own settings" puts back.
data class StyleDetails(
    val bigName: Boolean,
    val glow: GlowChoice,
    val spotColour: Boolean,
    val ring: Boolean,
    val band: BandChoice,
    val buttons: ButtonsChoice,
    val greenWifi: Boolean,
)

// Everything the look keys depend on, every value concrete. The model resolves the person's
// choice, the style's own and the gates into this; build() still applies the key rules of spec
// 7.3, so a value that does nothing in this style never reaches the bytes.
data class LookSpec(
    val style: StyleId,
    val bigName: Boolean,
    val glow: GlowChoice,
    val spotColour: Boolean,
    val ring: Boolean,
    val band: BandChoice,
    val seeThrough: Int,
    val buttons: ButtonsChoice,
    val greenWifi: Boolean,
    val names: Boolean,
    val movement: Movement,
    val lift: Boolean,
)

// The picture side of a push (spec 6.2, 7.1 step 4), resolved by the model.
data class PictureKeys(
    val wallpaper: Boolean,
    val backgroundColor: String,
    val dim: Int,
    val skyIntensity: Int,
    val festival: String? = null,
    val festivalUntil: String? = null,
    val accentColor: String? = null,
)

// The look2 key writer: spec 7.1 end to end, from a resolved LookSpec and PictureKeys to the
// whole doc a push sends. Every key is written outright (a style's value, the launcher default
// included), so nothing of the last look stays behind on the TV. The 2.7 keys LauncherConfigDoc
// has no field for go in `unknown` (ConfigCodecImpl.CARRIED_27), and so do the 3.0 extras
// (CARRIED_30). LookDifferentialTest holds the result to the bundled launcher's own Config.java
// (3.0, b06ab21) and to 2.7's, for TVs not updated; LookExtrasDifferentialTest holds the extras to 3.0's.
object LookKeys {
    // Spec 7.2 rows, in table order; every style writes each of them (or removes it when the
    // table says "not written").
    val STYLE_KEYS: List<String> = listOf(
        "dock_row", "dock_title", "cinematic_home", "cinematic_scrim", "cinematic_art", "ambient_glow", "glow_style",
        "app_glow", "dock_glass", "dock_opacity", "dock_even_padding", "always_show_names", "icon_only_buttons",
        "icon_only_inputs", "top_menu", "wifi_status_color", "cinematic_white_focus",
    )
    val SEE_THROUGH: List<Int> = listOf(70, 50, 30)
    const val SEE_THROUGH_DEFAULT = 50
    const val SKY_STRENGTH_DEFAULT = 55

    // The keys build() writes for Movement and Lift and zoom (spec 7.3), in that order.
    val MOVEMENT_KEYS: List<String> = listOf("animations", "reduced_motion", "focus_overshoot", "advanced_effects")

    // Config.java's glow_style values.
    const val GLOW_RADIAL = "radial"
    const val GLOW_HORIZON = "horizon"

    // Spotlight's shade and art (spec 7.2): softer over a picture, the launcher's own without one.
    const val SPOT_SCRIM_PICTURE = 45
    const val SPOT_ART_PICTURE = 20
    const val SPOT_SCRIM_NONE = 100
    const val SPOT_ART_NONE = 50

    // Table 7.2 as the prototype's STYLES[].own gives the rows. Spotlight's glow is the colour
    // across the top (spotColour), so its `glow` is the radial SOFT the style writes; its band is
    // the shelf's own see-through one, which no key changes. All apps has no band, so SOLID stands
    // for nothing there. spotColour does nothing outside Spotlight and is false there.
    fun own(style: StyleId): StyleDetails = when (style) {
        StyleId.CLASSIC -> StyleDetails(bigName = true, glow = GlowChoice.SOFT, spotColour = false, ring = false,
            band = BandChoice.SOLID, buttons = ButtonsChoice.WITH_NAMES, greenWifi = false)
        StyleId.GLASS -> StyleDetails(bigName = true, glow = GlowChoice.FROM_TOP, spotColour = false, ring = true,
            band = BandChoice.GLASS, buttons = ButtonsChoice.ROUND, greenWifi = true)
        StyleId.SPOTLIGHT -> StyleDetails(bigName = true, glow = GlowChoice.SOFT, spotColour = true, ring = true,
            band = BandChoice.SEE_THROUGH, buttons = ButtonsChoice.WITH_NAMES, greenWifi = false)
        StyleId.ALL_APPS -> StyleDetails(bigName = false, glow = GlowChoice.SOFT, spotColour = false, ring = true,
            band = BandChoice.SOLID, buttons = ButtonsChoice.WITH_NAMES, greenWifi = false)
        StyleId.CALM -> StyleDetails(bigName = false, glow = GlowChoice.NONE, spotColour = false, ring = false,
            band = BandChoice.SOLID, buttons = ButtonsChoice.ROUND, greenWifi = false)
    }

    // The style's own spec with the given person-level values (names, movement, lift belong to nobody).
    fun spec(style: StyleId, details: StyleDetails = own(style), seeThrough: Int = SEE_THROUGH_DEFAULT, names: Boolean = false,
        movement: Movement = Movement.LIVELY, lift: Boolean = false): LookSpec = LookSpec(
        style = style, bigName = details.bigName, glow = details.glow, spotColour = details.spotColour, ring = details.ring,
        band = details.band, seeThrough = seeThrough, buttons = details.buttons, greenWifi = details.greenWifi,
        names = names, movement = movement, lift = lift,
    )

    // Table 7.2, one column: the STYLE_KEYS values a style writes before any detail. null is
    // "not written" (removed).
    fun styleKeys(style: StyleId, picture: Boolean): Map<String, Any?> {
        val spot = style == StyleId.SPOTLIGHT
        return linkedMapOf(
            "dock_row" to (style != StyleId.ALL_APPS),
            "dock_title" to (style != StyleId.ALL_APPS && style != StyleId.CALM),
            "cinematic_home" to spot,
            "cinematic_scrim" to if (spot) (if (picture) SPOT_SCRIM_PICTURE else SPOT_SCRIM_NONE) else null,
            "cinematic_art" to if (spot) (if (picture) SPOT_ART_PICTURE else SPOT_ART_NONE) else null,
            "ambient_glow" to (style != StyleId.CALM),
            "glow_style" to if (style == StyleId.GLASS) GLOW_HORIZON else GLOW_RADIAL,
            "app_glow" to (style == StyleId.GLASS || style == StyleId.ALL_APPS),
            "dock_glass" to (style == StyleId.GLASS),
            "dock_opacity" to 100,
            "dock_even_padding" to (style == StyleId.GLASS),
            "always_show_names" to false,
            "icon_only_buttons" to (style == StyleId.GLASS || style == StyleId.CALM),
            "icon_only_inputs" to (style == StyleId.GLASS || style == StyleId.CALM),
            "top_menu" to false,
            "wifi_status_color" to (style == StyleId.GLASS),
            "cinematic_white_focus" to if (spot) false else null,
        )
    }

    // Spec 7.1 steps 1 to 9: the whole doc a push sends, from scratch. Style keys (7.2), the
    // details, the picture keys, movement and Lift and zoom, columns, fill_dock false, then
    // DockLayers.applyTo (dock_apps, rows_below_dock, dock_title at three rows, rows 0 on the
    // cinematic shelf), then module_cards. 2.7 keys go in doc.unknown (CARRIED_27).
    //
    // Values out of the launcher's range are brought into it rather than refused: See-through 30
    // to 100 (dock_opacity), dim 0 to 90, sky 0 to 100, columns 1 to 8.
    //
    // 0.4.5 (plan WP5 step 4): after steps 8 and 9, the 3.0 extras go in doc.unknown (CARRIED_30),
    // each only when it differs from the launcher's default and only where it takes effect
    // (LookExtrasKeys.applying: one row and no band need the dock row, the band behind every app
    // needs one row and a band, the internet warnings need Green Wi-Fi and the pill on screen). DEFAULT adds
    // nothing, so every look keeps its 0.4.0 bytes (Look2BytesTest). The rules give DEFAULT to a TV
    // below 3.0, so a 2.7 TV never gets a 3.0 key. One row also writes rows_below_dock 0 (see below);
    // without it the rows are the dock's again, as DockLayers cuts them.
    fun build(spec: LookSpec, picture: PictureKeys, columns: Int, dock: List<PackageName>, cards: Boolean,
        extras: LookExtras = LookExtras.DEFAULT): LauncherConfigDoc {
        val style = spec.style
        val spot = style == StyleId.SPOTLIGHT
        val k = LinkedHashMap(styleKeys(style, picture.wallpaper))   // step 2

        // Step 3: the details, by the rules of spec 7.3.
        k["dock_title"] = when (style) {
            StyleId.ALL_APPS -> false
            StyleId.SPOTLIGHT -> true
            else -> spec.bigName
        }
        if (spot) {
            k["ambient_glow"] = spec.spotColour
            k["glow_style"] = GLOW_RADIAL
            k["cinematic_white_focus"] = !spec.ring
            k["app_glow"] = false
        } else {
            when (spec.glow) {
                GlowChoice.NONE -> k["ambient_glow"] = false   // glow_style keeps the style's; it does nothing
                GlowChoice.SOFT -> { k["ambient_glow"] = true; k["glow_style"] = GLOW_RADIAL }
                GlowChoice.FROM_TOP -> { k["ambient_glow"] = true; k["glow_style"] = GLOW_HORIZON }
            }
            k["app_glow"] = spec.ring
            k["cinematic_white_focus"] = null
            k["cinematic_scrim"] = null
            k["cinematic_art"] = null
        }
        if (style != StyleId.ALL_APPS && !spot) {
            k["dock_glass"] = spec.band == BandChoice.GLASS
            k["dock_opacity"] = if (spec.band == BandChoice.SEE_THROUGH) spec.seeThrough.coerceIn(30, 100) else 100
        }
        if (spec.buttons == ButtonsChoice.MENU) {
            k["top_menu"] = true                           // the icon keys and Wi-Fi colour keep the style's
        } else {
            k["top_menu"] = false
            k["icon_only_buttons"] = spec.buttons == ButtonsChoice.ROUND
            k["icon_only_inputs"] = spec.buttons == ButtonsChoice.ROUND
            k["wifi_status_color"] = spec.greenWifi
        }
        k["always_show_names"] = spec.names

        // Step 5: the four movement keys, with Lift and zoom.
        val (animations, reduced) = when (spec.movement) {
            Movement.LIVELY, Movement.SMOOTH, Movement.QUICK -> true to (spec.movement == Movement.QUICK)
            Movement.OFF -> false to true
        }
        val overshoot = spec.movement == Movement.LIVELY
        val lift = spec.lift && (spec.movement == Movement.LIVELY || spec.movement == Movement.SMOOTH)
        k["focus_overshoot"] = overshoot

        val unknown = LinkedHashMap<String, JsonElement>()
        for ((key, v) in k) {
            if (key in ConfigCodecImpl.MODELLED) continue
            when (v) {
                null -> {}
                is Boolean -> unknown[key] = JsonPrimitive(v)
                is Int -> unknown[key] = JsonPrimitive(v)
                is String -> unknown[key] = JsonPrimitive(v)
                else -> error("no JSON form for $key")
            }
        }
        val doc = LauncherConfigDoc(
            dockRow = k["dock_row"] as Boolean,
            dockTitle = k["dock_title"] as Boolean,
            cinematicHome = k["cinematic_home"] as Boolean,
            // Step 4: the picture.
            wallpaper = picture.wallpaper,
            backgroundColor = picture.backgroundColor,
            wallpaperDim = picture.dim.coerceIn(0, 90),
            skyIntensity = picture.skyIntensity.coerceIn(0, 100),
            festival = picture.festival,
            festivalUntil = picture.festivalUntil,
            accentColor = picture.accentColor,
            wallpaperRotationMinutes = 0,
            // Step 5.
            animations = animations,
            reducedMotion = reduced,
            advancedEffects = lift,
            // Steps 6 and 7.
            columns = columns.coerceIn(1, 8),
            fillDock = false,
            unknown = unknown,
        )
        // Steps 8 and 9.
        val built = DockLayers.applyTo(doc, dock).copy(moduleCards = cards)
        if (extras == LookExtras.DEFAULT) return built
        val applied = LookExtrasKeys.applying(extras, dockRow = built.dockRow == true,
            wifiStatusColor = k["wifi_status_color"] == true, topMenu = k["top_menu"] == true)
        val withKeys = built.copy(unknown = LinkedHashMap(built.unknown).apply { putAll(LookExtrasKeys.keys(applied)) })
        // One row: every app is in the dock's row and nothing is under it, but the launcher still
        // sizes the tiles and lifts the row by rows_below_dock (MainActivity.java:608-628 and
        // AppAdapter.tileWidthPx at b06ab21), so it goes to 0, and the big name the three-row rule
        // took away (DockLayers) comes back: the row has the room, and its title names the app.
        if (!applied.oneRow) return withKeys
        return withKeys.copy(rowsBelowDock = withKeys.rowsBelowDock?.let { 0 }, dockTitle = k["dock_title"] as Boolean)
    }
}
