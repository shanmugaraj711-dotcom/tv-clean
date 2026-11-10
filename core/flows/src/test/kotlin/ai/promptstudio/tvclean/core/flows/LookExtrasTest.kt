package ai.promptstudio.tvclean.core.flows

import kotlinx.coroutines.test.runTest
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.spec.BandChoice
import ai.promptstudio.tvclean.core.spec.BundledThemeSource
import ai.promptstudio.tvclean.core.spec.ButtonsChoice
import ai.promptstudio.tvclean.core.spec.GlowChoice
import ai.promptstudio.tvclean.core.spec.HighlightChoice
import ai.promptstudio.tvclean.core.spec.LauncherConfigCodec
import ai.promptstudio.tvclean.core.spec.LookExtras
import ai.promptstudio.tvclean.core.spec.LookExtrasKeys
import ai.promptstudio.tvclean.core.spec.Movement
import ai.promptstudio.tvclean.core.spec.StyleId
import ai.promptstudio.tvclean.core.spec.TilesChoice
import ai.promptstudio.tvclean.testing.fixtures.FakeClock
import ai.promptstudio.tvclean.testing.fixtures.MemoryDocumentStore
import ai.promptstudio.tvclean.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// 0.4.5 (plan WP5 steps 5 to 8): the home screen 3.0 switches through the rules, the drafts, home.txt
// and the preview. A TV below 3.0 never gets a 3.0 key; a 0.4.0 home.txt loads as DEFAULT and saves
// again byte for byte.
class LookExtrasTest {
    private val looks = BundledThemeSource()
    private val drafts = LookDrafts(looks, looks, ConfigEncoder.Codec, FakeClock(Samples.NOW_MS))
    private fun apps(n: Int) = List(n) { AppChoice(PackageName("com.example.app$it"), "App $it") }
    private val tv30 = LookTv(9L, 2048, true)
    private val tv27 = LookTv(8L, 2048, true)
    private fun ctx(n: Int = 4, tv: LookTv = tv30) = LookContext(apps(n), tv = tv)

    private fun choice(style: StyleId, columns: Int = 5, details: LookDetails = LookDetails(), extras: LookExtras = LookExtras.DEFAULT) =
        HomeChoice(emptyList(), null, picture = "mist", columns = columns, style = style.id, movement = Movement.LIVELY.id, lift = false,
            names = false, details = details, extras = extras)

    private fun open(style: StyleId, n: Int = 4, tv: LookTv = tv30, columns: Int = 5, details: LookDetails = LookDetails(),
        extras: LookExtras = LookExtras.DEFAULT) = drafts.open(choice(style, columns, details, extras), ctx(n, tv))

    private fun LookDraft.edit(vararg e: LookEdit, n: Int = 4, tv: LookTv = tv30): LookDraft = e.fold(this) { d, x -> drafts.edit(d, x, ctx(n, tv)) }

    private fun every(): Sequence<LookExtras> = sequence {
        val tf = listOf(false, true)
        for (a in tf) for (b in tf) for (c in tf) for (d in tf) for (e in tf) for (f in tf) for (g in tf)
            for (t in TilesChoice.entries) for (h in HighlightChoice.entries) yield(LookExtras(a, b, c, d, e, f, g, t, h))
    }

    private fun rules(style: StyleId, extras: LookExtras, tv: LookTv = tv30, details: LookDetails = LookDetails()): LookExtras {
        val p = LookParts(style, "mist", 5, details = details, extras = extras)
        val pic = drafts.pictureOf("mist", ctx())
        return LookRules.extras(p, LookRules.rows(p, pic, 4, tv), tv)
    }

    private fun bytes(style: StyleId, extras: LookExtras, tv: LookTv, n: Int = 4, columns: Int = 5, details: LookDetails = LookDetails()): String {
        val (doc, _) = drafts.docOf(choice(style, columns, details, extras), apps(n).map { it.pkg }, cards = false, mine = null, tv = tv)
        return LauncherConfigCodec.encode(doc).decodeToString()
    }

