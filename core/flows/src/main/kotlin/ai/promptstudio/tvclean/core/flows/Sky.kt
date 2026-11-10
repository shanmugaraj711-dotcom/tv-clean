package ai.promptstudio.tvclean.core.flows

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// The launcher's time of day sky (sky_intensity above 0), ported from launcher 2.7's Sky.java so
// the phone's preview draws the same colours as the TV: STOPS and at(minute), pure maths. SkyView
// paints it as a gradient top, middle (at MIDDLE_AT) and bottom, then, when `sun` is above 0, a
// warm radial glow (warm(), alpha `sun`) centred at `sunX` across and SUN_Y down with a radius of
// SUN_RADIUS of the width, all at the view's alpha sky_intensity / 100. The TV stretches the day to
// its real sunrise and sunset only when a location is set in its config.xml; the preview uses the
// reference day, as the TV does without one.
object Sky {
    // One instant: the three gradient colours (opaque ARGB) and the glow (alpha 0 to 255, place 0 to 1).
    data class Palette(val top: Int, val mid: Int, val bottom: Int, val sun: Int, val sunX: Float)

    private class Stop(val minute: Int, val top: Int, val mid: Int, val bottom: Int, val sun: Int, val sunX: Float)

    const val MIDDLE_AT = 0.55f
    const val SUN_Y = 0.80f
    const val SUN_RADIUS = 0.55f
    private const val SUN_WARM = 0xFFFFD9A0.toInt()

    private const val NIGHT_TOP = 0xFF04060E.toInt()
    private const val NIGHT_MID = 0xFF070C1A.toInt()
    private const val NIGHT_BOTTOM = 0xFF0B1326.toInt()

    private val STOPS = listOf(
        Stop(0, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
        Stop(320, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
        Stop(390, 0xFF160F2E.toInt(), 0xFF3B2352.toInt(), 0xFF8A4A6B.toInt(), 100, 0.24f),
        Stop(450, 0xFF12203F.toInt(), 0xFF5C3A63.toInt(), 0xFFD3674F.toInt(), 160, 0.30f),
        Stop(570, 0xFF0E1A33.toInt(), 0xFF1F3557.toInt(), 0xFF43648B.toInt(), 90, 0.38f),
        Stop(780, 0xFF0B1524.toInt(), 0xFF17304F.toInt(), 0xFF31587F.toInt(), 60, 0.50f),
        Stop(1020, 0xFF0D1526.toInt(), 0xFF20395C.toInt(), 0xFF4C5470.toInt(), 90, 0.62f),
        Stop(1140, 0xFF1B1030.toInt(), 0xFF6C2E52.toInt(), 0xFFE2704A.toInt(), 190, 0.76f),
        Stop(1185, 0xFF140C27.toInt(), 0xFF4A2049.toInt(), 0xFFA54A5C.toInt(), 130, 0.82f),
        Stop(1260, 0xFF0A0A1C.toInt(), 0xFF191230.toInt(), 0xFF3A1F49.toInt(), 60, 0.86f),
        Stop(1350, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
        Stop(1440, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
    )

    // The palette at `minuteOfDay`, clamped to a day (Sky.at(int)).
    fun at(minuteOfDay: Int): Palette {
        val m = max(0, min(1440, minuteOfDay))
        var a = STOPS.first()
        var b = STOPS.last()
        for (i in 0 until STOPS.size - 1) {
            if (m >= STOPS[i].minute && m <= STOPS[i + 1].minute) {
                a = STOPS[i]
                b = STOPS[i + 1]
                break
            }
        }
        val t = if (b.minute == a.minute) 0f else (m - a.minute) / (b.minute - a.minute).toFloat()
        return Palette(lerpColor(a.top, b.top, t), lerpColor(a.mid, b.mid, t), lerpColor(a.bottom, b.bottom, t), lerp(a.sun, b.sun, t), lerp(a.sunX, b.sunX, t))
    }

    // The glow's warm core: the bottom colour 65% of the way to a pale sun (SkyView.render).
    fun warm(p: Palette): Int = lerpColor(p.bottom, SUN_WARM, 0.65f)

    // Blends two ARGB colours channel by channel; `t` is 0 to 1 (Sky.lerpColor, Math.round on floats).
    fun lerpColor(from: Int, to: Int, t: Float): Int {
        val f = max(0f, min(1f, t))
        fun ch(shift: Int): Int {
            val x = (from ushr shift) and 0xFF
            val y = (to ushr shift) and 0xFF
            return (x + (y - x) * f).roundToInt()
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun lerp(from: Int, to: Int, t: Float): Int = (from + (to - from) * max(0f, min(1f, t))).roundToInt()

    private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * max(0f, min(1f, t))
}
