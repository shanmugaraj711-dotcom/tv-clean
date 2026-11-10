package ai.promptstudio.tvclean.core.spec

import kotlinx.serialization.json.JsonPrimitive

// Home screen 3.0's look switches (plan 0.4.5 WP5 step 3, RFC-UP300-1). Kept apart from LookSpec and
// StyleDetails on purpose, so the look2 cross product and its 2.7 differential stay as they are; a
// TV on 2.7 or older gets DEFAULT from the rules (LookGates.reads30). Every default is the
// launcher's own (config.xml at b06ab21, LauncherKeys.DEFAULTS_30), so DEFAULT writes no key and a
// look keeps its 0.4.0 bytes. The keys are read back by the one 3.0 reader, LauncherKeys.keys30.
enum class TilesChoice(val id: String) { ART("art"), ICONS("icons") }
enum class HighlightChoice(val id: String) { GLASS("glass"), RIM("rim"), HALO("halo") }
data class LookExtras(
    val oneRow: Boolean = false,        // single_row
    val bandAll: Boolean = false,       // dock_band_all
    val noBand: Boolean = false,        // dock_band false
    val hideGreeting: Boolean = false,  // show_greeting false
    val dateBelow: Boolean = false,     // date_below_clock
    val brightDate: Boolean = false,    // header_white
    val wifiWarnings: Boolean = false,  // wifi_status_warnings
    val tiles: TilesChoice = TilesChoice.ART,              // uniform_tiles (T2)
    val highlight: HighlightChoice = HighlightChoice.GLASS, // glass_style (T2)
) { companion object { val DEFAULT = LookExtras() } }

object LookExtrasKeys {
    // The keys LookKeys.build may write for the extras, in LookExtras order.
    val KEYS: List<String> = listOf(
        "single_row", "dock_band_all", "dock_band", "show_greeting", "date_below_clock", "header_white",
        "wifi_status_warnings", "uniform_tiles", "glass_style",
    )

    // HighlightChoice for the launcher's glass_style as LauncherKeys.keys30 reads it.
    fun highlight(g: GlassStyle): HighlightChoice = when (g) {
        GlassStyle.GLASS -> HighlightChoice.GLASS
        GlassStyle.RIM -> HighlightChoice.RIM
        GlassStyle.HALO -> HighlightChoice.HALO
    }

    // The extras a 3.0 home screen shows for these bytes, each only where it takes effect (the
    // launcher's own rules, MainActivity and DockDecoration at b06ab21): one row needs the dock row
    // (Config); the band behind every app needs one row and a band (DockDecoration bandAll and
    // bandVisible); no band needs the
    // dock row (the band is a decoration of the dock row); the internet warnings tint the Wi-Fi pill,
    // so they need wifi_status_color and the pill on screen (top_menu hides it). The preview reads
    // these, and the extras differential reads back what build wrote.
    fun shown(doc: LauncherConfigDoc?): LookExtras {
        val c = LauncherKeys.keys30(doc)
        val k = LauncherKeys.look(doc)
        val dockRow = doc?.dockRow ?: ConfigCodecImpl.BUILT_IN.dockRow
        val oneRow = c.singleRow && dockRow   // Config keeps single_row and-ed with dock_row
        return LookExtras(
            oneRow = oneRow,
            bandAll = oneRow && c.dockBandAll && c.dockBand,
            noBand = dockRow && !c.dockBand,
            hideGreeting = !c.showGreeting,
            dateBelow = c.dateBelowClock,
            brightDate = c.headerWhite,
            wifiWarnings = c.wifiStatusWarnings && k.wifiStatusColor && !k.topMenu,
            tiles = if (c.uniformTiles) TilesChoice.ICONS else TilesChoice.ART,
            highlight = highlight(c.glassStyle),
        )
    }

    // What of `extras` takes effect on a doc with these keys (plan WP5 step 4, and the band behind
    // every app needs a band): the writer drops the rest, so a switch that does nothing never
    // reaches the bytes.
    fun applying(extras: LookExtras, dockRow: Boolean, wifiStatusColor: Boolean, topMenu: Boolean): LookExtras {
        val oneRow = extras.oneRow && dockRow
        val noBand = extras.noBand && dockRow
        return extras.copy(
            oneRow = oneRow,
            bandAll = extras.bandAll && oneRow && !noBand,
            noBand = noBand,
            wifiWarnings = extras.wifiWarnings && wifiStatusColor && !topMenu,
        )
    }

    // The keys of `extras` that differ from the launcher's own, as build writes them in doc.unknown.
    fun keys(extras: LookExtras): Map<String, JsonPrimitive> = buildMap {
        if (extras.oneRow) put("single_row", JsonPrimitive(true))
        if (extras.bandAll) put("dock_band_all", JsonPrimitive(true))
        if (extras.noBand) put("dock_band", JsonPrimitive(false))
        if (extras.hideGreeting) put("show_greeting", JsonPrimitive(false))
        if (extras.dateBelow) put("date_below_clock", JsonPrimitive(true))
        if (extras.brightDate) put("header_white", JsonPrimitive(true))
        if (extras.wifiWarnings) put("wifi_status_warnings", JsonPrimitive(true))
        if (extras.tiles == TilesChoice.ICONS) put("uniform_tiles", JsonPrimitive(true))
        if (extras.highlight != HighlightChoice.GLASS) put("glass_style", JsonPrimitive(extras.highlight.id))
    }
}
