package ai.promptstudio.tvclean.ui.design.component

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import androidx.compose.runtime.Immutable
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

// 0.4.0 "Your photo": the photo the system picker handed over (no storage permission), made into
// the launcher's wallpaper.jpg. ImageDecoder reads JPEG, PNG, WebP and HEIF and turns the photo by
// its EXIF rotation; the result is a fresh JPEG, so nothing of the original's metadata (place,
// camera, time) is left. The TV gets 1920 x 1080 (the launcher samples to the screen, and most TVs
// draw their interface at 1080p even on a 4K panel), at the best quality from 85 down to 60 that
// fits MAX_BYTES, else 1600 x 900, else 1280 x 720.
@Immutable
object OwnPhoto {
    const val MAX_BYTES = 280_000
    private const val WORKING_LONG_SIDE = 3200
    val sizes = listOf(1920 to 1080, 1600 to 900, 1280 to 720)
    val qualities = (85 downTo 60 step 5).toList()

    // The photo at a working size (its long side at most WORKING_LONG_SIDE), or null when it does not
    // decode. The picker's grant is short lived, so this runs at once.
    fun decode(resolver: ContentResolver, uri: Uri): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val long = max(info.size.width, info.size.height)
            var sample = 1
            while (long / (sample * 2) >= WORKING_LONG_SIDE) sample *= 2
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }
    }.getOrNull()

    // The cropped part of `photo` as the JPEG the TV gets, or null when not even 1280 x 720 at
    // quality 60 fits.
    fun make(photo: Bitmap, crop: Rect): ByteArray? {
        val part = Bitmap.createBitmap(photo, crop.left, crop.top, crop.width().coerceAtLeast(1), crop.height().coerceAtLeast(1))
        return fit(sizes, qualities, MAX_BYTES) { w, h, q -> jpeg(scaled(part, w, h), q) }
    }

    // The first size and quality, in order, whose bytes fit `max`.
    fun fit(sizes: List<Pair<Int, Int>>, qualities: List<Int>, max: Int, encode: (Int, Int, Int) -> ByteArray): ByteArray? {
        for ((w, h) in sizes) for (q in qualities) {
            val b = encode(w, h, q)
            if (b.size <= max) return b
        }
        return null
    }

    // Scales with filtering, halving first while it would shrink more than 2x (one big step loses detail).
    fun scaled(src: Bitmap, w: Int, h: Int): Bitmap {
        var b = src
        while (b.width > w * 2 && b.height > h * 2) b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        return if (b.width == w && b.height == h) b else Bitmap.createScaledBitmap(b, w, h, true)
    }

    private fun jpeg(b: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    // The 16:9 part of a `w` x `h` photo a frame shows: the photo scaled to cover the frame, times
    // `zoom` (1 or more), moved by the pan (`panX`, `panY`, as fractions of the free room, -1 to 1).
    fun crop(w: Int, h: Int, zoom: Float, panX: Float, panY: Float): Rect {
        val coverW = minOf(w.toFloat(), h * 16f / 9f)
        val cw = coverW / zoom.coerceAtLeast(1f)
        val ch = cw * 9f / 16f
        val left = (w - cw) / 2f * (1f + panX.coerceIn(-1f, 1f))
        val top = (h - ch) / 2f * (1f + panY.coerceIn(-1f, 1f))
        return Rect(left.roundToInt(), top.roundToInt(), (left + cw).roundToInt().coerceAtMost(w), (top + ch).roundToInt().coerceAtMost(h))
    }
}
