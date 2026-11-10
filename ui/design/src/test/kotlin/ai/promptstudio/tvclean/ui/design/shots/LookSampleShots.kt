package ai.promptstudio.tvclean.ui.design.shots

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ai.promptstudio.tvclean.core.flows.AppChoice
import ai.promptstudio.tvclean.core.flows.LookPreview
import ai.promptstudio.tvclean.core.flows.LookSample
import ai.promptstudio.tvclean.core.flows.LookSamples
import ai.promptstudio.tvclean.core.model.BlobRef
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.spec.AppNames
import ai.promptstudio.tvclean.core.spec.BundledThemeSource
import ai.promptstudio.tvclean.core.spec.FactsTable
import ai.promptstudio.tvclean.core.spec.PackageFacts
import ai.promptstudio.tvclean.ui.design.component.TvPreview
import ai.promptstudio.tvclean.ui.design.component.toTvLook
import ai.promptstudio.tvclean.ui.design.theme.CytTheme

// Every case of the look2 emulator matrix (LookSamples.matrix, build plan section 10), drawn from
// the bytes its push sends, at the TV's size (960 x 540 dp, one pixel a dp), as sample_<name>.png:
// the tester puts each beside the emulator's screenshot of the same bytes. The clock is the
// emulator's (Monday 26 October 2026, 8:30 PM); the apps have the stand-ins' names, and the other
// apps are the stand-ins that are not in the dock, in the order its grid shows them, so the rows
// under the dock match too.
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h600dp-mdpi")
class LookSampleShots(private val sample: NamedSample) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun shot() {
        val (_, bytes, wallpaper) = LookSamples.push(sample.sample, sources, sources)
        val names = sample.sample.dock.map { AppChoice(it, labels[it] ?: AppNames.of(it, noFacts)) }
        val others = emulatorApps.filter { o -> sample.sample.dock.none { it == o.pkg } }
        val preview = LookPreview.of(bytes, wallpaper, names, others, LocalDate.of(2026, 10, 26), 20 * 60 + 30, sample.sample.tv.hasInputs)
        // The push sends a wallpaper only when the TV shows one.
        val look = preview.toTvLook(image(wallpaper))
        compose.setContent {
            CytTheme(darkTheme = true) {
                Box(Modifier.testTag("tv").size(960.dp, 540.dp)) { TvPreview(look, "TV") }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tv").captureRoboImage("src/test/screenshots/sample_${sample.sample.name}.png")
    }

    // A wallpaper the push sends, from the :core:spec resources (null when the bytes show none).
    private fun image(ref: BlobRef?): ImageBitmap? {
        val path = ref?.path?.takeIf { it.startsWith(BundledThemeSource.PREFIX) }?.removePrefix(BundledThemeSource.PREFIX) ?: return null
        return LookSampleShots::class.java.getResourceAsStream("/$path")?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
        }
    }

    // The case's name for the test report, and the case.
    class NamedSample(val sample: LookSample) {
        override fun toString() = sample.name
    }

    companion object {
        private val sources = BundledThemeSource()
        private val noFacts = object : FactsTable { override fun lookup(pkg: PackageName): PackageFacts? = null }
        private val emulatorApps = TvShots.others
        // The names the emulator's stand-in apps show on the TV, so the drawing reads like the screenshot.
        private val labels: Map<PackageName, String> = (TvShots.dock15 + TvShots.others).associate { it.pkg to it.name } + listOf(
            "com.disney.disneyplus" to "Disney+", "com.apple.atve.androidtv.appletv" to "Apple TV", "com.zattoo.player" to "Zattoo",
        ).associate { (p, n) -> PackageName(p) to n }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = LookSamples.matrix.map { arrayOf<Any>(NamedSample(it)) }
    }
}
