package ai.promptstudio.tvclean.core.engine

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Issues #1273 (Mi Box S), #1631 (SHIELD) and #1502 (Sony): the home screen switch never opened and
// the report said only VERIFY_FAILED. On a 4K TV the forced screenshot of openSection was over the
// 2 MiB pull cap. Each failed check of openSection now writes one line saying which, and after a
// large screenshot (over 4 MiB) the unforced ones wait 20 s instead of 5 s.
class Issue1273ScreenshotTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    private suspend fun TestScope.finished(h: Harness, engine: Engine): RunState.Finished {
        val plan = h.setupPlan()
        engine.start(plan, h.tv, plan.hash, h.pass())
        h.settle(engine)
        return assertIs<RunState.Finished>(engine.runState(h.tv.tvId).value)
    }

    @Test fun aFailedScreenshotSaysSoWithTheLastGoodSize() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        h.prober.screenshotOkCount = 1   // the run's first screenshot, then none
        val lines = mutableListOf<String>()
        val fin = finished(h, h.engine(log = { lines += it }))
        assertEquals(HomeOutcome.VERIFY_FAILED, fin.home)
        assertEquals(listOf("engine: the home screen switch did not open: the screenshot failed (no PNG came back), the last one was 4 bytes"), lines)
    }

    @Test fun aScreenshotThatNeverCameBackSaysSo() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        h.prober.screenshotFails = true
        val lines = mutableListOf<String>()
        assertEquals(HomeOutcome.VERIFY_FAILED, finished(h, h.engine(log = { lines += it })).home)
        assertEquals(listOf("engine: the home screen switch did not open: the screenshot failed (no PNG came back), none came back before"), lines)
    }

    @Test fun aHomeScreenThatDidNotStartSaysSo() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        h.tv.startFails = true
        val lines = mutableListOf<String>()
        assertEquals(HomeOutcome.VERIFY_FAILED, finished(h, h.engine(log = { lines += it })).home)
        assertEquals(listOf("engine: the home screen switch did not open: the start of the new home screen did not succeed"), lines)
    }

    // A switch that opens writes none of these lines.
    @Test fun anOpenedSwitchWritesNoLine() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val lines = mutableListOf<String>()
        val engine = h.engine(log = { lines += it })
        val plan = h.setupPlan()
        engine.start(plan, h.tv, plan.hash, h.pass())
        advanceTimeBy(20_000); runCurrent()
        assertIs<RunState.AwaitingConfirm>(engine.runState(h.tv.tvId).value)
        assertTrue(lines.none { "did not open" in it }, "$lines")
    }

    // The check screen asks for an unforced screenshot every 2 s: one every 5 s for a small one, one
    // every 20 s once the last was over 4 MiB. The forced one on the check screen is still taken, so
    // the window is the 90 s of a recent thumbnail.
    @Test fun aLargeScreenshotSlowsTheUnforcedOnes() = runTest(timeout = TEST_TIMEOUT) {
        suspend fun TestScope.unforced(bytes: ByteArray): Int {
            val h = Harness(this)
            h.tv.screenshot = bytes
            val engine = h.engine()
            val plan = h.setupPlan()
            engine.start(plan, h.tv, plan.hash, h.pass())
            advanceTimeBy(20_000); runCurrent()
            val s = assertIs<RunState.AwaitingConfirm>(engine.runState(h.tv.tvId).value)
            assertEquals(h.clock.nowMs() - 20_000 + 15_000 + 90_000, s.deadlineMs, "a fresh forced thumbnail")
            val before = h.prober.calls.count { it == "screenshot" }
            advanceTimeBy(60_000); runCurrent()
            assertIs<RunState.AwaitingConfirm>(engine.runState(h.tv.tvId).value)
            val taken = h.prober.calls.count { it == "screenshot" } - before
            engine.confirmHome(h.tv.tvId); h.settle(engine)
            return taken
        }
        val small = unforced(png)
        val large = unforced(png + ByteArray((DefaultEngine.LARGE_THUMBNAIL_BYTES + 1).toInt()))
        assertTrue(small in 9..12, "small: $small")
        assertTrue(large in 2..3, "large: $large")
    }
}
