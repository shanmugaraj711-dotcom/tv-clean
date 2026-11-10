package ai.promptstudio.tvclean.core.spec

import org.junit.Test
import ai.promptstudio.tvclean.core.model.PackageName
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

// The dock and the rows under it: one ordered list cut into rows of `columns`, written as
// dock_apps (the list) and rows_below_dock (the rows under the dock that hold an app).
class DockLayersTest {
    private fun apps(n: Int) = List(n) { PackageName("com.example.app$it") }

    // Launcher 2.5 keeps a dock or tile order set on the TV (Edit dock, Reorder; files/order.txt)
    // only while dock_apps is the same list (Order.ranks), so a new look must write the same
    // dock_apps for the same dock: the Home screen note promises that a new look keeps it.
    @Test fun aNewLookWritesTheSameDockApps() {
        val looks = BundledThemeSource().bundled().map { it.doc } + LauncherConfigDoc()
        for (n in listOf(1, 4, 8, 12)) {
            val written = looks.map { DockLayers.applyTo(it, apps(n)).dockApps }
            assertEquals(listOf(apps(n)), written.distinct(), "$n apps")
        }
    }

    // RF-LAUNCHER-250 F4: up to 12 apps every look writes the same dock_apps. Six columns holds up
    // to 18 (the dock and two rows of six); a four-tile look holds 12, so a longer dock is cut to its
    // first 12 there and the TV's own dock edit for the longer list no longer applies (upstream #340).
    @Test fun aDockOfMoreThanTwelveAppsIsCutByAFourTileLook() {
        val looks = BundledThemeSource().bundled().associateBy { it.id }
        val six = looks.getValue("six-columns").doc
        assertEquals(6, DockLayers.columnsOf(six))
        for (n in listOf(13, 18)) {
            assertEquals(apps(n), DockLayers.applyTo(six, apps(n)).dockApps, "$n apps under Six columns")
            for (id in listOf("default", "aurora")) assertEquals(apps(12), DockLayers.applyTo(looks.getValue(id).doc, apps(n)).dockApps, "$n apps under $id")
            assertEquals(apps(12), DockLayers.applyTo(LauncherConfigDoc(), apps(n)).dockApps)
        }
        assertEquals(apps(18), DockLayers.applyTo(six, apps(20)).dockApps, "never a fourth row")
    }

    @Test fun theListIsCutIntoRowsOfColumns() {
        assertEquals(emptyList(), DockLayers.rows(apps(0), 4))
        assertEquals(listOf(apps(3)), DockLayers.rows(apps(3), 4))
        assertEquals(listOf(apps(4)), DockLayers.rows(apps(4), 4))
        assertEquals(listOf(apps(4), apps(5).drop(4)), DockLayers.rows(apps(5), 4))
        assertEquals(listOf(apps(6)), DockLayers.rows(apps(6), 6))
    }

