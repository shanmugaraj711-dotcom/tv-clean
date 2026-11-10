package ai.promptstudio.tvclean.core.flows

import org.junit.Test
import kotlin.test.assertEquals

// The phone's sky is launcher 2.7's Sky.java: the same anchors and the same blend, so the preview
// of a look with sky_intensity draws the colours the TV paints at that minute. The expected values
// are Sky.at's own (Java float maths, Math.round).
class SkyTest {
    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
    private fun assertSky(minute: Int, top: String, mid: String, bottom: String, sun: Int, sunX: Float) {
        val p = Sky.at(minute)
        assertEquals(listOf(top, mid, bottom), listOf(hex(p.top), hex(p.mid), hex(p.bottom)), "colours at $minute")
        assertEquals(0xFF, p.top ushr 24, "opaque at $minute")
        assertEquals(sun, p.sun, "sun at $minute")
        assertEquals(sunX, p.sunX, 0.0005f, "sunX at $minute")
    }

    @Test fun halfPastSixInTheMorningIsTheDawnAnchor() = assertSky(6 * 60 + 30, "#160F2E", "#3B2352", "#8A4A6B", 100, 0.24f)

    @Test fun oneInTheAfternoonIsTheDayAnchor() = assertSky(13 * 60, "#0B1524", "#17304F", "#31587F", 60, 0.50f)

    @Test fun sevenInTheEveningIsTheWarmestGlow() = assertSky(19 * 60, "#1B1030", "#6C2E52", "#E2704A", 190, 0.76f)

    // The captures' time: between the 19:45 and 21:00 anchors, 60% of the way.
    @Test fun halfPastEightInTheEveningIsDarkMauveWithTheWarmColourLow() = assertSky(20 * 60 + 30, "#0E0B20", "#2D183A", "#653051", 88, 0.844f)

    @Test fun betweenAnchorsItBlends() = assertSky(8 * 60, "#111F3C", "#4D3960", "#AF665E", 143, 0.32f)

    @Test fun theDayIsClampedAndNightHasNoSun() {
        for (m in listOf(-5, 0, 3 * 60, 1440, 2000)) assertSky(m, "#04060E", "#070C1A", "#0B1326", 0, 0.30f)
    }

    @Test fun theSunsWarmCoreIsTheBottomColourMostOfTheWayToPale() {
        assertEquals(0xFFC99E84.toInt(), Sky.warm(Sky.at(20 * 60 + 30)))
    }
}
