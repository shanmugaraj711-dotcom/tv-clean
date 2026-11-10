package ai.promptstudio.tvclean.core.spec

import org.junit.Test
import ai.promptstudio.tvclean.core.model.PackageName
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The preview's app colours: a small table of known TV apps by package, and grey for the rest.
class AppColoursTest {
    @Test fun knownAppsHaveTheirOwnColour() {
        assertEquals(0xFFE50914.toInt(), AppColours.of(PackageName("com.netflix.ninja")))
        assertEquals(0xFFFF1F1F.toInt(), AppColours.of(PackageName("com.google.android.youtube.tv")))
        assertEquals(0xFF00A8E1.toInt(), AppColours.of(PackageName("com.amazon.amazonvideo.livingroom")))
        assertEquals(0xFF2B5CFF.toInt(), AppColours.of(PackageName("in.startv.hotstar")))
    }

    @Test fun anyOtherAppAndNoAppAreNeutral() {
        assertEquals(AppColours.NEUTRAL, AppColours.of(PackageName("com.example.tvbrowser")))
        assertEquals(AppColours.NEUTRAL, AppColours.of(null as PackageName?))
        assertEquals(AppColours.NEUTRAL, AppColours.of(""))
    }

    @Test fun theTableCoversAboutTwentyFiveAppsAllOpaqueAndValidPackages() {
        assertTrue(AppColours.known.size >= 25, "${AppColours.known.size} apps")
        for (pkg in AppColours.known) {
            PackageName(pkg)
            assertEquals(0xFF, AppColours.of(pkg) ushr 24, pkg)
            assertTrue(AppColours.of(pkg) != AppColours.NEUTRAL, pkg)
        }
    }
}
