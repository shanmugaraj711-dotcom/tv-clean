package ai.promptstudio.tvclean.core.spec

import ai.promptstudio.tvclean.core.model.PackageName

// The dock and the rows under it, as the launcher builds them (AppAdapter.reload and MainActivity
// at upstream main): every app is sorted by its place in dock_apps first, the grid has `columns`
// tiles per row and its first row is the dock, and rows_below_dock grid rows stay in view under
// it. So the phone keeps one ordered list and cuts it into rows of `columns`: the first row is the
// dock, the next are Row 2 and Row 3. A row only ever holds apps once the rows above it are full,
// because a gap would make the launcher slide the next apps up into it; the cut list cannot have
// one. The phone offers at most MAX_ROWS rows (the dock and two under it).
//
// On the TV, an app in dock_apps that is not there (uninstalled, switched off, no TV launcher
// activity) is skipped and every later app moves up a slot. `decode` applies the same rule, so
// the sheet shows what the TV shows.
object DockLayers {
    const val MAX_ROWS = 3
    const val DEFAULT_COLUMNS = 4
    // 0.4.0: the most apps in a row the phone offers (Apps in a row, 4 to 8). The TV's own Launcher
    // settings stop at 7; config.json takes 1 to 8 (Config.load), so 8 comes only from the phone.
    const val MIN_OFFERED_COLUMNS = 4
    const val MAX_OFFERED_COLUMNS = 8

    // Tiles per row for a theme's doc, as the launcher reads it (1 to 8, else the default 4).
    fun columnsOf(doc: LauncherConfigDoc?): Int = doc?.columns?.takeIf { it in 1..8 } ?: DEFAULT_COLUMNS

    fun capacity(columns: Int): Int = columns.coerceAtLeast(1) * MAX_ROWS

    // The list the phone keeps: no repeats, at most MAX_ROWS rows.
    fun trim(apps: List<PackageName>, columns: Int): List<PackageName> = apps.distinct().take(capacity(columns))

    // 0.4.0: the list the phone saves (HomeChoice.dock): no repeats, at most the rows of the most
    // apps in a row it offers (24 apps). It is never cut to the look's own capacity, so going from
    // 7 apps in a row to 4 and back loses no app and no place. Only encoding and drawing cut it
    // (applyTo, decode, rows).
    fun keep(apps: List<PackageName>): List<PackageName> = apps.distinct().take(capacity(MAX_OFFERED_COLUMNS))

    // How many apps of a saved list of `count` wait beyond what `columns` a row shows (three rows).
    fun waiting(count: Int, columns: Int): Int = (count - capacity(columns)).coerceAtLeast(0)

    // The list cut into rows: [dock, row 2, row 3], only the rows that hold an app.
    fun <T> rows(apps: List<T>, columns: Int): List<List<T>> = apps.take(capacity(columns)).chunked(columns.coerceAtLeast(1))

    // How many rows under the dock hold an app: the rows_below_dock the phone writes.
    fun rowsBelowDock(count: Int, columns: Int): Int {
        val c = columns.coerceAtLeast(1)
        val n = count.coerceIn(0, capacity(c))
        return if (n == 0) 0 else (n + c - 1) / c - 1
    }

    // The doc the phone pushes: the theme's doc with dock_apps the trimmed list and rows_below_dock
    // the rows under the dock that hold an app. An empty list leaves dock_apps out, so the launcher
    // keeps its own dock, and leaves the theme's rows_below_dock as it is.
    //
    // Owner decision D1 (launcher v2.4.0): with three rows (the dock and two under it) dock_title
    // is written false. Launcher 2.4 fits the rows to the screen, and the focused app's name above
    // the dock would push most of Row 3 below the edge of a 1080p screen; without it all three
    // rows fit. One or two rows leave the theme's dock_title as it is. Launcher 2.0 ignores the key.
    //
    // 0.4.0: a look with the cinematic shelf (cinematic_home true, Cinema) always writes
    // rows_below_dock 0. Its 68 sp name, Last opened line and dots stand taller than the room the
    // launcher keeps for a dock title (TITLE_ROOM_DP, made for the 48 sp name), so a row in view
    // under the dock pushed the name up into the clock. The rows still hold their apps: dock_apps
    // keeps the whole list in order, so on the TV they are the first rows of the grid, peeking under
    // the dock, one press of Down away.
    //
    // 0.4.0 `columns`: the person's own apps in a row (HomeChoice.columns), written into the doc
    // before the list is cut, so the rows and dock_title follow it, not the look. Null, outside 1 to
    // 8 or the look's own leaves the doc's columns as they are.
    fun applyTo(doc: LauncherConfigDoc, apps: List<PackageName>, columns: Int? = null): LauncherConfigDoc {
        val override = columns?.takeIf { it in 1..8 && it != columnsOf(doc) }
        return cut(if (override != null) doc.copy(columns = override) else doc, apps)
    }

    private fun cut(doc: LauncherConfigDoc, apps: List<PackageName>): LauncherConfigDoc {
        val columns = columnsOf(doc)
        val dock = trim(apps, columns)
        if (dock.isEmpty()) return doc.copy(dockApps = null)
        val below = if (doc.cinematicHome == true) 0 else rowsBelowDock(dock.size, columns)
        val title = if (below == MAX_ROWS - 1) false else doc.dockTitle
        return doc.copy(dockApps = dock, rowsBelowDock = below, dockTitle = title)
    }

    // What the TV shows for a dock_apps list: the apps it can show, in order, cut to MAX_ROWS rows.
    fun decode(dockApps: List<PackageName>, shown: (PackageName) -> Boolean, columns: Int): List<PackageName> =
        trim(dockApps.filter(shown), columns)
}