    @Test fun rowsBelowDockCountsTheRowsUnderTheDockThatHoldAnApp() {
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2), (0..13).map { DockLayers.rowsBelowDock(it, 4) })
        assertEquals(0, DockLayers.rowsBelowDock(6, 6))
        assertEquals(1, DockLayers.rowsBelowDock(7, 6))
    }

    // Dock plus two rows is the most: a fourth row is never written.
    @Test fun atMostThreeRows() {
        assertEquals(12, DockLayers.capacity(4))
        assertEquals(apps(12), DockLayers.trim(apps(20), 4))
        assertEquals(3, DockLayers.rows(apps(20), 4).size)
        assertEquals(2, DockLayers.rowsBelowDock(20, 4))
        val doc = DockLayers.applyTo(LauncherConfigDoc(), apps(20))
        assertEquals(apps(12), doc.dockApps)
        assertEquals(2, doc.rowsBelowDock)
    }

    // A row only holds apps once the rows above it are full: every row but the last of the cut is
    // full, whatever was added, removed or moved.
    @Test fun aLowerRowNeverHoldsAppsWhileARowAboveHasRoom() {
        for (n in 0..12) {
            val rows = DockLayers.rows(apps(n), 4)
            rows.dropLast(1).forEach { assertEquals(4, it.size, "n=$n") }
            assertEquals(n, rows.sumOf { it.size })
            assertEquals(maxOf(0, rows.size - 1), DockLayers.rowsBelowDock(n, 4))
        }
    }

    @Test fun applyWritesExactlyTheListAndItsRows() {
        val six = apps(6)
        val doc = DockLayers.applyTo(LauncherConfigDoc(backgroundColor = "#000000"), six + six.take(2))
        assertEquals(six, doc.dockApps, "repeats are dropped")
        assertEquals(1, doc.rowsBelowDock)
        assertEquals("#000000", doc.backgroundColor, "the theme's other keys stay")
        // The six columns theme: six fit in the dock, so no row under it.
        val wide = DockLayers.applyTo(LauncherConfigDoc(columns = 6, rowsBelowDock = 1), six)
        assertEquals(6, DockLayers.columnsOf(wide))
        assertEquals(0, wide.rowsBelowDock)
    }

    // Owner decision D1: three rows (the dock and two under it) write dock_title false, so launcher
    // 2.4 fits all three on the screen; one or two rows leave the theme's dock_title as it is.
    @Test fun threeRowsTurnTheDockTitleOff() {
        assertEquals(false, DockLayers.applyTo(LauncherConfigDoc(), apps(9)).dockTitle)
        assertEquals(false, DockLayers.applyTo(LauncherConfigDoc(dockTitle = true), apps(12)).dockTitle)
        assertNull(DockLayers.applyTo(LauncherConfigDoc(), apps(8)).dockTitle)
        assertNull(DockLayers.applyTo(LauncherConfigDoc(), apps(1)).dockTitle)
        assertEquals(true, DockLayers.applyTo(LauncherConfigDoc(dockTitle = true), apps(5)).dockTitle)
        assertEquals(false, DockLayers.applyTo(LauncherConfigDoc(dockTitle = false), apps(4)).dockTitle)
        assertNull(DockLayers.applyTo(LauncherConfigDoc(), emptyList()).dockTitle)
        assertEquals(false, DockLayers.applyTo(LauncherConfigDoc(columns = 6), apps(13)).dockTitle)
        assertNull(DockLayers.applyTo(LauncherConfigDoc(columns = 6), apps(12)).dockTitle)
        val text = LauncherConfigCodec.encode(DockLayers.applyTo(LauncherConfigDoc(), apps(9))).decodeToString()
        assertEquals(true, "\"rows_below_dock\": 2," in text && "\"dock_title\": false" in text, text)
    }

    // 0.4.0: the cinematic shelf (Cinema) keeps the dock low whatever the dock's length: rows_below_dock
    // 0, so its big name never rides up into the clock. Every app stays in dock_apps, in order: the rows
    // under the dock are the first grid rows on the TV, one press of Down away, and read back the same.
    @Test fun theCinematicShelfKeepsTheRowsUnderTheDockOutOfView() {
        val cinema = LauncherConfigDoc(columns = 5, cinematicHome = true, dockTitle = true)
        for (n in 1..15) {
            val doc = DockLayers.applyTo(cinema, apps(n))
            assertEquals(0, doc.rowsBelowDock, "n=$n")
            assertEquals(apps(n), doc.dockApps, "n=$n: no app is dropped")
            assertEquals(true, doc.dockTitle, "n=$n: the name stays")
            val back = assertIs<DecodeResult.Ok>(LauncherConfigCodec.decode(LauncherConfigCodec.encode(doc))).doc
            assertEquals(apps(n), DockLayers.decode(back.dockApps.orEmpty(), { true }, DockLayers.columnsOf(back)))
        }
        assertEquals(1, DockLayers.applyTo(cinema.copy(cinematicHome = false), apps(8)).rowsBelowDock, "the other looks count the rows")
        assertEquals(1, DockLayers.applyTo(cinema.copy(cinematicHome = null), apps(8)).rowsBelowDock)
    }

    // An empty dock writes no dock_apps (the launcher keeps its own) and leaves the theme's rows.
    @Test fun anEmptyDockLeavesTheLauncherItsOwn() {
        val doc = DockLayers.applyTo(LauncherConfigDoc(dockApps = apps(2), rowsBelowDock = 1), emptyList())
        assertNull(doc.dockApps)
        assertEquals(1, doc.rowsBelowDock)
    }

    @Test fun columnsFollowTheLauncherRange() {
        assertEquals(4, DockLayers.columnsOf(null))
        assertEquals(4, DockLayers.columnsOf(LauncherConfigDoc()))
        assertEquals(6, DockLayers.columnsOf(LauncherConfigDoc(columns = 6)))
        assertEquals(4, DockLayers.columnsOf(LauncherConfigDoc(columns = 9)))
        assertEquals(4, DockLayers.columnsOf(LauncherConfigDoc(columns = 0)))
    }

    // Decoding skips what the TV cannot show, so later apps move up a slot, as on the TV.
    @Test fun decodeSkipsAppsTheTvDoesNotShow() {
        val all = apps(7)
        val gone = setOf(all[1], all[5])
        val shown = DockLayers.decode(all, { it !in gone }, 4)
        assertEquals(listOf(all[0], all[2], all[3], all[4], all[6]), shown)
        assertEquals(listOf(listOf(all[0], all[2], all[3], all[4]), listOf(all[6])), DockLayers.rows(shown, 4))
        assertEquals(apps(12), DockLayers.decode(apps(15), { true }, 4))
    }

    // Encode, then read the bytes back the launcher's way: the same list and the same rows.
    @Test fun roundTripThroughTheConfigFile() {
        for (n in 0..13) for (columns in listOf(4, 6)) {
            val theme = LauncherConfigDoc(columns = columns.takeIf { it != 4 })
            val doc = DockLayers.applyTo(theme, apps(n))
            val back = assertIs<DecodeResult.Ok>(LauncherConfigCodec.decode(LauncherConfigCodec.encode(doc))).doc
            assertEquals(doc, back)
            val decoded = DockLayers.decode(back.dockApps.orEmpty(), { true }, DockLayers.columnsOf(back))
            assertEquals(DockLayers.trim(apps(n), columns), decoded)
            if (n > 0) assertEquals(DockLayers.rows(decoded, columns).size - 1, back.rowsBelowDock)
        }
    }

    // 0.4.0 Apps in a row, 4 to 8, over every look and dock length: the override wins, the rows and
    // dock_title follow it, not the look, and Cinema's shelf keeps rows_below_dock 0 whatever it is.
    @Test fun appsInARowDecidesTheRowsForEveryLookAndDockLength() {
        val looks = BundledThemeSource().bundled()
        for (look in looks) for (columns in DockLayers.MIN_OFFERED_COLUMNS..DockLayers.MAX_OFFERED_COLUMNS) for (n in 0..24) {
            val doc = DockLayers.applyTo(look.doc, apps(n), columns)
            val label = "${look.id} at $columns with $n apps"
            assertEquals(columns, DockLayers.columnsOf(doc), label)
            val kept = apps(n).take(DockLayers.capacity(columns))
            if (n == 0) { assertNull(doc.dockApps, label); continue }
            assertEquals(kept, doc.dockApps, label)
            val rows = DockLayers.rowsBelowDock(kept.size, columns)
            if (look.doc.cinematicHome == true) {
                assertEquals(0, doc.rowsBelowDock, "$label: the shelf keeps the rows out of view")
            } else {
                assertEquals(rows, doc.rowsBelowDock, label)
                if (rows == 2) assertEquals(false, doc.dockTitle, "$label: three rows turn the big name off")
            }
            if (rows < 2 || look.doc.cinematicHome == true) assertEquals(look.doc.dockTitle, doc.dockTitle, "$label: the look's own big name")
        }
    }

    // The look's own count, a value outside 1 to 8 and null all leave the doc's columns as they are,
    // so a choice with no override sends exactly the look's bytes.
    @Test fun theLooksOwnCountIsNoOverride() {
        for (look in BundledThemeSource().bundled()) {
            val own = DockLayers.columnsOf(look.doc)
            for (c in listOf(null, own, 0, 9, -1)) assertEquals(DockLayers.applyTo(look.doc, apps(6)), DockLayers.applyTo(look.doc, apps(6), c), "${look.id} $c")
        }
        assertEquals(null, DockLayers.applyTo(LauncherConfigDoc(), apps(6), 4).columns, "Default's own 4 is not written")
        assertEquals(8, DockLayers.applyTo(LauncherConfigDoc(), apps(6), 8).columns)
    }

    // The saved list (HomeChoice.dock) keeps up to 24 apps, the rows of 8 apps in a row, in order and
    // without repeats, so a smaller row never drops an app the person put in the dock.
    @Test fun keepNeverDropsAnAppBelowTwentyFour() {
        assertEquals(8, DockLayers.MAX_OFFERED_COLUMNS)
        assertEquals(24, DockLayers.capacity(DockLayers.MAX_OFFERED_COLUMNS))
        for (n in 0..24) assertEquals(apps(n), DockLayers.keep(apps(n)), "$n apps")
        assertEquals(apps(24), DockLayers.keep(apps(30)))
        assertEquals(apps(5), DockLayers.keep(apps(5) + apps(3)), "repeats are dropped")
    }

    @Test fun waitingCountsTheAppsBeyondThreeRows() {
        assertEquals(0, DockLayers.waiting(12, 4))
        assertEquals(3, DockLayers.waiting(15, 4))
        assertEquals(0, DockLayers.waiting(15, 5))
        assertEquals(12, DockLayers.waiting(24, 4))
        assertEquals(0, DockLayers.waiting(24, 8))
        assertEquals(0, DockLayers.waiting(0, 7))
        for (c in 4..8) for (n in 0..24) assertEquals(maxOf(0, n - 3 * c), DockLayers.waiting(n, c))
    }
}
