package ai.promptstudio.tvclean.ui.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ai.promptstudio.tvclean.ui.design.component.CountdownRing
import ai.promptstudio.tvclean.ui.design.component.DiffItem
import ai.promptstudio.tvclean.ui.design.component.DiffRow
import ai.promptstudio.tvclean.ui.design.component.NumeralHeadline
import ai.promptstudio.tvclean.ui.design.component.PinnedPreviewSize
import ai.promptstudio.tvclean.ui.design.component.SEGMENT_HIGHLIGHT_TAG
import androidx.compose.ui.unit.dp
import ai.promptstudio.tvclean.ui.design.component.SegmentOption
import ai.promptstudio.tvclean.ui.design.component.SegmentedChoice
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithContentDescription
import ai.promptstudio.tvclean.ui.design.component.UndoSnackbarHost
import ai.promptstudio.tvclean.ui.design.component.UndoSnackbarState
import ai.promptstudio.tvclean.ui.design.component.WelcomeIllustration
import ai.promptstudio.tvclean.ui.design.component.formatCountdown
import ai.promptstudio.tvclean.ui.design.theme.CytTheme

// Reduced-motion variants and the component behaviours the screens rely on.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class MotionAndBehaviourTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun countUpIsStaticUnderReducedMotion() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CytTheme(reducedMotion = true) { NumeralHeadline(23, "things to switch off") } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("23").assertExists()
    }

    @Test
    fun countUpRunsAtLeast700ms() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CytTheme(reducedMotion = false) { NumeralHeadline(23, "things to switch off") } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("23").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("23").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("23").assertExists()
    }

    @Test
    fun welcomePlaysOnceAndReducedShowsRestState() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CytTheme(reducedMotion = false) { Box(Modifier.testTag("w")) { WelcomeIllustration() } } }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithTag("w").captureRoboImage("src/test/screenshots/m01_welcome_mid_animation.png")
        compose.mainClock.advanceTimeBy(2500)
        compose.onNodeWithTag("w").captureRoboImage("src/test/screenshots/m02_welcome_rest.png")
    }

    @Test
    fun countdownFormatsAndAnnouncesOnlyAtMarks() {
        assertEquals("1:24", formatCountdown(84_000))
        assertEquals("1:30", formatCountdown(89_001))
        assertEquals("0:01", formatCountdown(1))
        assertEquals("0:00", formatCountdown(0))
        var remaining by mutableLongStateOf(84_000L)
        compose.setContent { CytTheme { CountdownRing(remaining, 90_000) } }
        val live = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        compose.onNode(live).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("1:30 left")))
        remaining = 55_000
        compose.waitForIdle()
        compose.onNode(live).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("One minute left. Say yes or go back.")))
        remaining = 45_000
        compose.waitForIdle()
        compose.onNode(live).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("One minute left. Say yes or go back.")))
        remaining = 25_000
        compose.waitForIdle()
        compose.onNode(live).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("30 seconds left.")))
        remaining = 9_000
        compose.waitForIdle()
        compose.onNode(live).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("10 seconds left.")))
    }

    @Test
    fun diffRowStatesTheOutcomeAndToggles() {
        var toggled: DiffItem? = null
        val item = DiffItem("com.mitv.tvhome.atv", "PatchWall", "Xiaomi's home screen with adverts", "com.mitv.tvhome.atv", off = true)
        compose.setContent { CytTheme { DiffRow(item, { toggled = it }) } }
        compose.onNodeWithText("PatchWall", useUnmergedTree = true).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off")).performClick()
        assertEquals(item, toggled)
    }

    @Test
    fun snackbarLeavesAfterFiveSeconds() {
        val state = UndoSnackbarState()
        compose.setContent { CytTheme { UndoSnackbarHost(state) } }
        compose.runOnIdle { state.show("Mi TV+ promos will be kept.") {} }
        compose.onNodeWithText("Mi TV+ promos will be kept.").assertExists()
        compose.mainClock.advanceTimeBy(5_500)
        compose.waitForIdle()
        assertNull(state.current)
    }

    // 0.4.0 SegmentedChoice: the one highlight slides from the old choice to the new one, about
    // 300 ms on a short spring, and under reduced motion it is on the new one at once.
    private fun highlightLeft() = compose.onNodeWithTag(SEGMENT_HIGHLIGHT_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
    private fun segmentLeft(n: Int) = compose.onNodeWithContentDescription("$n apps in a row").fetchSemanticsNode().boundsInRoot.left

    private fun slide(reduced: Boolean): List<Float> {
        var chosen by mutableStateOf("4")
        val options = (4..8).map { SegmentOption("$it", "$it", "$it apps in a row") }
        compose.mainClock.autoAdvance = false
        compose.setContent { CytTheme(reducedMotion = reduced) { SegmentedChoice(options, chosen, {}) } }
        compose.mainClock.advanceTimeBy(100)
        val start = highlightLeft()
        chosen = "7"
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        val mid = highlightLeft()
        compose.mainClock.advanceTimeBy(600)
        return listOf(start, mid, highlightLeft(), segmentLeft(4), segmentLeft(7))
    }

    @Test
    fun theSegmentHighlightSlidesToTheNewChoice() {
        val (start, mid, end, four, seven) = slide(reduced = false)
        assertEquals(four, start, 1f)
        assertTrue("mid-slide at $mid, between $four and $seven", mid > four + 1f && mid < seven - 1f)
        assertEquals(seven, end, 1f)
    }

    @Test
    fun theSegmentHighlightJumpsUnderReducedMotion() {
        val (start, mid, end, four, seven) = slide(reduced = true)
        assertEquals(four, start, 1f)
        assertEquals(seven, mid, 1f)
        assertEquals(seven, end, 1f)
    }

    // look2 pinned preview (spec 4, 12.2): 184 dp at rest on a wide enough phone, 96 dp scrolled,
    // compact from font scale 1.5, never over 30% of the window, and with the footer under 40%.
    @Test
    fun pinnedPreviewSizes() {
        val w = 328.dp
        assertEquals(184.dp, PinnedPreviewSize.height(w, 800.dp, 0.dp, 1f, 0f, mainPage = true, big = false))
        assertEquals(96.dp, PinnedPreviewSize.height(w, 800.dp, 0.dp, 1f, 1f, mainPage = true, big = false))
        assertEquals(96.dp, PinnedPreviewSize.height(w, 800.dp, 0.dp, 2f, 0f, mainPage = true, big = false))
        assertEquals(150.dp, PinnedPreviewSize.height(w, 500.dp, 0.dp, 1f, 0f, mainPage = true, big = false))
        assertEquals(120.dp, PinnedPreviewSize.height(w, 800.dp, 200.dp, 1f, 0f, mainPage = true, big = false))
        assertEquals(96.dp, PinnedPreviewSize.height(w, 800.dp, 0.dp, 1f, 0f, mainPage = false, big = false))
        assertEquals(184.dp, PinnedPreviewSize.height(w, 800.dp, 0.dp, 1f, 0f, mainPage = false, big = true))
    }

    // look2 notes: a message with no action shows no button, and each message says when it leaves
    // (replaced by a newer one, or dismissed), once.
    @Test
    fun notesLeaveOnce() {
        val state = UndoSnackbarState()
        val gone = mutableListOf<String>()
        compose.setContent { CytTheme { UndoSnackbarHost(state) } }
        compose.runOnIdle { state.note("Copied.", null, null) { gone += "copied" } }
        compose.onNodeWithText("Copied.").assertExists()
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.runOnIdle { state.note("Options reset.", "Undo", {}) { gone += "reset" } }
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertEquals(listOf("copied", "reset"), gone)
        assertNull(state.current)
    }
}
