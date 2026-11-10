package ai.promptstudio.tvclean.core.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

enum class GlowStyle { RADIAL, HORIZON }

// The 2.7 look keys as launcher 2.7's Config.load reads them from a config.json: the carried
// keys in doc.unknown, with config.xml 2.7's defaults, a wrong type or out of range value giving
// the default, an unknown glow_style giving RADIAL (Config.java bool, integer, string, glowStyle).
data class LookConfig(
    val ambientGlow: Boolean,
    val glowStyle: GlowStyle,
    val appGlow: Boolean,
    val dockGlass: Boolean,
    val dockOpacity: Int,
    val dockEvenPadding: Boolean,
    val alwaysShowNames: Boolean,
    val iconOnlyButtons: Boolean,
    val iconOnlyInputs: Boolean,
    val topMenu: Boolean,
    val wifiStatusColor: Boolean,
    val focusOvershoot: Boolean,
    val cinematicDots: Boolean,
    val cinematicWhiteFocus: Boolean,
    val cinematicArt: Int,
    val cinematicScrim: Int,
)

// The 25 keys launcher 3.0 added (ConfigCodecImpl.CARRIED_30) as its Config.load reads them from a
// config.json: config.xml 3.0's defaults (lunar_utc_offset, read from config.json alone, -1), a
// wrong type or out of range value giving the default, an unknown glass_style giving GLASS, a list
// as Config.stringList keeps it (strings only, trimmed, blanks dropped). Two are not the Config
// field as is: Config keeps single_row and-ed with dock_row, and adds top_bar_apps to the hidden
// list (Agreement in the tests applies both).
enum class GlassStyle { GLASS, RIM, HALO }

data class Keys30(
    val inputSources: List<String>,
    val lunarDate: Boolean,
    val lunarUtcOffset: Int,
    val openOnStartChoice: Boolean,
    val showLibraryPill: Boolean,
    val libraryOkOpens: Boolean,
    val uniformTiles: Boolean,
    val consumeVolumeKeys: Boolean,
    val powerOnHome: Boolean,
    val homeLongPressHome: Boolean,
    val powerOnHomeAlways: Boolean,
    val dockBand: Boolean,
    val wifiStatusWarnings: Boolean,
    val dateBelowClock: Boolean,
    val vpnPill: Boolean,
    val locale: String,
    val topBarApps: List<String>,
    val showGreeting: Boolean,
    val headerWhite: Boolean,
    val clockWallpaper: Boolean,
    val clockWallpaperDim: Int,
    val clockGlow: Boolean,
    val singleRow: Boolean,
    val dockBandAll: Boolean,
    val glassStyle: GlassStyle,
)

// The reader of the carried keys (ConfigCodecImpl.CARRIED_27 and CARRIED_30), for the preview and
// the tests.
// A value in `unknown` is what the codec read (Int, Long or Double as JSONTokener made it, a
// string, a boolean, null, a list or an object) or what code put there; each reader takes it the
// way Config.java's helper of the same name does. Only `unknown` is read: the modelled keys have
// their LauncherConfigDoc fields and LauncherConfigCodec.effective. Not modelled: the dock band
// opacity chosen on the TV (Launcher settings), which wins until the next push.
object LauncherKeys {
    // res/values/config.xml at 3e34f40 (launcher 2.7), checked by LauncherKeysTest, which parses it;
    // the bundled launcher 3.0 keeps every one of these defaults. dock_opacity is not in config.xml:
    // Config.load reads it from config.json alone, default 100.
    val DEFAULTS_27: LookConfig = LookConfig(
        ambientGlow = true, glowStyle = GlowStyle.RADIAL, appGlow = false, dockGlass = false, dockOpacity = 100,
        dockEvenPadding = false, alwaysShowNames = false, iconOnlyButtons = false, iconOnlyInputs = false, topMenu = false,
        wifiStatusColor = false, focusOvershoot = true, cinematicDots = true, cinematicWhiteFocus = false,
        cinematicArt = 50, cinematicScrim = 100,
    )