    // Step 5.
    @Test fun theRulesTakeWhatActsInThisLook() {
        val all = LookExtras(oneRow = true, bandAll = true, noBand = true, hideGreeting = true, dateBelow = true, brightDate = true,
            wifiWarnings = true, tiles = TilesChoice.ICONS, highlight = HighlightChoice.HALO)
        assertEquals(all.copy(oneRow = false, bandAll = false, noBand = false, wifiWarnings = false), rules(StyleId.ALL_APPS, all), "All apps")
        assertEquals(all.copy(noBand = false, wifiWarnings = false), rules(StyleId.SPOTLIGHT, all), "Spotlight: no band choice, one row and its band act")
        assertEquals(all.copy(bandAll = false), rules(StyleId.GLASS, all), "Glass: no band, so no band behind every app")
        assertEquals(all.copy(noBand = false), rules(StyleId.GLASS, all.copy(noBand = false)), "Glass with its band")
        assertEquals(LookExtras(), rules(StyleId.CLASSIC, LookExtras(bandAll = true)), "no one row, no band behind every app")
        assertEquals(LookExtras(), rules(StyleId.CLASSIC, LookExtras(wifiWarnings = true)), "Classic's Wi-Fi is not green")
        assertEquals(LookExtras(wifiWarnings = true), rules(StyleId.CLASSIC, LookExtras(wifiWarnings = true), details = LookDetails(greenWifi = true)))
        assertEquals(LookExtras(), rules(StyleId.GLASS, LookExtras(wifiWarnings = true), details = LookDetails(buttons = ButtonsChoice.MENU)), "one menu button")
        for (e in every()) for (style in StyleId.entries) {
            assertEquals(LookExtras.DEFAULT, rules(style, e, tv27), "a 2.7 TV: $style $e")
            assertEquals(LookExtras.DEFAULT, rules(style, e, LookTv(5L, 1024, false)), "an older TV: $style $e")
        }
    }

    // With reads30 false the push is the DEFAULT one, byte for byte, for every switch on every style.
    @Test fun aTvBelow30NeverGetsA30Key() {
        for (style in StyleId.entries) for (n in listOf(4, 15)) {
            val plain = bytes(style, LookExtras.DEFAULT, tv27, n, columns = 4)
            for (e in every()) {
                val got = bytes(style, e, tv27, n, columns = 4)
                assertEquals(plain, got, "$style with $n apps, $e")
                for (k in LookExtrasKeys.KEYS) assertFalse("\"$k\"" in got, "$k on a 2.7 TV")
            }
        }
        // The same choice on a 3.0 TV does write them.
        assertTrue("\"single_row\": true" in bytes(StyleId.CLASSIC, LookExtras(oneRow = true), tv30))
        assertTrue("\"single_row\": true" in bytes(StyleId.CLASSIC, LookExtras(oneRow = true), LookTv()), "not known: the bundled 3.0")
    }

    // One row on a full dock: rows 0, and the big name is not taken by the three-row rule.
    @Test fun oneRowKeepsTheBigName() {
        val three = open(StyleId.CLASSIC, n = 12, columns = 4)
        assertEquals(LookRow(false, false, LookReason.THREE_ROWS), three.rows?.bigName)
        val one = three.edit(LookEdit.SetExtras(LookExtras(oneRow = true)), n = 12)
        assertEquals(LookRow(true, true), one.rows?.bigName)
        assertTrue("\"rows_below_dock\": 0" in one.fullFile && "\"dock_title\": true" in one.fullFile, one.fullFile)
        val back = one.edit(LookEdit.SetExtras(LookExtras()), n = 12)
        assertEquals(three.fullFile, back.fullFile, "one row off again: the dock's rows and the three-row rule")
    }

