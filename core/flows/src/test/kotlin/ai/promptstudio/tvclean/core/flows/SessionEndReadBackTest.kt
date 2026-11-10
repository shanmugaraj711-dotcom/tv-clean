package ai.promptstudio.tvclean.core.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import ai.promptstudio.tvclean.core.model.AdbError
import ai.promptstudio.tvclean.core.model.DebugState
import ai.promptstudio.tvclean.core.model.Endpoint
import ai.promptstudio.tvclean.core.model.SettingKey
import ai.promptstudio.tvclean.core.model.SnapshotReason
import ai.promptstudio.tvclean.core.model.TvId
import ai.promptstudio.tvclean.core.planner.PlanRequest
import ai.promptstudio.tvclean.core.session.OpenResult
import ai.promptstudio.tvclean.core.session.SessionManager
import ai.promptstudio.tvclean.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// 0.4.8 (beta4 follow-up): Finish switched debugging off, yet the TV's record said "on" on the QA emulator, so the
// debugging step never said "we switched it off". The emulator's own process listens on port 5555 whatever the TV's
// adbd does, and the record followed the port. A port that stays open is now asked: the known-TV handshake and a fresh
// read of adb_enabled, and the record says what the TV has.
@OptIn(ExperimentalCoroutinesApi::class)
class SessionEndReadBackTest {
    // A port that takes the connection and never speaks ADB (the emulator's listener with the TV's adbd off).
    private class Silent(inner: SessionManager) : SessionManager by inner {
        override suspend fun openKnown(endpoint: Endpoint, expected: TvId): OpenResult = awaitCancellation()
    }

    private class Answers(inner: SessionManager, val error: AdbError) : SessionManager by inner {
        override suspend fun openKnown(endpoint: Endpoint, expected: TvId): OpenResult = OpenResult.Failed(error)
    }

    private fun Harness.readsAdbEnabled(value: String?) {
        prober.snapshotFor = { reason -> Samples.miTvSnapshot(reason).let { it.copy(settings = it.settings + (SettingKey.ADB_ENABLED to value)) } }
    }

    private suspend fun Harness.switchOffWith(sessions: SessionManager = this.sessions): DebugState {
        val t = knownTv()
        discovery.open5555[HOST] = true
        return SessionEnd(sessions, discovery, tvs, runner, foreground).switchOff(t.id)
    }

    @Test fun aPortThatStaysOpenButNeverAnswersIsOff() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val run = async { h.switchOffWith(Silent(h.sessions)) }
        testScheduler.advanceUntilIdle()
        assertEquals(DebugState.OFF, run.await())
        assertEquals(DebugState.OFF, h.tvs.tvs.value.single().debugging, "the record says what the TV has")
        assertTrue(h.planner.requests.any { it == PlanRequest.DebugOff })
        assertTrue(h.sessions.opens.size == 1, "no open that could show the Allow dialog: only the known TV's opening one")
    }

    @Test fun aPortThatAnswersButIsNotAnAdbdIsOff() = runTest(timeout = 30.seconds) {
        val h = Harness()
        assertEquals(DebugState.OFF, h.switchOffWith(Answers(h.sessions, AdbError.Transport.Unreachable)))
        assertEquals(DebugState.OFF, h.tvs.tvs.value.single().debugging)
    }

    @Test fun theTvAnsweringWithAdbEnabledZeroIsOff() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.readsAdbEnabled("0")
        assertEquals(DebugState.OFF, h.switchOffWith())
        assertTrue(Endpoint.PlainTcp(HOST) in h.sessions.knownOpens, "only the known-TV handshake")
    }

    @Test fun theTvAnsweringWithDebuggingStillOnIsOn() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.readsAdbEnabled("1")
        assertEquals(DebugState.ON, h.switchOffWith())
        assertEquals(DebugState.ON, h.tvs.tvs.value.single().debugging)
        assertEquals(SnapshotReason.BASELINE, h.snapshots.latest(Samples.miTvId())?.reason, "the read is a check: no snapshot is stored")
    }

    @Test fun anAdbdThatRefusesTheKeyMeansDebuggingIsOn() = runTest(timeout = 30.seconds) {
        val h = Harness()
        assertEquals(DebugState.ON, h.switchOffWith(Answers(h.sessions, AdbError.Auth.Denied)))
    }

    @Test fun aReadThatFailsAfterTheHandshakeIsUnknown() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.prober.snapshotFor = { throw ai.promptstudio.tvclean.core.probe.ProbeException(ai.promptstudio.tvclean.core.probe.ProbeStage.SETTINGS, "no answer") }
        val t = h.knownTv()
        h.discovery.open5555[HOST] = true
        // The DebugOff plan reads the stored snapshot; only the read back fails.
        assertEquals(DebugState.UNKNOWN, SessionEnd(h.sessions, h.discovery, h.tvs, h.runner, h.foreground).switchOff(t.id))
        assertEquals(DebugState.UNKNOWN, h.tvs.tvs.value.single().debugging)
    }

    @Test fun aClosedPortIsOffWithoutAnyHandshake() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val t = h.knownTv()
        h.discovery.open5555[HOST] = false
        assertEquals(DebugState.OFF, SessionEnd(h.sessions, h.discovery, h.tvs, h.runner, h.foreground).switchOff(t.id))
        assertTrue(h.sessions.knownOpens.isEmpty())
    }
}
