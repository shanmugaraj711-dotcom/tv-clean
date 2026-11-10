package ai.promptstudio.tvclean.core.planner

import ai.promptstudio.tvclean.core.model.BlobRef
import ai.promptstudio.tvclean.core.model.LauncherArtifact
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.Sha256
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.choices
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.plan
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.refused
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.setup
import ai.promptstudio.tvclean.core.planner.PlannerFixtures.snapshot
import ai.promptstudio.tvclean.core.store.SnapshotCodec
import ai.promptstudio.tvclean.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// RF-PROFILES-63 P1: the launcher and the TV curtain need Android 8 (SDK 26). On an older TV
// (sony-kd-55x8509c is Android 7.0) a Setup is the clean-up alone; the planner refuses an install
// or a switch asked for anyway and never weaves the curtain in.
class OldAndroidPlanTest {
    private val curtainSha = Sha256("e".repeat(64))
    private val curtain = LauncherArtifact(PackageName("tv.cleanyour.curtain"), 1, "0.1", curtainSha, Samples.launcherArtifact().signer,
        BlobRef(curtainSha, 49_571, "bundled:tvclean-curtain.apk"))
    private val config = BlobRef(Sha256("c".repeat(64)), 100, "blobs/config")

    private fun android7() = snapshot().let { s -> s.copy(facts = s.facts.copy(sdk = 24, release = "7.0")) }.let { it.copy(contentHash = SnapshotCodec.hash(it)) }

    @Test fun theCleanUpAloneBelowAndroid8() {
        val p = plan(setup(choices = choices(installLauncher = false, takeover = false)).copy(curtain = curtain, config = config), snapshot = android7())
        assertTrue(p.steps.isNotEmpty())
        assertTrue(p.steps.all { it.op is Op.Disable }, p.steps.map { it.op::class.simpleName }.toString())
        assertTrue(p.sections.isEmpty())
        assertTrue(p.steps.none { it.group == DefaultPlanner.GROUP_CURTAIN })
        // The same request on Android 8 or newer gets the curtain.
        val newer = plan(setup(choices = choices(installLauncher = false, takeover = false)).copy(curtain = curtain))
        assertTrue(newer.steps.any { it.group == DefaultPlanner.GROUP_CURTAIN })
    }

    @Test fun anInstallOrASwitchAskedForAnywayIsRefused() {
        assertEquals(listOf(DefaultPlanner.REASON_OLD_ANDROID), refused(setup(), snapshot = android7()))
        assertEquals(listOf(DefaultPlanner.REASON_OLD_ANDROID), refused(setup(choices = choices(installLauncher = false)), snapshot = android7()))
    }
}