    // Step 7.
    @Test fun theDraftCarriesTheSwitches() {
        val d = open(StyleId.CLASSIC)
        assertTrue(d.reads30)
        val e = LookExtras(oneRow = true, hideGreeting = true, highlight = HighlightChoice.RIM)
        val set = d.edit(LookEdit.SetExtras(e))
        assertEquals(e, set.extras)
        assertTrue(set.changed)
        assertEquals(WayBack.UNDO, set.wayBack)
        assertEquals(3, assertNotNull(set.rows).resettable, "three switches count as changes")
        for (k in listOf("\"single_row\": true", "\"show_greeting\": false", "\"glass_style\": \"rim\"")) assertTrue(k in set.fullFile, k)
        // A style change keeps them; the rules take what acts.
        val all = set.edit(LookEdit.SelectStyle(StyleId.ALL_APPS))
        assertEquals(e, all.extras)
        assertFalse("single_row" in all.fullFile)
        assertEquals(2, assertNotNull(all.rows).resettable, "one row does not act in All apps")
        // Use {style}'s own settings puts them back, and its note brings them back.
        val reset = set.edit(LookEdit.ResetDetails)
        assertEquals(LookExtras.DEFAULT, reset.extras)
        assertEquals(d.fullFile, reset.fullFile)
        val note = assertNotNull(reset.note)
        assertEquals(e, reset.edit(LookEdit.NoteAction(note.id)).extras)
        // Undo changes goes back to what was sent.
        assertEquals(LookExtras.DEFAULT, set.edit(LookEdit.UndoChanges).extras)
        // The saved choice keeps them, and opens with them.
        val saved = drafts.choiceOf(set, null, apps(4).map { it.pkg }, cards = false, picture = "mist", dim = null)
        assertEquals(e, saved.extras)
        val reopened = drafts.open(saved, ctx())
        assertEquals(e, reopened.extras)
        assertEquals(set.fullFile, reopened.fullFile)
        assertFalse(reopened.changed)
        // On a 2.7 TV the edit does nothing, the draft shows DEFAULT and no 3.0 rows.
        val old = open(StyleId.CLASSIC, tv = tv27)
        assertFalse(old.reads30)
        assertEquals(old, old.edit(LookEdit.SetExtras(e), tv = tv27))
        assertEquals(LookExtras.DEFAULT, drafts.open(saved, ctx(tv = tv27)).extras)
    }

    // Put back the earlier look carries its switches.
    @Test fun theEarlierLookCarriesTheSwitches() {
        val e = LookExtras(dateBelow = true, tiles = TilesChoice.ICONS)
        val earlier = EarlierLook.Saved(choice(StyleId.CLASSIC, extras = e), null)
        val d = drafts.open(choice(StyleId.GLASS), ctx().copy(earlier = earlier, sent = choice(StyleId.GLASS)))
        assertEquals(WayBack.EARLIER, d.wayBack)
        val back = drafts.edit(d, LookEdit.PutBackEarlier, ctx().copy(earlier = earlier, sent = choice(StyleId.GLASS)))
        assertEquals(StyleId.CLASSIC to e, back.style to back.extras)
    }

    // Step 8: the preview reads the switches back from the bytes.
    @Test fun thePreviewReadsTheSwitchesFromTheBytes() {
        val d = open(StyleId.CLASSIC)
        val p0 = assertNotNull(d.preview)
        assertEquals(listOf(false, false, true, false, false, false, HighlightChoice.GLASS),
            listOf(p0.oneRow, p0.bandAll, p0.greetingShown, p0.dateBelow, p0.brightDate, p0.iconTiles, p0.highlight))
        assertEquals(4, p0.dockArgb.size)
        val p = assertNotNull(d.edit(LookEdit.SetExtras(LookExtras(oneRow = true, bandAll = true, hideGreeting = true, dateBelow = true,
            brightDate = true, tiles = TilesChoice.ICONS, highlight = HighlightChoice.HALO))).preview)
        assertEquals(listOf(true, true, false, true, true, true, HighlightChoice.HALO),
            listOf(p.oneRow, p.bandAll, p.greetingShown, p.dateBelow, p.brightDate, p.iconTiles, p.highlight))
        assertEquals(PreviewBand.SOLID, p.band)
        assertEquals(PreviewBand.NONE, assertNotNull(d.edit(LookEdit.SetExtras(LookExtras(noBand = true))).preview).band)
        // All apps: nothing of one row reaches the bytes, so the preview has none of it.
        val all = assertNotNull(open(StyleId.ALL_APPS, extras = LookExtras(oneRow = true, bandAll = true)).preview)
        assertFalse(all.oneRow || all.bandAll)
    }

