package ai.promptstudio.tvclean.core.flows

import ai.promptstudio.tvclean.core.model.BlobRef
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.Sha256
import ai.promptstudio.tvclean.core.spec.BandChoice
import ai.promptstudio.tvclean.core.spec.BundledThemeSource
import ai.promptstudio.tvclean.core.spec.ButtonsChoice
import ai.promptstudio.tvclean.core.spec.GlowChoice
import ai.promptstudio.tvclean.core.spec.LookKeys
import ai.promptstudio.tvclean.core.spec.Movement
import ai.promptstudio.tvclean.core.spec.StyleId
import ai.promptstudio.tvclean.testing.fixtures.FakeClock
import ai.promptstudio.tvclean.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// look2 rules (spec 6 to 8, the prototype's rowsOf, chooseStyle and autoPick), on the drafts both
// machines make (LookDrafts), with the bundled pictures.
class LookChoiceTest {
    private val looks = BundledThemeSource()
    private val drafts = LookDrafts(looks, looks, ConfigEncoder.Codec, FakeClock(Samples.NOW_MS))
    private fun apps(n: Int) = List(n) { AppChoice(PackageName("com.example.app$it"), "App $it") }
    private val tv2gb = LookTv(8L, 2048, true)
    private fun ctx(n: Int = 4, tv: LookTv = tv2gb) = LookContext(apps(n), tv = tv)

    private fun choice(style: StyleId, picture: String = "mist", columns: Int = 5, details: LookDetails = LookDetails(), movement: Movement? = Movement.LIVELY,
        lift: Boolean = false, names: Boolean = false) = HomeChoice(emptyList(), null, picture = picture, columns = columns, style = style.id,
        movement = movement?.id, lift = lift, names = names, details = details)

    private fun open(style: StyleId, n: Int = 4, tv: LookTv = tv2gb, picture: String = "mist", columns: Int = 5, details: LookDetails = LookDetails(),
        movement: Movement? = Movement.LIVELY, lift: Boolean = false) = drafts.open(choice(style, picture, columns, details, movement, lift), ctx(n, tv))

    private fun LookDraft.edit(vararg e: LookEdit, n: Int = 4, tv: LookTv = tv2gb): LookDraft = e.fold(this) { d, x -> drafts.edit(d, x, ctx(n, tv)) }
    private fun LookDraft.rowsOf(): LookRows = assertNotNull(rows)

    @Test fun theRowsSayWhyTheyAreDisabled() {
        val all = open(StyleId.ALL_APPS).rowsOf()
        assertEquals(LookRow(false, false, LookReason.ALL_APPS_NO_BIG_NAME), all.bigName)
        assertEquals(LookRow<BandChoice>(null, false, LookReason.ALL_APPS_NO_BAND), all.band, "nothing checked")
        val spot = open(StyleId.SPOTLIGHT).rowsOf()
        assertEquals(LookRow(true, false, LookReason.SPOTLIGHT_BIG_NAME), spot.bigName)
        assertEquals(LookRow(BandChoice.SEE_THROUGH, false, LookReason.SPOTLIGHT_BAND), spot.band)
        assertEquals(false, spot.glow.visible)
        assertEquals(true to true, spot.spotColour.visible to spot.spotColour.value, "App colour across the top, on by default")
        assertEquals(false, open(StyleId.CLASSIC).rowsOf().spotColour.visible)
        assertEquals(LookRow(false, false, LookReason.THREE_ROWS), open(StyleId.CLASSIC, n = 12, columns = 4).rowsOf().bigName)
        assertEquals(true, open(StyleId.SPOTLIGHT, n = 12, columns = 4).rowsOf().bigName.value, "the shelf keeps rows 0")
        val menu = open(StyleId.GLASS, details = LookDetails(buttons = ButtonsChoice.MENU)).rowsOf()
        assertEquals(LookRow(true, false, LookReason.MENU_NO_WIFI), menu.greenWifi, "the style's own, disabled")
        val none = open(StyleId.CLASSIC, picture = LookPicture.NONE_ID).rowsOf()
        assertEquals(LookRow(0, false, LookReason.NO_PICTURE), none.dim)
        val quick = open(StyleId.CLASSIC, movement = Movement.QUICK, lift = true).rowsOf()
        assertEquals(LookRow(false, false, LookReason.LIFT_MOVEMENT), quick.lift)
        val classic = open(StyleId.CLASSIC).rowsOf()
        assertEquals(LookRow(50, false, LookReason.SEE_THROUGH_OFF), classic.seeThrough)
        assertEquals(LookRow(55, false, LookReason.SKY_OFF), classic.skyStrength)
        val see = open(StyleId.CLASSIC, details = LookDetails(band = BandChoice.SEE_THROUGH, seeThrough = 30)).rowsOf()
        assertEquals(LookRow(30, true), see.seeThrough)
        assertEquals(LookRow(true, true), open(StyleId.CLASSIC, picture = "mountains").rowsOf().sky, "Mountains has its own sky")
        assertEquals(true, open(StyleId.CLASSIC, picture = "diwali").rowsOf().festiveRing)
        assertEquals(false, classic.festiveRing)
    }

