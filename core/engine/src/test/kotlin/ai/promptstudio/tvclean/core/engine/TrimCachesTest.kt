package ai.promptstudio.tvclean.core.engine

import kotlinx.coroutines.test.runTest
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.RunStatus
import ai.promptstudio.tvclean.core.probe.Prober
import ai.promptstudio.tvclean.core.session.TvSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// RFC-TRIM-1: `pm trim-caches` runs, and the MB it freed is the TV's free space after the step
// minus before it, journaled on the step's Done record — never a guess, never the bytes asked for.
class TrimCachesTest {
    // A prober whose free-space reads move: the first call answers `first`, later calls `rest`.
    // The engine under test reads twice per trim step (before the line, after the verify), so the
    // two values are the measurement's before and after.
    private class ShiftingFreeMbProber(private val base: ScriptedProber, private val first: Int, private val rest: Int) : Prober by base {
        private var calls = 0
        override suspend fun freeDataMb(session: TvSession): Int {
            base.calls += "freeDataMb"
            return if (calls++ == 0) first else rest
        }
    }

    private fun engine(h: Harness, prober: Prober, lines: MutableList<String>) =
        DefaultEngine(h.renderer, prober, h.journal, h.snapshots, h.plans, h.clock, h.scope.backgroundScope, h.profiles, log = { lines += it })

    @Test fun theFreedMbIsMeasuredBeforeAndAfter() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val lines = mutableListOf<String>()
        val engine = engine(h, ShiftingFreeMbProber(h.prober, 900, 1400), lines)
        val plan = h.opsPlan(Op.TrimCaches(1_073_741_824L))
        engine.start(plan, h.tv, plan.hash, h.pass())
        assertEquals(RunStatus.COMPLETED, assertIs<RunState.Finished>(h.settle(engine)).status)
        assertEquals(1, h.tv.execs("pm trim-caches 1073741824"))
        assertEquals(2, h.prober.calls.count { it == "freeDataMb" }, "one read before the line, one after the verify")
        val done = h.records(plan).filterIsInstance<JournalRecord.Done>().single()
        assertEquals(500, done.freedMb)
        assertTrue("engine: trim-caches freed 500 MB (free space 900 MB before, 1400 MB after)" in lines, lines.toString())
    }

    @Test fun freedNothingIsZeroNotUnknown() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val engine = engine(h, ShiftingFreeMbProber(h.prober, 900, 900), mutableListOf())
        val plan = h.opsPlan(Op.TrimCaches(1_073_741_824L))
        engine.start(plan, h.tv, plan.hash, h.pass())
        assertEquals(RunStatus.COMPLETED, assertIs<RunState.Finished>(h.settle(engine)).status)
        assertEquals(0, h.records(plan).filterIsInstance<JournalRecord.Done>().single().freedMb)
    }

    @Test fun aFailedReadLeavesTheStepDoneButUnmeasured() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        h.prober.freeMb = null
        val lines = mutableListOf<String>()
        val engine = h.engine(log = { lines += it })
        val plan = h.opsPlan(Op.TrimCaches(1_073_741_824L))
        engine.start(plan, h.tv, plan.hash, h.pass())
        assertEquals(RunStatus.COMPLETED, assertIs<RunState.Finished>(h.settle(engine)).status)
        assertNull(h.records(plan).filterIsInstance<JournalRecord.Done>().single().freedMb)
        assertTrue("engine: trim-caches: the TV's free storage could not be read (facts)" in lines, lines.toString())
    }
}
