package ai.promptstudio.tvclean.core.flows

import java.io.File
import kotlinx.coroutines.test.runTest
import ai.promptstudio.tvclean.core.model.BlobRef
import ai.promptstudio.tvclean.core.model.EnabledState
import ai.promptstudio.tvclean.core.model.LauncherState
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.PackageState
import ai.promptstudio.tvclean.core.planner.PlanRequest
import ai.promptstudio.tvclean.core.spec.BundledThemeSource
import ai.promptstudio.tvclean.core.spec.LauncherConfigCodec
import ai.promptstudio.tvclean.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

// 0.4.0 emulator check (plan item H): the app's own bytes for each case, so the TV emulator gets
// exactly what a push sends. Each case is a folder under build/look-bytes with config.json and,
// when the push sends one, wallpaper.jpg. The emulator session pushes them the way
// FileOverlayTransport does; nothing here talks to a TV.
class LookBytesDumpTest {
    private val looks = BundledThemeSource()
    private val out = File("build/look-bytes")
    private fun p(s: String) = PackageName(s)

    // Packages the emulator has as small stand-in apps.
    private val fifteen = listOf(
        "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar",
        "com.spotify.tv.android", "com.plexapp.android", "com.sonyliv", "com.graymatrix.did", "com.jio.media.ondemand",
        "com.example.tvbrowser", "org.videolan.vlc", "tv.twitch.android.app", "com.disney.disneyplus",
        "com.apple.atve.androidtv.appletv", "com.zattoo.player",
    ).map(::p)

    private fun write(case: String, config: ByteArray, wallpaper: ByteArray?) {
        val dir = File(out, case).apply { deleteRecursively(); mkdirs() }
        File(dir, "config.json").writeBytes(config)
        if (wallpaper != null) File(dir, "wallpaper.jpg").writeBytes(wallpaper)
    }

    private fun bundled(ref: BlobRef?): ByteArray? = ref?.let { BundledThemeSource.bytes(it) ?: error("not a bundled picture: $it") }

    // look2: every case of the emulator matrix (LookSamples.matrix, build plan section 10) by its
    // name, with the bytes and the wallpaper its push sends, and its home.txt.
    @Test fun everySampleOfTheMatrix() {
        for (s in LookSamples.matrix) {
            val (_, bytes, wallpaper) = LookSamples.push(s, looks, looks)
            write(s.name, bytes, bundled(wallpaper))
            File(out, "${s.name}/home.txt").writeText(HomePrefs.encode(s.choice))
        }
        assertEquals(LookSamples.matrix.size, out.listFiles().orEmpty().count { f -> LookSamples.matrix.any { it.name == f.name } })
    }

    @Test fun appsInARowAndEmptyDockSpaces() {
        val mist = looks.bundled().single { it.id == "mist" }
        val six = fifteen.take(6)
        for (c in listOf(4, 5, 7, 8)) {
            val (doc, wallpaper) = LookDocs.of(mist, six, cards = false, columns = c)
            write("cols-$c", LauncherConfigCodec.encode(doc), bundled(wallpaper))
        }
        val default = looks.bundled().single { it.id == "default" }
        val (doc, wallpaper) = LookDocs.of(default, fifteen.take(4), cards = false, columns = 7)
        assertEquals(false, doc.fillDock)
        write("empty-default-7", LauncherConfigCodec.encode(doc), bundled(wallpaper))
    }

    // Console with 15 apps, then Default, then Console again, through the Look sheet as a person
    // does it; each push's own bytes. The last must equal the first: the dock order survives.
    @Test fun consoleToDefaultToConsole() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val tv = h.knownTv().copy(launcher = LauncherState(1, Samples.zeroHash, Samples.zeroHash, Samples.zeroHash))
        h.tvs.upsert(tv)
        val on = PackageState(EnabledState.ENABLED, false, 0, null, false, false, null, false)
        h.snapshots.save(Samples.snapshot(packages = Samples.miTvPackages() + fifteen.associateWith { on }, takenAtMs = Samples.NOW_MS + 1))
        HomePrefs(h.documents).save(tv.id, HomeChoice(fifteen, "console", cards = false))
        val m = HomeScreenMachine(h.manageDeps(encoder = ConfigEncoder.Codec, themes = looks), backgroundScope, tv.id)
        settle()
        val sent = mutableListOf<ByteArray>()
        for ((i, look) in listOf("console", "default", "console").withIndex()) {
            m.dispatch(HomeScreenIntent.OpenLook)
            m.dispatch(HomeScreenIntent.SelectLook(look))
            m.dispatch(HomeScreenIntent.UseLook)
            settle()
            val push = h.planner.requests.filterIsInstance<PlanRequest.ApplyTheme>().last()
            val bytes = h.blobs.bytes(push.config)
            sent += bytes
            write("order-${i + 1}-$look", bytes, bundled(push.wallpaper))
            File(out, "order-${i + 1}-$look/home.txt").writeBytes(h.documents.read("tvs/${tv.id.value}/home.txt") { it }!!)
        }
        assertEquals(sent[0].decodeToString(), sent[2].decodeToString())
        assertEquals(fifteen, HomePrefs(h.documents).load(tv.id)?.dock)
    }
}