    // res/values/config.xml at b06ab21 (launcher 3.0, the bundled one), checked by LauncherKeysTest,
    // which parses it. Every default keeps the 2.7 look: dock_band, show_library_pill, show_greeting
    // and clock_glow are on, the rest off or empty.
    val DEFAULTS_30: Keys30 = Keys30(
        inputSources = emptyList(), lunarDate = false, lunarUtcOffset = -1, openOnStartChoice = false,
        showLibraryPill = true, libraryOkOpens = false, uniformTiles = false, consumeVolumeKeys = false,
        powerOnHome = false, homeLongPressHome = false, powerOnHomeAlways = false, dockBand = true,
        wifiStatusWarnings = false, dateBelowClock = false, vpnPill = false, locale = "", topBarApps = emptyList(),
        showGreeting = true, headerWhite = false, clockWallpaper = false, clockWallpaperDim = 50, clockGlow = true,
        singleRow = false, dockBandAll = false, glassStyle = GlassStyle.GLASS,
    )

    fun keys30(doc: LauncherConfigDoc?): Keys30 {
        val d = DEFAULTS_30
        return Keys30(
            inputSources = stringList(doc, "input_sources", d.inputSources),
            lunarDate = bool(doc, "lunar_date", d.lunarDate),
            lunarUtcOffset = int(doc, "lunar_utc_offset", -1, 14, d.lunarUtcOffset),
            openOnStartChoice = bool(doc, "open_on_start_choice", d.openOnStartChoice),
            showLibraryPill = bool(doc, "show_library_pill", d.showLibraryPill),
            libraryOkOpens = bool(doc, "library_ok_opens", d.libraryOkOpens),
            uniformTiles = bool(doc, "uniform_tiles", d.uniformTiles),
            consumeVolumeKeys = bool(doc, "consume_volume_keys", d.consumeVolumeKeys),
            powerOnHome = bool(doc, "power_on_home", d.powerOnHome),
            homeLongPressHome = bool(doc, "home_long_press_home", d.homeLongPressHome),
            powerOnHomeAlways = bool(doc, "power_on_home_always", d.powerOnHomeAlways),
            dockBand = bool(doc, "dock_band", d.dockBand),
            wifiStatusWarnings = bool(doc, "wifi_status_warnings", d.wifiStatusWarnings),
            dateBelowClock = bool(doc, "date_below_clock", d.dateBelowClock),
            vpnPill = bool(doc, "vpn_pill", d.vpnPill),
            locale = string(doc, "locale", d.locale),
            topBarApps = stringList(doc, "top_bar_apps", d.topBarApps),
            showGreeting = bool(doc, "show_greeting", d.showGreeting),
            headerWhite = bool(doc, "header_white", d.headerWhite),
            clockWallpaper = bool(doc, "clock_wallpaper", d.clockWallpaper),
            clockWallpaperDim = int(doc, "clock_wallpaper_dim", 0, 90, d.clockWallpaperDim),
            clockGlow = bool(doc, "clock_glow", d.clockGlow),
            singleRow = bool(doc, "single_row", d.singleRow),
            dockBandAll = bool(doc, "dock_band_all", d.dockBandAll),
            glassStyle = glassStyle(string(doc, "glass_style", "glass")),
        )
    }

    // Config.glassStyle: "rim" or "halo" in any case, once trimmed, else glass.
    fun glassStyle(value: String?): GlassStyle {
        val v = value?.javaTrim() ?: return GlassStyle.GLASS
        return when {
            v.equals("rim", ignoreCase = true) -> GlassStyle.RIM
            v.equals("halo", ignoreCase = true) -> GlassStyle.HALO
            else -> GlassStyle.GLASS
        }
    }

