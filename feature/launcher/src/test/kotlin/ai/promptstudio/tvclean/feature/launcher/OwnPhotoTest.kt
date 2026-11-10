package ai.promptstudio.tvclean.feature.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.random.Random
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ai.promptstudio.tvclean.ui.design.component.OwnPhoto

// 0.4.0 "Your photo": a noisy 12 MP photo (the hardest kind to compress) becomes a 1920 x 1080 JPEG
// of at most 280 KB, the quality stepping down from 85 and then the size, and the crop is 16:9.
// OwnPhoto lives in :ui:design; it is tested here, beside the Look sheet, so the 12 MP photo stays
// out of the design module's screenshot test JVM, which runs beside the others on a small build host.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OwnPhotoTest {
    @Test fun aNoisyPhotoFitsTheBudget() {
        val w = 4000; val h = 3000
        val r = Random(7)
        // A camera-like photo: gradients with strong sensor noise on every channel.
        fun c(v: Int) = (v + r.nextInt(-28, 29)).coerceIn(0, 255)
        val px = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            0xFF000000.toInt() or (c(x * 255 / w) shl 16) or (c(y * 255 / h) shl 8) or c(128 + (x + y) % 64)
        }
        val photo = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val crop = OwnPhoto.crop(w, h, 1f, 0f, 0f)
        assertEquals(16f / 9f, crop.width().toFloat() / crop.height(), 0.01f)
        val jpeg = assertNotNull(OwnPhoto.make(photo, crop))
        assertTrue(jpeg.size <= OwnPhoto.MAX_BYTES, "${jpeg.size} bytes")
        assertTrue(jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte())
        val back = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertEquals(16f / 9f, back.width.toFloat() / back.height, 0.01f)
        assertTrue(back.width in listOf(1920, 1600, 1280))
    }

    @Test fun theFirstSizeAndQualityThatFitsWins() {
        val tried = mutableListOf<Pair<Int, Int>>()
        val out = OwnPhoto.fit(OwnPhoto.sizes, OwnPhoto.qualities, 100) { w, _, q -> tried += w to q; ByteArray(if (w == 1600 && q == 70) 90 else 200) }
        assertEquals(90, out?.size)
        assertEquals(OwnPhoto.qualities.map { 1920 to it } + listOf(1600 to 85, 1600 to 80, 1600 to 75, 1600 to 70), tried)
        assertNull(OwnPhoto.fit(OwnPhoto.sizes, OwnPhoto.qualities, 10) { _, _, _ -> ByteArray(11) })
    }

    @Test fun theCropFollowsZoomAndPan() {
        assertEquals(android.graphics.Rect(0, 375, 4000, 2625), OwnPhoto.crop(4000, 3000, 1f, 0f, 0f))
        val left = OwnPhoto.crop(4000, 3000, 2f, -1f, -1f)
        assertEquals(0, left.left); assertEquals(0, left.top); assertEquals(2000, left.width())
        val right = OwnPhoto.crop(4000, 3000, 2f, 1f, 1f)
        assertEquals(4000, right.right); assertEquals(3000, right.bottom)
    }
}