    @Test fun anOlderHomeScreenGetsClassicOnly() {
        val tv = LookTv(5L, 2048, true)
        val d = open(StyleId.GLASS, tv = tv, details = LookDetails(ring = true))
        assertEquals(StyleId.CLASSIC, d.style, "a style the TV cannot show is Classic")
        assertEquals(listOf(StyleId.CLASSIC), d.styles)
        assertEquals(false, d.reads27)
        assertEquals(listOf(true, false, false, false, false), d.styleCards.map { it.enabled })
        val r = d.rowsOf()
        for (row in listOf(r.names, r.glow, r.ring, r.band, r.buttons, r.greenWifi)) assertEquals(LookReason.NEEDS_UPDATE, row.reason, row.toString())
        assertEquals(true, r.bigName.enabled, "dock_title is older than 2.7")
        assertEquals(d, d.edit(LookEdit.SelectStyle(StyleId.GLASS), LookEdit.SetNames(true), LookEdit.SetRing(true), tv = tv), "nothing changes")
        assertTrue("\"always_show_names\": false" in d.settingsFile)
    }

    @Test fun liftAndZoomHidesBelow2GbUnlessItIsOn() {
        val low = LookTv(8L, 1024, true)
        assertEquals(false, drafts.open(choice(StyleId.CLASSIC), ctx(tv = low)).rowsOf().lift.visible)
        val on = drafts.open(choice(StyleId.CLASSIC, lift = true), ctx(tv = low)).rowsOf()
        assertEquals(true, on.lift.visible)
        assertEquals(1024, on.liftSlowRamMb)
        assertEquals(true, open(StyleId.CLASSIC, tv = LookTv(8L, null, true)).rowsOf().lift.visible, "memory not known")
        assertNull(open(StyleId.CLASSIC, lift = true).rowsOf().liftSlowRamMb)
    }

    // 8.1: the auto pick only fills a Movement nobody chose.
    @Test fun theMemoryPickNeverOverridesAChosenMovement() {
        val low = LookTv(8L, 1536, true)
        val unchosen = drafts.open(null, ctx(tv = low))
        assertEquals(Movement.QUICK to true, unchosen.movement to unchosen.movementPicked)
        assertEquals(StyleId.CLASSIC to LookPicture.NONE_ID, unchosen.style to unchosen.picture, "Setup's Default look")
        assertEquals(4, unchosen.columns)
        val chosen = drafts.open(choice(StyleId.CLASSIC, movement = Movement.LIVELY), ctx(tv = low))
        assertEquals(Movement.LIVELY to false, chosen.movement to chosen.movementPicked)
        val none = drafts.open(choice(StyleId.CLASSIC, movement = null, lift = true), ctx(tv = low))
        assertEquals(Triple(Movement.QUICK, false, true), Triple(none.movement, none.lift, none.movementPicked))
        val picked = none.edit(LookEdit.SetMovement(Movement.SMOOTH), tv = low)
        assertEquals(Movement.SMOOTH to false, picked.movement to picked.movementPicked)
        assertEquals(Movement.LIVELY, drafts.open(null, ctx(tv = LookTv(8L, null, true))).movement, "memory not known")
    }

    // "Use {style}'s own settings" counts and puts back every row and Movement, with Undo.
    @Test fun ownSettingsCountsAndResetsEveryRow() {
        val d = open(StyleId.CLASSIC, picture = "mountains")
        assertEquals(0, d.rowsOf().resettable)
        assertEquals(d, d.edit(LookEdit.ResetDetails), "nothing to reset")
        val changed = d.edit(LookEdit.SetGlow(GlowChoice.FROM_TOP), LookEdit.SetRing(true), LookEdit.SetBand(BandChoice.SEE_THROUGH), LookEdit.SetSeeThrough(70),
            LookEdit.SetDim(20), LookEdit.SetSkyStrength(80), LookEdit.SetMovement(Movement.SMOOTH), LookEdit.SetLift(true), LookEdit.SetGlow(GlowChoice.SOFT))
        assertEquals(7, changed.rowsOf().resettable, "ring, band, see-through, dim, sky strength, movement, lift (the glow is its own again)")
        val reset = changed.edit(LookEdit.ResetDetails)
        assertEquals(LookDetails(), reset.details)
        assertEquals(Movement.LIVELY to false, reset.movement to reset.lift)
        assertEquals(0, reset.rowsOf().resettable)
        val note = assertNotNull(reset.note)
        assertEquals(LookNoteKind.RESET to true, note.kind to note.action)
        val undone = reset.edit(LookEdit.NoteAction(note.id))
        assertEquals(changed.details to changed.movement, undone.details to undone.movement)
        assertNull(undone.note)
        val low = LookTv(8L, 1024, true)
        assertEquals(Movement.QUICK, open(StyleId.CLASSIC, tv = low, movement = Movement.LIVELY).edit(LookEdit.ResetDetails, tv = low).movement,
            "Movement goes back to the TV's default")
    }

