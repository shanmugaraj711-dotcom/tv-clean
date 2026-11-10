package ai.promptstudio.tvclean.core.flows

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import ai.promptstudio.tvclean.core.model.EnabledState
import ai.promptstudio.tvclean.core.model.LauncherState
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.PackageState
import ai.promptstudio.tvclean.core.planner.PlanRequest
import ai.promptstudio.tvclean.core.planner.PlanResult
import ai.promptstudio.tvclean.core.model.BlobRef
import ai.promptstudio.tvclean.core.spec.BandChoice
import ai.promptstudio.tvclean.core.spec.BundledThemeSource
import ai.promptstudio.tvclean.core.spec.ButtonsChoice
import ai.promptstudio.tvclean.core.spec.GlowChoice
import ai.promptstudio.tvclean.core.spec.Movement
import ai.promptstudio.tvclean.core.spec.StyleId
import ai.promptstudio.tvclean.core.spec.DecodeResult
import ai.promptstudio.tvclean.core.spec.DockLayers
import ai.promptstudio.tvclean.core.spec.LauncherConfigCodec
import ai.promptstudio.tvclean.core.spec.LauncherConfigDoc
import ai.promptstudio.tvclean.core.spec.Picture
import ai.promptstudio.tvclean.core.spec.Theme
import ai.promptstudio.tvclean.testing.fixtures.FakeThemeSource
import ai.promptstudio.tvclean.testing.fixtures.Samples
import ai.promptstudio.tvclean.testing.fixtures.sha256Of
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// 0.4.0 looks: a dated look (Diwali, until 2026-11-10) keeps the look the person had before it, is
// offered up to its last day, and afterwards the phone counts the earlier look as the TV's.
class LooksTest {
    private val lastDay = LocalDate.of(2026, 11, 10)
    private fun ms(day: LocalDate) = day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val dock = listOf(PackageName("com.netflix.ninja"), PackageName("com.google.android.youtube.tv"))

    @Test fun aDatedLookKeepsTheLookBeforeIt() {
        val cinema = HomeChoice(dock, "cinema")
        val diwali = cinema.withTheme("diwali")
        assertEquals("cinema", diwali.previousThemeId)
        assertEquals("cinema", diwali.withTheme("diwali").previousThemeId, "picked again: still the one before")
        assertNull(diwali.withTheme("console").previousThemeId, "an undated look forgets it")
        assertNull(HomeChoice(dock, null).withTheme("diwali").previousThemeId, "Default before it")
        assertEquals("diwali", diwali.lookOn(lastDay))
        assertNull(diwali.endedLook(lastDay))
        assertEquals("cinema", diwali.lookOn(lastDay.plusDays(1)))
        assertEquals("diwali", diwali.endedLook(lastDay.plusDays(1)))
        assertEquals("cinema", cinema.lookOn(lastDay.plusYears(1)))
    }

    @Test fun homeTxtKeepsTheEarlierLookAndOldFilesStillLoad() = runTest {
        val h = Harness()
        val prefs = HomePrefs(h.documents)
        val id = h.knownTv().id
        val choice = HomeChoice(dock, "diwali", cards = false, previousThemeId = "horizon")
        prefs.save(id, choice)
        assertEquals(choice, prefs.load(id))
        h.documents.write("tvs/${id.value}/home.txt", "dock=com.netflix.ninja\ntheme=aurora\n".encodeToByteArray())
        assertEquals(HomeChoice(dock.take(1), "aurora"), prefs.load(id))
    }

    private val themes = FakeThemeSource(listOf(
        Theme("cinema", "Cinema", "Curtains", LauncherConfigDoc(columns = 5, cinematicHome = true), null),
        Theme("diwali", "Diwali", "Lamps", LauncherConfigDoc(columns = 5, festival = "diwali", festivalUntil = "2026-11-10"), null),
    ))

    @Test fun afterItsLastDayTheEarlierLookIsTheTvsAndOneTapPutsItBack() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val tv = h.knownTv().copy(launcher = LauncherState(1, Samples.zeroHash, Samples.zeroHash, Samples.zeroHash))
        h.tvs.upsert(tv)
        h.snapshots.save(Samples.snapshot(takenAtMs = Samples.NOW_MS + 1))
        val deps = h.manageDeps(encoder = ConfigEncoder.Codec, themes = themes)
        HomePrefs(h.documents).save(tv.id, HomeChoice(dock, "cinema", cards = false, picture = LookPicture.NONE_ID))

        h.clock.setWall(ms(lastDay))
        val m = HomeScreenMachine(deps, backgroundScope, tv.id)
        settle()
        assertEquals(listOf("cinema", "diwali"), m.state.value.themes.map { it.id })
        m.dispatch(HomeScreenIntent.SetTheme("diwali")); settle()
        assertEquals("diwali", h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().last().doc.festival)
        assertEquals(HomeChoice(dock, "diwali", cards = false, previousThemeId = "cinema", picture = LookPicture.NONE_ID), HomePrefs(h.documents).load(tv.id),
            "the picture chosen for Cinema is kept for when it comes back")
        assertNull(m.state.value.endedLook)

