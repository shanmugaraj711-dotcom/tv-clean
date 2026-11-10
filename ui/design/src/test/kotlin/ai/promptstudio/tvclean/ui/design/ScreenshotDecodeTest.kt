package ai.promptstudio.tvclean.ui.design

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ai.promptstudio.tvclean.ui.design.component.SCREENSHOT_MAX_SIDE
import ai.promptstudio.tvclean.ui.design.component.decodeScreenshot
import ai.promptstudio.tvclean.ui.design.component.screenshotSampleSize
import java.io.ByteArrayOutputStream

// Issue #1273: a 4K TV's screenshot (3840 x 2160) decoded whole is a 33 MB bitmap for a thumbnail
// a phone shows at about 1000 px. The bounds are read first and the PNG is sampled down by a power
// of two, so the longer side is at most SCREENSHOT_MAX_SIDE.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotDecodeTest {
    @Test fun theSampleIsThePowerOfTwoThatFitsTheLongerSide() {
        assertEquals(2, screenshotSampleSize(3840, 2160))
        assertEquals(1, screenshotSampleSize(1920, 1080))
        assertEquals(1, screenshotSampleSize(1080, 1920))
        assertEquals(1, screenshotSampleSize(1280, 720))
        assertEquals(1, screenshotSampleSize(960, 540))
        assertEquals(4, screenshotSampleSize(7680, 4320))
        assertEquals("unknown bounds decode as they are", 1, screenshotSampleSize(-1, -1))
    }

    @Test fun aFourKScreenshotDecodesSampledDown() {
        val image = decodeScreenshot(png(3840, 2160))!!
        assertEquals(1920, image.width)
        assertEquals(1080, image.height)
        assertTrue(maxOf(image.width, image.height) <= SCREENSHOT_MAX_SIDE)
        val small = decodeScreenshot(png(1280, 720))!!
        assertEquals(1280, small.width)
        assertNull(decodeScreenshot("not a png".encodeToByteArray()))
    }

    private fun png(width: Int, height: Int): ByteArray {
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