    fun look(doc: LauncherConfigDoc?): LookConfig {
        val d = DEFAULTS_27
        return LookConfig(
            ambientGlow = bool(doc, "ambient_glow", d.ambientGlow),
            glowStyle = glowStyle(string(doc, "glow_style", if (d.glowStyle == GlowStyle.HORIZON) "horizon" else "radial")),
            appGlow = bool(doc, "app_glow", d.appGlow),
            dockGlass = bool(doc, "dock_glass", d.dockGlass),
            dockOpacity = int(doc, "dock_opacity", 30, 100, d.dockOpacity),
            dockEvenPadding = bool(doc, "dock_even_padding", d.dockEvenPadding),
            alwaysShowNames = bool(doc, "always_show_names", d.alwaysShowNames),
            iconOnlyButtons = bool(doc, "icon_only_buttons", d.iconOnlyButtons),
            iconOnlyInputs = bool(doc, "icon_only_inputs", d.iconOnlyInputs),
            topMenu = bool(doc, "top_menu", d.topMenu),
            wifiStatusColor = bool(doc, "wifi_status_color", d.wifiStatusColor),
            focusOvershoot = bool(doc, "focus_overshoot", d.focusOvershoot),
            cinematicDots = bool(doc, "cinematic_dots", d.cinematicDots),
            cinematicWhiteFocus = bool(doc, "cinematic_white_focus", d.cinematicWhiteFocus),
            cinematicArt = int(doc, "cinematic_art", 0, 100, d.cinematicArt),
            cinematicScrim = int(doc, "cinematic_scrim", 0, 100, d.cinematicScrim),
        )
    }

    // Config.glowStyle: "horizon" in any case, once trimmed, else radial.
    fun glowStyle(value: String?): GlowStyle =
        if (value != null && value.javaTrim().equals("horizon", ignoreCase = true)) GlowStyle.HORIZON else GlowStyle.RADIAL

    private fun value(doc: LauncherConfigDoc?, key: String): JsonPrimitive? = doc?.unknown?.get(key) as? JsonPrimitive

    // Config.bool: a JSON true or false, else the default (a string "true" is not a boolean).
    fun bool(doc: LauncherConfigDoc?, key: String, default: Boolean): Boolean {
        val v = value(doc, key) ?: return default
        if (v.isString) return default
        return when (v.content) {
            "true" -> true
            "false" -> false
            else -> default
        }
    }

    // Config.integer: a number's intValue() (a long keeps its low 32 bits, a double is cut towards
    // zero), the default outside min to max or for anything else.
    fun int(doc: LauncherConfigDoc?, key: String, min: Int, max: Int, default: Int): Int {
        val i = intValue(doc?.unknown?.get(key)) ?: return default
        return if (i < min || i > max) default else i
    }

    // Config.string: a string, trimmed as Java trims, else the default.
    fun string(doc: LauncherConfigDoc?, key: String, default: String): String {
        val v = value(doc, key) ?: return default
        return if (v.isString) v.content.javaTrim() else default
    }

    // Config.stringList: a list keeps its strings, trimmed as Java trims, blank ones and anything
    // that is not a string dropped; anything but a list gives the default.
    fun stringList(doc: LauncherConfigDoc?, key: String, default: List<String>): List<String> {
        val v = doc?.unknown?.get(key) as? JsonArray ?: return default
        return v.mapNotNull { item -> (item as? JsonPrimitive)?.takeIf { it.isString }?.content?.javaTrim()?.takeIf { it.isNotEmpty() } }
    }

    // Number.intValue() of a JSON number as the codec or code put it in `unknown`; null for what
    // is not a number. An integer text is a Java Integer or Long (both give the low 32 bits); any
    // other number text (a decimal point, an exponent, Infinity or NaN for a double JSONTokener
    // made) is a Double, which (int) cuts towards zero and saturates.
    internal fun intValue(e: JsonElement?): Int? {
        val v = e as? JsonPrimitive ?: return null
        if (v.isString) return null
        val c = v.content
        if (c == "true" || c == "false" || c == "null") return null
        c.toLongOrNull()?.let { return it.toInt() }
        return c.toDoubleOrNull()?.toInt()
    }
}