    // A style change puts back the new style's own details; the note says what changed and Keep
    // mine puts back the rows that still apply.
    @Test fun aStyleChangeResetsTheDetailsWithKeepMine() {
        val d = open(StyleId.CLASSIC).edit(LookEdit.SetGlow(GlowChoice.NONE), LookEdit.SetRing(true), LookEdit.SetBand(BandChoice.GLASS),
            LookEdit.SetButtons(ButtonsChoice.ROUND), LookEdit.SetNames(true), LookEdit.SelectColumns(7), LookEdit.SetMovement(Movement.SMOOTH))
        val all = d.edit(LookEdit.SelectStyle(StyleId.ALL_APPS))
        assertEquals(LookDetails(), all.details)
        assertEquals(Triple(true, 7, Movement.SMOOTH), Triple(all.names, all.columns, all.movement), "names, apps in a row and movement belong to nobody")
        val note = assertNotNull(all.note)
        assertEquals(LookNoteKind.STYLE_CHANGED, note.kind)
        assertEquals(StyleId.ALL_APPS, note.style)
        assertEquals(listOf(DetailWord.GLOW, DetailWord.RING, DetailWord.BUTTONS), note.own)
        assertEquals(listOf(DetailWord.DOCK_BAND), note.dropped)
        assertEquals(true, note.action)
        val mine = all.edit(LookEdit.NoteAction(note.id))
        assertEquals(LookDetails(glow = GlowChoice.NONE, ring = true, buttons = ButtonsChoice.ROUND), mine.details)
        // Spotlight has its own glow: nothing to keep.
        val spot = open(StyleId.CLASSIC).edit(LookEdit.SetGlow(GlowChoice.NONE), LookEdit.SelectStyle(StyleId.SPOTLIGHT))
        assertEquals(LookNote(spot.note!!.id, LookNoteKind.STYLE_CHANGED, StyleId.SPOTLIGHT, listOf(DetailWord.GLOW), emptyList(), false), spot.note)
        // No changed detail: no note.
        assertNull(open(StyleId.CLASSIC).edit(LookEdit.SelectStyle(StyleId.GLASS)).note)
        // Seen: the note goes and its action with it.
        val seen = all.edit(LookEdit.NoteSeen(note.id))
        assertNull(seen.note)
        assertEquals(seen, seen.edit(LookEdit.NoteAction(note.id)))
    }

    @Test fun aPictureChangeResetsDimAndSky() {
        val d = open(StyleId.GLASS).edit(LookEdit.SetDim(40), LookEdit.SetSky(true), LookEdit.SetSkyStrength(90), LookEdit.SetRing(false))
        assertEquals(LookDetails(ring = false, dim = 40, sky = true, skyStrength = 90), d.details)
        val next = d.edit(LookEdit.SelectPicture("aurora"))
        assertEquals(LookDetails(ring = false), next.details, "the style's detail stays")
        assertEquals(StyleId.GLASS, next.style)
        assertEquals(next, next.edit(LookEdit.SelectPicture("not-a-picture")), "a picture this phone does not have")
        assertEquals(next, next.edit(LookEdit.SelectPicture(LookPicture.MINE_ID)), "no photo kept")
        assertTrue("\"background_color\": \"#070A14\"" in next.settingsFile, next.settingsFile)
    }

    @Test fun turnItOffTurnsTheGlowOffAndKeepsTheStyle() {
        val glass = open(StyleId.GLASS).edit(LookEdit.TurnGlowOff)
        assertEquals(StyleId.GLASS to GlowChoice.NONE, glass.style to glass.details.glow)
        assertTrue("\"ambient_glow\": false" in glass.settingsFile)
        val spot = open(StyleId.SPOTLIGHT).edit(LookEdit.TurnGlowOff)
        assertEquals(false, spot.details.spotColour)
        assertTrue("\"ambient_glow\": true" in open(StyleId.SPOTLIGHT).settingsFile, "Spotlight's colour across the top is on by default")
    }