        h.clock.setWall(ms(lastDay.plusDays(1)))
        val later = HomeScreenMachine(deps, backgroundScope, tv.id)
        settle()
        assertEquals(listOf("cinema"), later.state.value.themes.map { it.id }, "no longer offered")
        assertEquals("cinema", later.state.value.themeId)
        assertEquals("Diwali", later.state.value.endedLook)
        val pushes = h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().size
        later.dispatch(HomeScreenIntent.SetTheme("diwali")); settle()
        assertEquals(pushes, h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().size, "an ended look is never pushed")
        later.dispatch(HomeScreenIntent.RestoreMine); settle()
        val back = h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().last().doc
        assertEquals(true, back.cinematicHome)
        assertNull(back.festival)
        assertEquals(false, back.wallpaper, "Cinema comes back with its picture choice (none)")
        assertEquals(HomeChoice(dock, "cinema", cards = false, picture = LookPicture.NONE_ID), HomePrefs(h.documents).load(tv.id))
        assertNull(later.state.value.endedLook)
    }

    private val glass = Picture("glass", "Glass", BlobRef(Samples.zeroHash, 1000, "/blobs/glass.jpg"))
    private val ownPicture = BlobRef(sha256Of("cinema".encodeToByteArray()), 2000, "/blobs/cinema.jpg")
    private val gallery = FakeThemeSource(listOf(
        Theme("quiet", "Quiet black", "Black", LauncherConfigDoc(wallpaper = false, wallpaperDim = 0, wallpaperRotationMinutes = 0, reducedMotion = true), null),
        Theme("cinema", "Cinema", "Curtains", LauncherConfigDoc(wallpaper = true, wallpaperDim = 0, wallpaperRotationMinutes = 0, cinematicHome = true), ownPicture),
        Theme("diwali", "Diwali", "Lamps", LauncherConfigDoc(wallpaper = true, festival = "diwali", festivalUntil = "2026-11-10"), ownPicture),
    ), listOf(glass))

    // A picture over a look that shows none (Quiet black) forces wallpaper on, or it would be
    // pushed and not shown; "No picture" turns it off and sends no wallpaper.jpg; a dated look
    // keeps its own whatever was chosen.
    @Test fun theEncoderForcesWallpaperOnWhenAPictureIsChosen() {
        val quiet = gallery.bundled()[0]
        val (doc, wallpaper) = Look.docOf(quiet, dock, cards = false, LookPicture.of("glass", gallery))
        assertEquals(true, doc.wallpaper)
        assertEquals(0, doc.wallpaperRotationMinutes)
        assertEquals(glass.wallpaper, wallpaper)
        assertEquals(true, doc.reducedMotion, "the rest of the look stays")
        val cinema = gallery.bundled()[1]
        val (none, noFile) = Look.docOf(cinema, dock, cards = false, LookPicture.of(LookPicture.NONE_ID, gallery))
        assertEquals(false, none.wallpaper)
        assertNull(noFile)
        assertEquals(cinema.doc.copy(dockApps = dock, rowsBelowDock = 0, moduleCards = false, fillDock = false) to ownPicture,
            Look.docOf(cinema, dock, cards = false, LookPicture.of(null, gallery)), "the look's own")
        assertEquals(ownPicture, Look.docOf(cinema, dock, cards = false, LookPicture.of("gone", gallery)).second, "a picture no longer there: the look's own")
        val (festive, festiveFile) = Look.docOf(gallery.bundled()[2], dock, cards = false, LookPicture.of("glass", gallery))
        assertEquals(true, festive.wallpaper)
        assertEquals(ownPicture, festiveFile)
    }

    // The Look sheet only chooses; "Put it on the TV" sends the look and the picture as one ApplyTheme,
    // and both are kept for the next push (a new dock keeps the picture). Apps in a row only chooses too.
    // look2: the saved 0.4.0 Cinema opens as its migrated draft (Spotlight; Curtains is not in this
    // gallery, so no picture), on this 1 GB TV with Quick picked; the send writes the new form.
    @Test fun theLookSheetSendsNothingUntilUseThisLook() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val tv = h.knownTv().copy(launcher = LauncherState(1, Samples.zeroHash, Samples.zeroHash, Samples.zeroHash))
        h.tvs.upsert(tv)
        h.snapshots.save(Samples.snapshot(takenAtMs = Samples.NOW_MS + 1))
        val deps = h.manageDeps(encoder = ConfigEncoder.Codec, themes = gallery)
        assertEquals(gallery, deps.pictures)
        HomePrefs(h.documents).save(tv.id, HomeChoice(dock, "cinema", cards = false))
        h.clock.setWall(ms(lastDay))
        val m = HomeScreenMachine(deps, backgroundScope, tv.id)
        settle()
        assertEquals(listOf("glass"), m.state.value.pictures.map { it.id })
        val plansBefore = h.planner.requests.size
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        val opened = assertNotNull(m.state.value.look)
        assertEquals(listOf("cinema", "cinema", LookPicture.NONE_ID), listOf(opened.themeId, opened.legacyThemeId, opened.picture))
        assertEquals(StyleId.SPOTLIGHT, opened.style)
        assertEquals(Movement.QUICK to true, opened.movement to opened.movementPicked, "a 1 GB TV: Quick, picked for it")
        assertEquals(false, opened.changed)
        m.dispatch(HomeScreenIntent.Look(LookEdit.SelectStyle(StyleId.CALM))); settle()
        m.dispatch(HomeScreenIntent.Look(LookEdit.SelectPicture("glass"))); settle()
        val draft = assertNotNull(m.state.value.look)
        assertEquals(listOf("default", null, "glass"), listOf(draft.themeId, draft.legacyThemeId, draft.picture), "an edit ends the migrated look")
        assertEquals(StyleId.CALM, draft.style)
        assertEquals(true, draft.changed)
        assertEquals(WayBack.UNDO, draft.wayBack)
        // The preview is made from the bytes the push would send: the picture on, quick movement.
        val preview = assertNotNull(draft.preview)
        assertEquals("/blobs/glass.jpg", preview.picturePath)
        assertEquals(LookMotion.CALM, preview.motion)
        assertEquals(dock.size, preview.dock.size)
        assertEquals(StyleId.entries, draft.styleCards.map { it.style })
        assertTrue(draft.styleCards.all { it.preview?.picturePath == "/blobs/glass.jpg" }, "every card shows this picture")
        for (n in listOf(4, 8, 6, 3, 9)) { m.dispatch(HomeScreenIntent.Look(LookEdit.SelectColumns(n))); settle() }
        assertEquals(6, m.state.value.look?.columns, "3 and 9 are ignored")
        assertEquals(6, m.state.value.look?.preview?.columns, "the preview follows at once")
        assertEquals(plansBefore, h.planner.requests.size, "no plan of any kind while choosing")
        assertEquals(0, h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().size, "nothing sent while choosing")
        m.dispatch(HomeScreenIntent.UseLook); settle()
        assertNull(m.state.value.look)
        val sent = h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().single()
        assertEquals(true, sent.doc.wallpaper)
        assertEquals(true, sent.doc.reducedMotion)
        assertEquals(6, sent.doc.columns)
        assertEquals(false, sent.doc.fillDock)
        assertEquals(glass.wallpaper, sent.wallpaper)
        assertEquals(6, m.state.value.sent?.columns, "the Look row's mini preview is what was sent")
        val kept = assertNotNull(HomePrefs(h.documents).load(tv.id))
        assertEquals(listOf<Any?>("calm", "glass", 6, "quick", null), listOf(kept.style, kept.picture, kept.columns, kept.movement, kept.themeId))
        assertEquals("glass", m.state.value.pictureId)
        assertEquals(StyleId.CALM to "glass", m.state.value.sentStyle to m.state.value.sentPicture)
        // A new dock later keeps the look and its picture.
        m.dispatch(HomeScreenIntent.SetDock(dock.reversed())); settle()
        assertEquals(glass.wallpaper, h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().last().wallpaper)
        assertEquals("calm", HomePrefs(h.documents).load(tv.id)?.style)
        // Closing the sheet sends nothing.
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        assertEquals(StyleId.CALM to "glass", m.state.value.look?.let { it.style to it.picture })
        m.dispatch(HomeScreenIntent.Look(LookEdit.SelectStyle(StyleId.GLASS))); settle()
        m.dispatch(HomeScreenIntent.CloseLook); settle()
        assertEquals(2, h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().size)
        assertNull(m.state.value.look)
    }

    // look2: every edit of the Look editor changes the draft only; no plan of any kind runs, and
    // "Put it on the TV" sends exactly once.
    @Test fun noLookEditRunsAPlanAndUseLookSendsOnce() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val (m, id) = homeWith(h, pkgs(6), HomeChoice(pkgs(6), "mist", cards = false))
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        val before = h.planner.requests.size
        val edits = listOf(
            LookEdit.SelectStyle(StyleId.GLASS), LookEdit.SelectPicture("aurora"), LookEdit.SelectColumns(6), LookEdit.SetNames(true),
            LookEdit.SetMovement(Movement.SMOOTH), LookEdit.SetLift(true), LookEdit.SetBigName(false), LookEdit.SetGlow(GlowChoice.SOFT),
            LookEdit.SetSpotColour(false), LookEdit.SetRing(false), LookEdit.SetBand(BandChoice.SEE_THROUGH), LookEdit.SetSeeThrough(30),
            LookEdit.SetButtons(ButtonsChoice.MENU), LookEdit.SetGreenWifi(false), LookEdit.SetDim(40), LookEdit.SetSky(true),
            LookEdit.SetSkyStrength(80), LookEdit.TurnGlowOff, LookEdit.ResetDetails, LookEdit.UndoChanges, LookEdit.PutBackEarlier,
            LookEdit.NoteAction(1), LookEdit.NoteSeen(1), LookEdit.SetKeyNames(true), LookEdit.SelectStyle(StyleId.SPOTLIGHT),
        )
        for (e in edits) { m.dispatch(HomeScreenIntent.Look(e)); settle() }
        assertEquals(before, h.planner.requests.size, "no plan of any kind while choosing")
        assertEquals(true, h.manageDeps().appPrefs.load().lookKeyNames, "Show launcher names is kept on the phone")
        m.dispatch(HomeScreenIntent.UseLook)
        m.dispatch(HomeScreenIntent.UseLook)
        settle()
        assertEquals(1, h.pushes().size, "one send")
        assertEquals("spotlight", HomePrefs(h.documents).load(id)?.style)
    }

    @Test fun homeTxtKeepsThePicture() = runTest {
        val h = Harness()
        val id = h.knownTv().id
        val choice = HomeChoice(dock, "quiet", cards = true, picture = "glass")
        HomePrefs(h.documents).save(id, choice)
        assertEquals(choice, HomePrefs(h.documents).load(id))
    }

    // The preview's keys are the ones the TV reads from the pushed bytes, for every bundled look.
    @Test fun thePreviewIsReadBackFromTheBytesThePushSends() {
        val today = LocalDate.of(2026, 10, 25)
        val names = listOf("Netflix", "YouTube")
        for (t in BundledThemeSource().bundled()) {
            val (doc, wallpaper) = Look.docOf(t, dock, cards = true, null)
            val bytes = LauncherConfigCodec.encode(doc)
            val p = LookPreview.of(bytes, wallpaper?.copy(path = "/files/${t.id}.jpg"), names, today, 600)
            val e = LauncherConfigCodec.effective(assertIs<DecodeResult.Ok>(LauncherConfigCodec.decode(bytes)).doc, LauncherConfigCodec.builtInDefaults)
            assertEquals(listOf(e.backgroundColorArgb, e.wallpaperDim, e.skyIntensity, e.columns, e.rowsBelowDock),
                listOf(p.backgroundArgb, p.dim, p.sky, p.columns, p.rowsBelowDock), t.id)
            assertEquals(listOf(e.dockRow, e.dockTitle, e.cinematicHome), listOf(p.dockRow, p.dockTitle, p.cinematic), t.id)
            assertEquals(if (e.wallpaper && wallpaper != null) "/files/${t.id}.jpg" else null, p.picturePath, t.id)
            assertEquals(names, p.dock)
        }
        val diwali = BundledThemeSource().bundled().first { it.id == "diwali" }
        val bytes = LauncherConfigCodec.encode(Look.docOf(diwali, dock, cards = true, null).first)
        val on = LookPreview.of(bytes, null, names, today, 600)
        assertEquals(true, on.festive); assertEquals(0xFFFFB23C.toInt(), on.accentArgb); assertEquals("Happy Diwali", on.greeting)
        assertEquals(LookMotion.CRISP, on.motion)
        val after = LookPreview.of(bytes, null, names, lastDay.plusDays(1), 600)
        assertEquals(false, after.festive); assertEquals("", after.greeting)
    }

    // 0.4.0 pre-release fixes, as the bytes reach the TV. Quiet black and Horizon turn the launcher's
    // glow in the focused app's colour off (ambient_glow, a launcher 2.7 key carried as is), so Quiet
    // is truly black and Horizon's sky is the clock's, not Netflix red. Cinema also turns its shelf's
    // scrim and art down so its curtains show, and keeps rows_below_dock 0 with a long dock so the
    // big name stays clear of the clock, with every app still in dock_apps. The other looks leave
    // the glow as the launcher has it.
    @Test fun theFixedLooksSendWhatTheTvNeeds() {
        val looks = BundledThemeSource().bundled().associateBy { it.id }
        val eight = (1..8).map { PackageName("com.example.app$it") }
        fun sent(id: String, apps: List<PackageName> = dock) =
            LauncherConfigCodec.encode(Look.docOf(looks.getValue(id), apps, cards = false, null).first).decodeToString()
        for (id in listOf("quiet", "horizon", "cinema")) assertTrue("\"ambient_glow\": false" in sent(id), id)
        for (id in listOf("default", "aurora", "six-columns", "console", "diwali", "library", "arcade", "mist")) {
            assertTrue("ambient_glow" !in sent(id), id)
        }
        val cinema = sent("cinema", eight)
        assertTrue("\"cinematic_scrim\": 45" in cinema && "\"cinematic_art\": 20" in cinema, cinema)
        val back = assertIs<DecodeResult.Ok>(LauncherConfigCodec.decode(cinema.encodeToByteArray()))
        assertEquals(emptyList(), back.warnings)
        assertEquals(0, back.doc.rowsBelowDock)
        assertEquals(eight, back.doc.dockApps, "no app is lost")
        assertEquals(1, assertIs<DecodeResult.Ok>(LauncherConfigCodec.decode(sent("mist", eight).encodeToByteArray())).doc.rowsBelowDock,
            "a look without the cinematic shelf still shows the row")
    }

    // "Your photo": the photo the phone made is staged and chosen in the sheet; "Put it on the TV" sends
    // it as wallpaper.jpg with its dim (wallpaper forced on, no rotation) and only then keeps it for
    // this TV in its private files. After the cache lost it, "Restore mine" still sends it, from the
    // private copy.
    @Test fun yourOwnPhotoIsKeptForTheTvAndSentWithItsDim() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val tv = h.knownTv().copy(launcher = LauncherState(1, Samples.zeroHash, Samples.zeroHash, Samples.zeroHash))
        h.tvs.upsert(tv)
        h.snapshots.save(Samples.snapshot(takenAtMs = Samples.NOW_MS + 1))
        val deps = h.manageDeps(encoder = ConfigEncoder.Codec, themes = gallery)
        HomePrefs(h.documents).save(tv.id, HomeChoice(dock, "cinema", cards = false))
        val m = HomeScreenMachine(deps, backgroundScope, tv.id)
        settle()
        assertNull(m.state.value.ownPicture)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(5_000) { (it % 251).toByte() }
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        m.dispatch(HomeScreenIntent.SelectLook("quiet")); settle()
        m.dispatch(HomeScreenIntent.SetOwnPicture(jpeg, 40)); settle()
        assertEquals(LookPicture.NEW_ID, m.state.value.look?.picture)
        assertEquals(40, m.state.value.look?.preview?.dim)
        assertNull(h.documents.read(OwnPicture.path(tv.id)) { it }, "not kept before the TV has it")
        assertEquals(0, h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().size, "nothing sent yet")
        m.dispatch(HomeScreenIntent.UseLook); settle()
        assertEquals(true, jpeg.contentEquals(h.documents.read(OwnPicture.path(tv.id)) { it }), "kept in the private files")
        assertEquals(sha256Of(jpeg), m.state.value.ownPicture?.sha256)
        assertNull(m.state.value.newPicture)
        val sent = h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().single()
        assertEquals(true, sent.doc.wallpaper); assertEquals(40, sent.doc.wallpaperDim); assertEquals(0, sent.doc.wallpaperRotationMinutes)
        assertEquals(sha256Of(jpeg), sent.wallpaper?.sha256)
        val kept = assertNotNull(HomePrefs(h.documents).load(tv.id))
        assertEquals(LookPicture.MINE_ID to 40, kept.picture to kept.dim)
        // Another picture for a while, the cache cleared, then "Restore mine".
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        m.dispatch(HomeScreenIntent.SelectPicture("glass")); settle()
        m.dispatch(HomeScreenIntent.UseLook); settle()
        assertNull(HomePrefs(h.documents).load(tv.id)?.dim, "a gallery picture keeps no dim")
        h.blobs.delete(sha256Of(jpeg))
        val later = HomeScreenMachine(deps, backgroundScope, tv.id)
        settle()
        assertEquals(sha256Of(jpeg), later.state.value.ownPicture?.sha256)
        later.dispatch(HomeScreenIntent.OpenLook); settle()
        later.dispatch(HomeScreenIntent.SelectPicture(LookPicture.MINE_ID)); settle()
        later.dispatch(HomeScreenIntent.UseLook); settle()
        assertEquals(sha256Of(jpeg), h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().last().wallpaper?.sha256)
        // A file that is not a JPEG within 280 KB is refused and the kept one stays.
        later.dispatch(HomeScreenIntent.SetOwnPicture(ByteArray(300_000), 30)); settle()
        assertEquals(true, jpeg.contentEquals(h.documents.read(OwnPicture.path(tv.id)) { it }))
    }

    // A photo picked and then cancelled, or one the TV never got, changes nothing: the kept copy,
    // its dim and "Restore mine" stay the photo on the TV, and the sheet's dim goes back to it.
    @Test fun aNewPhotoIsKeptOnlyOnceTheTvHasIt() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val tv = h.knownTv().copy(launcher = LauncherState(1, Samples.zeroHash, Samples.zeroHash, Samples.zeroHash))
        h.tvs.upsert(tv)
        h.snapshots.save(Samples.snapshot(takenAtMs = Samples.NOW_MS + 1))
        val deps = h.manageDeps(encoder = ConfigEncoder.Codec, themes = gallery)
        fun photo(seed: Int) = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(5_000) { ((it + seed) % 251).toByte() }
        val onTv = photo(1)
        val other = photo(2)
        h.documents.write(OwnPicture.path(tv.id), onTv)
        HomePrefs(h.documents).save(tv.id, HomeChoice(dock, "cinema", cards = false, picture = LookPicture.MINE_ID, dim = 40))
        val m = HomeScreenMachine(deps, backgroundScope, tv.id)
        settle()
        assertEquals(sha256Of(onTv), m.state.value.ownPicture?.sha256)
        assertEquals(40, m.state.value.ownDim)

        // Picked, then the sheet closed.
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        m.dispatch(HomeScreenIntent.SetOwnPicture(other, 60)); settle()
        assertEquals(LookPicture.NEW_ID, m.state.value.look?.picture)
        assertEquals(sha256Of(other), m.state.value.newPicture?.sha256)
        assertEquals(60, m.state.value.look?.preview?.dim)
        m.dispatch(HomeScreenIntent.CloseLook); settle()
        assertNull(m.state.value.newPicture)
        assertEquals(40, m.state.value.newDim, "the unapplied dim is gone")
        assertEquals(40, m.state.value.ownDim)
        assertEquals(true, onTv.contentEquals(h.documents.read(OwnPicture.path(tv.id)) { it }))
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        assertEquals(LookPicture.MINE_ID, m.state.value.look?.picture, "the sheet opens on the photo the TV has")
        assertEquals(40, m.state.value.look?.preview?.dim)
        m.dispatch(HomeScreenIntent.CloseLook); settle()
        assertEquals(0, h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().size)

        // Picked and sent, but the TV refused the push. look2: the draft and the new photo are kept,
        // and Open Look reopens them; closing then drops them.
        val previous = h.planner.respond
        h.planner.respond = { r, snap -> if (r is PlanRequest.ApplyTheme) PlanResult.Refused(listOf("no")) else previous(r, snap) }
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        m.dispatch(HomeScreenIntent.SetOwnPicture(other, 60)); settle()
        m.dispatch(HomeScreenIntent.UseLook); settle()
        assertEquals(true, onTv.contentEquals(h.documents.read(OwnPicture.path(tv.id)) { it }), "the kept copy is still the TV's")
        assertEquals(HomeChoice(dock, "cinema", cards = false, picture = LookPicture.MINE_ID, dim = 40), HomePrefs(h.documents).load(tv.id))
        assertEquals(true, m.state.value.lookFailed)
        assertEquals(sha256Of(other), m.state.value.newPicture?.sha256, "the new photo is kept")
        assertEquals(40, m.state.value.ownDim)
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        assertEquals(LookPicture.NEW_ID to 60, m.state.value.look?.let { it.picture to it.preview?.dim }, "the kept draft")
        assertEquals(false, m.state.value.lookFailed)
        m.dispatch(HomeScreenIntent.CloseLook); settle()
        assertNull(m.state.value.newPicture)
        h.planner.respond = previous

        // "Restore mine" sends the photo on the TV with its dim.
        m.dispatch(HomeScreenIntent.RestoreMine); settle()
        val sent = h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().last()
        assertEquals(sha256Of(onTv), sent.wallpaper?.sha256)
        assertEquals(40, sent.doc.wallpaperDim)

        // Picked and sent: now it is the kept one.
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        m.dispatch(HomeScreenIntent.SetOwnPicture(other, 60)); settle()
        m.dispatch(HomeScreenIntent.UseLook); settle()
        assertEquals(sha256Of(other), h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().last().wallpaper?.sha256)
        assertEquals(true, other.contentEquals(h.documents.read(OwnPicture.path(tv.id)) { it }))
        assertEquals(LookPicture.MINE_ID to 60, HomePrefs(h.documents).load(tv.id)?.let { it.picture to it.dim })
        assertEquals(60, m.state.value.ownDim)
    }

    // look2 pictures (spec 6.2): every picture of today's looks, with its background colour, and no
    // new image bytes; the 0.4.0 ids keep working.
    @Test fun theGalleryHasEveryPictureWithItsColour() {
        val p = BundledThemeSource().pictures()
        assertEquals(listOf("diwali", "aurora", "mist", "glass", "ruska", "curtains", "mountains", "lamp", "neon", "cobalt"), p.map { it.id })
        assertEquals(listOf("Diwali", "Aurora", "Mist", "Navy", "Autumn lake", "Curtains", "Mountains", "Lamp light", "Neon", "Cobalt"), p.map { it.name })
        assertEquals(listOf(0x120809, 0x070A14, 0x0E1022, 0x090C14, 0x07080D, 0x07060A, 0x000000, 0x141210, 0x0B0614, 0x020611),
            p.map { it.backgroundArgb and 0xFFFFFF })
        assertTrue(p.all { it.backgroundArgb ushr 24 == 0xFF })
        val diwali = p.first()
        assertEquals(listOf<Any?>(lastDay, "diwali", "#FFB23C"), listOf(diwali.until, diwali.festival, diwali.accentColor))
        assertEquals(listOf(55), p.filter { it.sky > 0 }.map { it.sky })
        assertEquals("mountains", p.single { it.sky > 0 }.id)
        assertTrue(p.drop(1).all { it.until == null && it.festival == null })
        val themes = BundledThemeSource().bundled().associateBy { it.id }
        for ((picture, look) in listOf("curtains" to "cinema", "mountains" to "horizon", "lamp" to "library", "neon" to "arcade", "cobalt" to "console",
            "mist" to "mist", "diwali" to "diwali", "aurora" to "aurora")) {
            assertEquals(themes.getValue(look).wallpaper?.sha256, p.single { it.id == picture }.wallpaper.sha256, "$picture is $look's picture")
        }
    }

    // A dated picture: Diwali keeps the picture before it, is offered up to its last day, and after
    // it the draft opens on the picture before it and Home names it as ended.
    @Test fun diwaliIsADatedPictureThatGivesThePictureBackWhenItEnds() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.clock.setWall(ms(lastDay))
        val apps = pkgs(4)
        val (m, id) = homeWith(h, apps, HomeChoice(apps, null, cards = false, picture = "mist", columns = 5, style = "classic", movement = "lively", lift = false))
        assertTrue("diwali" in m.state.value.pictures.map { it.id })
        assertEquals(lastDay, m.state.value.pictures.single { it.id == "diwali" }.until)
        m.dispatch(HomeScreenIntent.OpenLook)
        m.dispatch(HomeScreenIntent.Look(LookEdit.SelectPicture("diwali")))
        assertEquals(true, m.state.value.look?.rows?.festiveRing)
        m.dispatch(HomeScreenIntent.UseLook); settle()
        val sent = h.pushes().last().doc
        assertEquals(listOf("diwali", "2026-11-10", "#FFB23C"), listOf(sent.festival, sent.festivalUntil, sent.accentColor))
        assertEquals("diwali" to "mist", HomePrefs(h.documents).load(id)?.let { it.picture to it.previousPicture })
        assertNull(m.state.value.endedLook)
        h.clock.setWall(ms(lastDay.plusDays(1)))
        val later = HomeScreenMachine(h.manageDeps(encoder = ConfigEncoder.Codec, themes = looks), backgroundScope, id)
        settle()
        assertTrue("diwali" !in later.state.value.pictures.map { it.id }, "no longer offered")
        assertEquals("Diwali", later.state.value.endedLook)
        later.dispatch(HomeScreenIntent.OpenLook); settle()
        assertEquals("mist", later.state.value.look?.picture, "the picture before it")
        // The draft opens on it as what is sent next; the ended line on Home is the way to send it.
        assertEquals(false, later.state.value.look?.changed)
    }

    // 0.4.0 Apps in a row. ---------------------------------------------------------------------

    private val looks = BundledThemeSource()
    private fun pkgs(n: Int) = List(n) { PackageName("com.example.app$it") }
    private fun on() = PackageState(EnabledState.ENABLED, false, 0, null, false, false, null, false)

    // A TV with the new home screen whose snapshot shows `apps`, with `choice` saved, and the
    // machine on it with the bundled looks.
    private suspend fun kotlinx.coroutines.test.TestScope.homeWith(h: Harness, apps: List<PackageName>, choice: HomeChoice): Pair<HomeScreenMachine, ai.promptstudio.tvclean.core.model.TvId> {
        val tv = h.knownTv().copy(launcher = LauncherState(1, Samples.zeroHash, Samples.zeroHash, Samples.zeroHash))
        h.tvs.upsert(tv)
        h.snapshots.save(Samples.snapshot(packages = Samples.miTvPackages() + apps.associateWith { on() }, takenAtMs = Samples.NOW_MS + 1))
        HomePrefs(h.documents).save(tv.id, choice)
        val m = HomeScreenMachine(h.manageDeps(encoder = ConfigEncoder.Codec, themes = looks), backgroundScope, tv.id)
        settle()
        return m to tv.id
    }

    private fun Harness.pushes() = planner.requests.filterIsInstance<PlanRequest.ApplyTheme>()

    // 1. Every look at every offered count: the override wins, rows_below_dock and dock_title follow
    // it, not the look, and the bytes decode with no codec warning.
    @Test fun appsInARowWinsOverTheLookForEveryLook() {
        for (t in looks.bundled()) for (c in DockLayers.MIN_OFFERED_COLUMNS..DockLayers.MAX_OFFERED_COLUMNS) for (n in listOf(4, 6, 9, 15)) {
            val label = "${t.id} at $c with $n apps"
            val (doc, _) = Look.docOf(t, pkgs(n), cards = false, null, c)
            val back = assertIs<DecodeResult.Ok>(LauncherConfigCodec.decode(LauncherConfigCodec.encode(doc)), label)
            assertEquals(emptyList(), back.warnings, "$label: no codec warning")
            val e = LauncherConfigCodec.effective(back.doc, LauncherConfigCodec.builtInDefaults)
            assertEquals(c, e.columns, label)
            val shown = minOf(n, 3 * c)
            val rows = DockLayers.rowsBelowDock(shown, c)
            assertEquals(if (t.doc.cinematicHome == true) 0 else rows, e.rowsBelowDock, label)
            assertEquals(pkgs(shown).map { it.value }, e.dockApps, label)
            assertEquals(false, e.fillDock, label)
            if (rows == 2 && t.doc.cinematicHome != true) assertEquals(false, e.dockTitle, "$label: three rows, no big name")
        }
    }

    // 2. With no override, each look's bytes are today's bytes plus one key, "fill_dock": false. A
    // JSON diff, so a failure names the key that changed.
    @Test fun withoutAnOverrideTheBytesOnlyAddFillDockFalse() {
        for (t in looks.bundled()) for (n in listOf(0, 2, 6, 15)) for (cards in listOf(true, false)) {
            val label = "${t.id} with $n apps, cards $cards"
            val before = LauncherConfigCodec.encode(DockLayers.applyTo(t.doc, pkgs(n)).copy(moduleCards = cards))
            val now = LauncherConfigCodec.encode(Look.docOf(t, pkgs(n), cards, null).first)
            val a = Json.parseToJsonElement(before.decodeToString()).jsonObject
            val b = Json.parseToJsonElement(now.decodeToString()).jsonObject
            assertEquals(setOf("fill_dock"), b.keys - a.keys, "$label: added keys")
            assertEquals(emptySet(), a.keys - b.keys, "$label: removed keys")
            assertEquals(emptyList(), a.keys.filter { a[it] != b[it] }, "$label: changed keys")
            assertEquals(JsonPrimitive(false), b["fill_dock"], label)
            assertEquals(LauncherConfigCodec.encode(Look.docOf(t, pkgs(n), cards, null, DockLayers.columnsOf(t.doc)).first).decodeToString(),
                now.decodeToString(), "$label: the look's own count is no override")
        }
    }

    // 4. A look tap resets apps in a row to that look's own; a picture keeps the draft's count, and
    // the sheet opens on the saved count.
    @Test fun aLookTapResetsAppsInARowAndAPictureKeepsIt() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val (m, id) = homeWith(h, pkgs(6), HomeChoice(pkgs(6), "console", cards = false, columns = 5))
        assertEquals(5, m.state.value.columns)
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        assertEquals(5 to 7, m.state.value.look?.let { it.columns to it.lookColumns }, "opens on the saved count")
        m.dispatch(HomeScreenIntent.SelectColumns(8)); settle()
        m.dispatch(HomeScreenIntent.SelectPicture("aurora")); settle()
        assertEquals(8, m.state.value.look?.columns, "a picture keeps it")
        m.dispatch(HomeScreenIntent.SelectLook("mist")); settle()
        assertEquals(5 to 5, m.state.value.look?.let { it.columns to it.lookColumns }, "Mist's own 5")
        m.dispatch(HomeScreenIntent.SelectLook("six-columns")); settle()
        assertEquals(6, m.state.value.look?.columns)
        assertEquals(6, m.state.value.look?.preview?.columns)
        m.dispatch(HomeScreenIntent.SelectColumns(4)); settle()
        m.dispatch(HomeScreenIntent.UseLook); settle()
        assertEquals(4, h.pushes().single().doc.columns)
        assertEquals(4, HomePrefs(h.documents).load(id)?.columns)
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        assertEquals(4, m.state.value.look?.columns, "the sheet opens on what was sent")
        m.dispatch(HomeScreenIntent.SelectColumns(6)); settle()
        m.dispatch(HomeScreenIntent.UseLook); settle()
        assertEquals(6, HomePrefs(h.documents).load(id)?.columns, "look2: apps in a row is always kept")
        assertEquals(6, h.pushes().last().doc.columns)
    }

    // 5. The order survives (the data loss fix): Console with 15 apps, then 4 in a row, then 7 again,
    // and Console to Default to Console. The saved list stays the same 15 in order; the TV gets the
    // part each count shows.
    @Test fun theDockOrderSurvivesASmallerRowAndBack() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val apps = pkgs(15)
        val (m, id) = homeWith(h, apps, HomeChoice(apps, "console", cards = false))
        assertEquals(apps, m.state.value.dock.map { it.pkg }, "7 a row shows all 15")
        fun use(look: String?, columns: Int?) {
            m.dispatch(HomeScreenIntent.OpenLook)
            if (look != null) m.dispatch(HomeScreenIntent.SelectLook(look))
            if (columns != null) m.dispatch(HomeScreenIntent.SelectColumns(columns))
            m.dispatch(HomeScreenIntent.UseLook)
        }
        use(null, 4); settle()
        assertEquals(apps.take(12), h.pushes().last().doc.dockApps)
        assertEquals(apps, HomePrefs(h.documents).load(id)?.dock, "the 3 apps beyond 12 are kept")
        assertEquals(apps.take(12), m.state.value.dock.map { it.pkg })
        assertEquals(apps.drop(12), m.state.value.waitingApps.map { it.pkg })
        assertEquals(3, m.state.value.waiting)
        use(null, 7); settle()
        assertEquals(apps, h.pushes().last().doc.dockApps, "back at 7: all 15, in order")
        assertEquals(apps, HomePrefs(h.documents).load(id)?.dock)
        assertEquals(7, HomePrefs(h.documents).load(id)?.columns)
        use("default", null); settle()
        assertEquals(apps.take(12), h.pushes().last().doc.dockApps)
        assertEquals(apps, HomePrefs(h.documents).load(id)?.dock)
        use("console", null); settle()
        assertEquals(apps, h.pushes().last().doc.dockApps, "Console again: the 15 in the same order")
        assertEquals(apps to 7, HomePrefs(h.documents).load(id)?.let { it.dock to it.columns })
        assertEquals(apps, m.state.value.dock.map { it.pkg })
        assertEquals(0, m.state.value.waiting)
    }

    // 6. The dock sheet at 4 with 15 saved: it shows 12 and its new order goes first; the 3 it never
    // showed stay after it, in their order. An app moved by dragging keeps its new place.
    @Test fun aNewDockFromTheSheetKeepsTheAppsItDidNotShow() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val apps = pkgs(15)
        val (m, id) = homeWith(h, apps, HomeChoice(apps, "default", cards = false))
        assertEquals(apps.take(12), m.state.value.dock.map { it.pkg })
        assertEquals(3, m.state.value.waiting)
        // Dragged: app 5 to the front, app 2 taken out.
        val edited = listOf(apps[5]) + apps.take(12).filter { it != apps[5] && it != apps[2] }
        m.dispatch(HomeScreenIntent.SetDock(edited)); settle()
        val saved = edited + apps.drop(12)
        assertEquals(saved, HomePrefs(h.documents).load(id)?.dock)
        assertEquals(saved.take(12), h.pushes().last().doc.dockApps)
        assertEquals(saved.take(12), m.state.value.dock.map { it.pkg })
        assertEquals(saved.drop(12), m.state.value.waitingApps.map { it.pkg })
        // A waiting app added in the sheet is not there twice.
        m.dispatch(HomeScreenIntent.SetDock(m.state.value.dock.map { it.pkg }.take(11) + apps[14])); settle()
        // Shown: the 11 edited and app 12; waiting: 13 and 14. App 12 left out, app 14 added.
        assertEquals(edited + apps[14] + apps[13], HomePrefs(h.documents).load(id)?.dock)
        // Saved apps in a row stay what they were.
        HomePrefs(h.documents).save(id, HomePrefs(h.documents).load(id)!!.copy(columns = 5))
        val again = HomeScreenMachine(h.manageDeps(encoder = ConfigEncoder.Codec, themes = looks), backgroundScope, id)
        settle()
        again.dispatch(HomeScreenIntent.SetDock(again.state.value.dock.map { it.pkg }.reversed())); settle()
        assertEquals(5, h.pushes().last().doc.columns)
        assertEquals(5, HomePrefs(h.documents).load(id)?.columns)
    }

    // A saved order from the dock sheet survives a change of apps in a row in the Look sheet: going
    // from 4 to 8 brings the waiting apps into view after the sheet's order, and back to 4 keeps all.
    @Test fun theDockSheetOrderSurvivesAppsInARow() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val apps = pkgs(20)
        val (m, id) = homeWith(h, apps, HomeChoice(apps, "default", cards = false))
        val edited = apps.take(12).reversed()
        m.dispatch(HomeScreenIntent.SetDock(edited)); settle()
        val order = edited + apps.drop(12)
        assertEquals(order, HomePrefs(h.documents).load(id)?.dock)
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        m.dispatch(HomeScreenIntent.SelectColumns(8)); settle()
        assertEquals(order.map { h.manageDeps().nameOf(it) }, m.state.value.look?.preview?.dock, "the preview shows the whole order")
        m.dispatch(HomeScreenIntent.UseLook); settle()
        assertEquals(order, h.pushes().last().doc.dockApps)
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        m.dispatch(HomeScreenIntent.SelectColumns(4)); settle()
        m.dispatch(HomeScreenIntent.UseLook); settle()
        assertEquals(order.take(12), h.pushes().last().doc.dockApps)
        assertEquals(order, HomePrefs(h.documents).load(id)?.dock)
    }

    // 8. home.txt: an old file loads with no apps in a row; 4 to 8 load, anything else is dropped; a
    // new file round trips.
    @Test fun homeTxtKeepsAppsInARow() = runTest {
        val h = Harness()
        val id = h.knownTv().id
        val prefs = HomePrefs(h.documents)
        h.documents.write("tvs/${id.value}/home.txt", "dock=com.netflix.ninja\ntheme=console\npicture=glass\n".encodeToByteArray())
        assertNull(prefs.load(id)?.columns)
        for ((text, want) in listOf("3" to null, "4" to 4, "8" to 8, "9" to null, "x" to null, "" to null)) {
            h.documents.write("tvs/${id.value}/home.txt", "dock=com.netflix.ninja\ntheme=console\ncolumns=$text\n".encodeToByteArray())
            assertEquals(want, prefs.load(id)?.columns, "columns=$text")
        }
        val choice = HomeChoice(pkgs(24), "mist", cards = true, picture = "glass", columns = 8)
        prefs.save(id, choice)
        assertEquals(choice, prefs.load(id))
        assertEquals(choice.copy(columns = null), choice.withTheme("mist", columns = null))
        assertEquals(8, choice.withTheme("console").columns, "a new look keeps a saved count unless told")
    }
}
