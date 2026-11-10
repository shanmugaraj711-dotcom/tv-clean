package ai.promptstudio.tvclean.core.flows

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import ai.promptstudio.tvclean.core.model.EnabledState
import ai.promptstudio.tvclean.core.model.LauncherState
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.PackageState
import ai.promptstudio.tvclean.core.model.TvId
import ai.promptstudio.tvclean.core.planner.PlanRequest
import ai.promptstudio.tvclean.core.spec.BundledThemeSource
import ai.promptstudio.tvclean.core.spec.StyleId
import ai.promptstudio.tvclean.testing.fixtures.Samples
import ai.promptstudio.tvclean.testing.fixtures.sha256Of
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// look2: one step of earlier look per TV, with its photo. Every send that changes the look keeps
// the look before it; "Put back the earlier look" only fills the draft, and the one button sends
// it, byte for byte what that look sent.
class EarlierLookTest {
    private val looks = BundledThemeSource()
    private val dock = listOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom").map(::PackageName)
    private val on = PackageState(EnabledState.ENABLED, false, 0, null, false, false, null, false)

    private suspend fun TestScope.home(h: Harness, choice: HomeChoice?): Pair<HomeScreenMachine, TvId> {
        val tv = h.knownTv().copy(launcher = LauncherState(1, Samples.zeroHash, Samples.zeroHash, Samples.zeroHash))
        h.tvs.upsert(tv)
        h.snapshots.save(Samples.snapshot(packages = Samples.miTvPackages() + dock.associateWith { on }, takenAtMs = Samples.NOW_MS + 1))
        if (choice != null) HomePrefs(h.documents).save(tv.id, choice)
        val m = HomeScreenMachine(h.manageDeps(encoder = ConfigEncoder.Codec, themes = looks), backgroundScope, tv.id)
        settle()
        return m to tv.id
    }

    private fun Harness.pushes() = planner.requests.filterIsInstance<PlanRequest.ApplyTheme>()
    private fun Harness.lastBytes() = blobs.bytes(pushes().last().config).decodeToString()

    private fun TestScope.send(m: HomeScreenMachine, vararg edits: LookEdit) {
        m.dispatch(HomeScreenIntent.OpenLook)
        for (e in edits) m.dispatch(HomeScreenIntent.Look(e))
        m.dispatch(HomeScreenIntent.UseLook)
        settle()
    }

    @Test fun putBackTheEarlierLookSendsItsBytesExactly() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val (m, id) = home(h, HomeChoice(dock, "mist", cards = false))
        // The first send keeps the saved 0.4.0 look as the earlier one.
        send(m, LookEdit.SelectStyle(StyleId.GLASS), LookEdit.SelectPicture("aurora"), LookEdit.SelectColumns(6))
        val a = h.lastBytes()
        val earlierText = assertNotNull(h.documents.read(EarlierLook.textPath(id)) { it.decodeToString() })
        assertTrue("theme=mist" in earlierText, earlierText)
        // Then B.
        send(m, LookEdit.SelectStyle(StyleId.CALM), LookEdit.SelectPicture("neon"), LookEdit.SetNames(true))
        val b = h.lastBytes()
        assertTrue(a != b)
        assertEquals("glass", HomePrefs.decode(h.documents.read(EarlierLook.textPath(id)) { it.decodeToString() }!!).style)
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        assertEquals(WayBack.EARLIER, m.state.value.look?.wayBack)
        val pushes = h.pushes().size
        m.dispatch(HomeScreenIntent.Look(LookEdit.PutBackEarlier)); settle()
        val draft = assertNotNull(m.state.value.look)
        assertEquals(Triple(StyleId.GLASS, "aurora", 6), Triple(draft.style, draft.picture, draft.columns))
        assertEquals(LookNoteKind.EARLIER_CHOSEN, draft.note?.kind)
        assertEquals(pushes, h.pushes().size, "putting it back only fills the draft")
        m.dispatch(HomeScreenIntent.UseLook); settle()
        assertEquals(pushes + 1, h.pushes().size)
        assertEquals(a, h.lastBytes(), "the earlier bytes exactly")
        // Now B is the earlier look.
        assertEquals("calm", HomePrefs.decode(h.documents.read(EarlierLook.textPath(id)) { it.decodeToString() }!!).style)
        // A send that changes nothing keeps the earlier look as it is.
        send(m)
        assertEquals("calm", HomePrefs.decode(h.documents.read(EarlierLook.textPath(id)) { it.decodeToString() }!!).style)
        assertEquals(a, h.lastBytes())
    }

    // The first send on a TV with no saved look keeps Setup's Default look as the earlier one.
    @Test fun theFirstSendKeepsTheDefaultLook() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val (m, id) = home(h, null)
        send(m, LookEdit.SelectStyle(StyleId.SPOTLIGHT))
        val earlier = HomePrefs.decode(h.documents.read(EarlierLook.textPath(id)) { it.decodeToString() }!!)
        assertEquals("default" to null, earlier.themeId to earlier.style)
    }

    // The earlier look's photo is kept with it (earlier.jpg), comes back as the earlier photo, and
    // once sent is the TV's photo again.
    @Test fun theEarlierPhotoComesBackWithItsLook() = runTest(timeout = 30.seconds) {
        val h = Harness()
        fun photo(seed: Int) = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(4_000) { ((it + seed) % 251).toByte() }
        val first = photo(1)
        val second = photo(2)
        val (m, id) = home(h, HomeChoice(dock, "default", cards = false))
        m.dispatch(HomeScreenIntent.OpenLook)
        m.dispatch(HomeScreenIntent.SetOwnPicture(first, 30)); settle()
        m.dispatch(HomeScreenIntent.UseLook); settle()
        val a = h.lastBytes()
        assertNull(h.documents.read(EarlierLook.photoPath(id)) { it }, "the Default look had no photo")
        m.dispatch(HomeScreenIntent.OpenLook)
        m.dispatch(HomeScreenIntent.SetOwnPicture(second, 50)); settle()
        m.dispatch(HomeScreenIntent.UseLook); settle()
        assertContentEquals(second, h.documents.read(OwnPicture.path(id)) { it })
        assertContentEquals(first, h.documents.read(EarlierLook.photoPath(id)) { it }, "the earlier photo")
        assertEquals(sha256Of(first), m.state.value.earlierPicture?.sha256)
        m.dispatch(HomeScreenIntent.OpenLook); settle()
        m.dispatch(HomeScreenIntent.Look(LookEdit.PutBackEarlier)); settle()
        assertEquals(LookPicture.EARLIER_ID to true, m.state.value.look?.let { it.picture to it.newPhoto })
        m.dispatch(HomeScreenIntent.UseLook); settle()
        assertEquals(a, h.lastBytes(), "the earlier bytes exactly")
        assertEquals(sha256Of(first), h.pushes().last().wallpaper?.sha256)
        assertContentEquals(first, h.documents.read(OwnPicture.path(id)) { it }, "the TV's photo again")
        assertEquals(LookPicture.MINE_ID to 30, HomePrefs(h.documents).load(id)?.let { it.picture to it.dim })
        assertContentEquals(second, h.documents.read(EarlierLook.photoPath(id)) { it }, "and the other one is the earlier photo now")
        // Both files live in the TV's folder, so Forget this TV takes them (deleteTree of tvs/<id>).
        assertTrue(EarlierLook.textPath(id).startsWith("tvs/${id.value}/") && EarlierLook.photoPath(id).startsWith("tvs/${id.value}/"))
        h.documents.deleteTree("tvs/${id.value}")
        assertNull(EarlierLook.load(h.documents, h.blobs, id))
    }
}