    // Step 6: home.txt.
    @Test fun aHomeTxtOf040LoadsAsDefaultAndSavesAgainByteForByte() = runTest {
        val files = listOf(
            "dock=com.netflix.ninja\ntheme=\n",
            "dock=com.netflix.ninja,com.google.android.youtube.tv\ntheme=aurora\ncards=true\n",
            "dock=com.netflix.ninja\ntheme=diwali\ncards=false\nprevious=horizon\n",
            "dock=com.netflix.ninja\ntheme=cinema\ncards=false\npicture=mine\ndim=40\n",
            "dock=com.netflix.ninja\ntheme=console\ncards=false\npicture=glass\ncolumns=5\n",
            "dock=com.netflix.ninja,com.google.android.youtube.tv\ntheme=\ncards=true\npicture=diwali\ncolumns=8\nstyle=spotlight\nmovement=quick\n" +
                "lift=true\nnames=true\nprevpic=mist\nd.bigname=false\nd.glow=top\nd.spotcolour=false\nd.ring=true\nd.band=see\nd.see=70\n" +
                "d.buttons=menu\nd.wifi=true\nd.dim=40\nd.sky=true\nd.skystr=85\n",
            "dock=com.netflix.ninja,com.google.android.youtube.tv\ntheme=\npicture=none\ncolumns=4\nstyle=classic\nmovement=lively\nlift=false\nnames=false\n",
        )
        val id = Samples.miTvId()
        for (text in files) {
            val docs = MemoryDocumentStore()
            docs.write("tvs/${id.value}/home.txt", text.encodeToByteArray())
            val prefs = HomePrefs(docs)
            val c = assertNotNull(prefs.load(id), text)
            assertEquals(LookExtras.DEFAULT, c.extras, text)
            prefs.save(id, c)
            assertEquals(text, docs.read("tvs/${id.value}/home.txt") { it.decodeToString() }, "saved again byte for byte")
        }
    }

    @Test fun theSwitchesRoundTripInHomeTxt() {
        val base = choice(StyleId.GLASS)
        for (e in every()) {
            val text = HomePrefs.encode(base.copy(extras = e))
            assertEquals(base.copy(extras = e), HomePrefs.decode(text), text)
            assertEquals(LookExtras.DEFAULT == e, "\nx." !in text, text)
        }
        val all = HomePrefs.encode(base.copy(extras = LookExtras(true, true, true, true, true, true, true, TilesChoice.ICONS, HighlightChoice.RIM)))
        assertTrue(all.endsWith("x.onerow=1\nx.bandall=1\nx.noband=1\nx.nogreeting=1\nx.datebelow=1\nx.brightdate=1\nx.wifiwarn=1\nx.tiles=icons\nx.highlight=rim\n"), all)
        assertTrue(HomePrefs.encode(base.copy(extras = LookExtras(highlight = HighlightChoice.HALO))).endsWith("\nx.highlight=halo\n"))
        // A value that does not read is the launcher's own.
        val bad = HomePrefs.decode(HomePrefs.encode(base) + "x.onerow=yes\nx.noband=true\nx.tiles=big\nx.highlight=glow\nx.wifiwarn=0\n")
        assertEquals(LookExtras.DEFAULT, bad.extras)
        // The earlier look's file is the same form.
        assertEquals(EarlierLook.lookOf(base.copy(extras = LookExtras(oneRow = true))) == EarlierLook.lookOf(base), false)
        assertEquals(GlowChoice.FROM_TOP, HomePrefs.decode(HomePrefs.encode(base.copy(details = LookDetails(glow = GlowChoice.FROM_TOP, band = BandChoice.GLASS)))).details.glow)
    }
}