    // Undo changes while the draft differs from what was sent; Bring them back puts them back.
    @Test fun undoChangesGoesBackToWhatWasSent() {
        val d = open(StyleId.CLASSIC)
        assertEquals(false to WayBack.NONE, d.changed to d.wayBack)
        assertEquals(d, d.edit(LookEdit.UndoChanges), "nothing to undo")
        val changed = d.edit(LookEdit.SelectStyle(StyleId.CALM), LookEdit.SelectPicture("neon"))
        assertEquals(true to WayBack.UNDO, changed.changed to changed.wayBack)
        val undone = changed.edit(LookEdit.UndoChanges)
        assertEquals(Triple(StyleId.CLASSIC, "mist", false), Triple(undone.style, undone.picture, undone.changed))
        assertEquals(LookNoteKind.UNDONE to true, undone.note?.let { it.kind to it.action })
        val back = undone.edit(LookEdit.NoteAction(undone.note!!.id))
        assertEquals(StyleId.CALM to "neon", back.style to back.picture)
        assertEquals(true, back.changed)
        // Choosing it all back by hand is no change.
        assertEquals(false, back.edit(LookEdit.SelectStyle(StyleId.CLASSIC), LookEdit.SelectPicture("mist")).changed)
    }

    // The earlier look: shown when it exists and differs from what was sent; it fills the draft.
    @Test fun theEarlierLookFillsTheDraft() {
        val sent = choice(StyleId.GLASS, picture = "neon")
        val earlier = EarlierLook.Saved(choice(StyleId.CALM, picture = "aurora", columns = 7, details = LookDetails(dim = 20)), null)
        val c = LookContext(apps(4), earlier = earlier, sent = sent, tv = tv2gb)
        val d = drafts.open(sent, c)
        assertEquals(WayBack.EARLIER, d.wayBack)
        val put = drafts.edit(d, LookEdit.PutBackEarlier, c)
        assertEquals(listOf<Any?>(StyleId.CALM, "aurora", 7, LookDetails(dim = 20)), listOf(put.style, put.picture, put.columns, put.details))
        assertEquals(LookNoteKind.EARLIER_CHOSEN to false, put.note?.let { it.kind to it.action })
        assertEquals(WayBack.UNDO, put.wayBack)
        assertEquals(WayBack.NONE, drafts.open(sent, c.copy(earlier = EarlierLook.Saved(sent, null))).wayBack, "the same as what was sent")
        // The earlier look's photo comes back as the earlier photo, until sent.
        val photo = LookPicture.Of(BlobRef(Sha256("3".repeat(64)), 10, "/blobs/earlier.jpg"), 50)
        val withPhoto = c.copy(earlier = EarlierLook.Saved(choice(StyleId.CLASSIC, picture = LookPicture.MINE_ID).copy(dim = 50), photo))
        val p = drafts.edit(drafts.open(sent, withPhoto), LookEdit.PutBackEarlier, withPhoto)
        assertEquals(LookPicture.EARLIER_ID to true, p.picture to p.newPhoto)
        assertEquals("/blobs/earlier.jpg" to 50, p.preview?.picturePath to p.preview?.dim)
    }

    // Build plan risk table: one edit (the draft, its bytes, its preview and the five style cards)
    // stays well under 50 ms on a 15 app dock.
    @Test fun anEditIsQuick() {
        var d = drafts.open(choice(StyleId.CLASSIC), ctx(n = 15))
        val styles = listOf(StyleId.GLASS, StyleId.SPOTLIGHT, StyleId.ALL_APPS, StyleId.CALM, StyleId.CLASSIC)
        repeat(50) { i -> d = drafts.edit(d, LookEdit.SelectStyle(styles[i % 5]), ctx(n = 15)) }
        val times = (0 until 50).map { i ->
            val t = System.nanoTime()
            d = drafts.edit(d, LookEdit.SelectStyle(styles[i % 5]), ctx(n = 15))
            (System.nanoTime() - t) / 1_000_000.0
        }.sorted()
        assertTrue(times[times.size / 2] < 50.0, "median ${times[times.size / 2]} ms")
    }

    @Test fun theDraftCarriesTheFilesAndCounts() {
        val d = drafts.open(choice(StyleId.CLASSIC, columns = 4), ctx(n = 15))
        assertEquals(15 to 3, d.apps to d.waiting)
        assertTrue("dock_apps" !in d.settingsFile && "module_cards" !in d.settingsFile, d.settingsFile)
        assertTrue("dock_apps" in d.fullFile && "module_cards" in d.fullFile, d.fullFile)
        assertEquals(StyleId.entries, d.styleCards.map { it.style })
        assertEquals(listOf(true, true, true, false, true), d.styleCards.map { it.preview?.dockRow }, "All apps has no band")
        val keys = d.edit(LookEdit.SetKeyNames(true), n = 15)
        assertEquals(true, keys.keyNames)
        assertEquals(d.fullFile, keys.fullFile)
        assertEquals(LookKeys.SEE_THROUGH, listOf(70, 50, 30))
    }
}
