package ai.promptstudio.tvclean.testing.fixtures

import ai.promptstudio.tvclean.core.model.BlobRef
import ai.promptstudio.tvclean.core.model.Component
import ai.promptstudio.tvclean.core.model.DebugState
import ai.promptstudio.tvclean.core.model.DeviceFacts
import ai.promptstudio.tvclean.core.model.Endpoint
import ai.promptstudio.tvclean.core.model.EnabledState
import ai.promptstudio.tvclean.core.model.HomeClaimant
import ai.promptstudio.tvclean.core.model.HomeResolution
import ai.promptstudio.tvclean.core.model.LauncherArtifact
import ai.promptstudio.tvclean.core.model.Level
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.PackageState
import ai.promptstudio.tvclean.core.model.PlanId
import ai.promptstudio.tvclean.core.model.Sha256
import ai.promptstudio.tvclean.core.model.Snapshot
import ai.promptstudio.tvclean.core.model.SnapshotReason
import ai.promptstudio.tvclean.core.model.TrustRecord
import ai.promptstudio.tvclean.core.model.Tv
import ai.promptstudio.tvclean.core.model.TvId
import ai.promptstudio.tvclean.core.ops.FailurePolicy
import ai.promptstudio.tvclean.core.ops.OpRenderer
import ai.promptstudio.tvclean.core.planner.GuardedSection
import ai.promptstudio.tvclean.core.planner.Plan
import ai.promptstudio.tvclean.core.planner.PlanKind
import ai.promptstudio.tvclean.core.planner.PlanSummary
import ai.promptstudio.tvclean.core.planner.Step
import ai.promptstudio.tvclean.core.store.SnapshotCodec

// Ready-made values built from the reference TV capture, so every stream's tests start from the
// same TV. All of them are plain data; nothing here talks to a device.
object Samples {
    const val NOW_MS = 1_760_000_000_000L
    val zeroHash = Sha256("0".repeat(64))
    val launcherComponent = Component(PackageName("ai.promptstudio.tvclean.launcher"), ".MainActivity")
    val stockLauncher = Component(PackageName("com.google.android.tvlauncher"), ".MainActivity")

    fun miTvFacts(): DeviceFacts = Fixtures.miTv().facts()
    fun miTvId(): TvId = Fixtures.miTv().tvId()

    // The capture's packages: system when installed under /system or /product, disabled when the
    // after-debloat list says so (pass disabled = emptySet() for the pre-debloat TV). All 107,
    // including the framework package "android", which PackageName admits by name (RFC-W1-2).
    fun miTvPackages(disabled: Set<String> = Fixtures.miTv().disabledAfter.toSet()): Map<PackageName, PackageState> =
        Fixtures.miTv().packagesAllText.lines().filter { it.startsWith("package:") }.associate { line ->
            val path = line.removePrefix("package:").substringBeforeLast('=')
            val pkg = line.substringAfterLast('=')
            PackageName(pkg) to PackageState(
                enabled = if (pkg in disabled) EnabledState.DISABLED_USER else EnabledState.ENABLED,
                system = path.startsWith("/system/") || path.startsWith("/product/") || path.startsWith("/vendor/"),
                versionCode = 0, signer = null, persistent = false, tvInput = pkg == "com.droidlogic.tvinput",
                label = null, installedByUs = false,
            )
        }

    // A snapshot whose contentHash is the SnapshotCodec hash, so it survives every store.
    fun snapshot(
        tvId: TvId = miTvId(),
        facts: DeviceFacts = miTvFacts(),
        packages: Map<PackageName, PackageState> = miTvPackages(),
        home: HomeResolution = HomeResolution(stockLauncher, listOf(HomeClaimant(stockLauncher, 2))),
        reason: SnapshotReason = SnapshotReason.BASELINE,
        takenAtMs: Long = NOW_MS,
    ): Snapshot {
        val draft = Snapshot(tvId, takenAtMs, reason, facts, packages, home, emptyMap(), null, emptyList(), null, emptyList(), zeroHash)
        return draft.copy(contentHash = SnapshotCodec.hash(draft))
    }

    fun miTvSnapshot(reason: SnapshotReason = SnapshotReason.BASELINE): Snapshot = snapshot(reason = reason)

    fun tv(id: TvId = miTvId(), facts: DeviceFacts = miTvFacts()): Tv = Tv(
        id = id, nickname = "Mi TV 4A", room = "Living room", facts = facts,
        endpoints = listOf(Endpoint.PlainTcp("192.0.2.10")), lastSubnet = "192.0.2.0/24",
        trust = TrustRecord(adbKeyId = "fake-key", tlsCertSha256 = null, firstConnectedAt = NOW_MS, pairedAt = null),
        profileId = "xiaomi-mitv-axso0", level = Level.SIMPLE, launcher = null, wizard = null,
        lastSeenAt = NOW_MS, debugging = DebugState.ON,
    )

    fun launcherArtifact(blob: BlobRef = BlobRef(zeroHash, 260_000, "bundled:launcher.apk")): LauncherArtifact =
        LauncherArtifact(PackageName("ai.promptstudio.tvclean.launcher"), 1, "0.1.0", blob.sha256, zeroHash, blob)

    // A plan the way the planner would hand it over: rendered with `renderer`, hash = planHash.
    fun plan(
        ops: List<Op>,
        tvId: TvId = miTvId(),
        renderer: OpRenderer = FakeOpRenderer(),
        kind: PlanKind = PlanKind.SETUP,
        id: PlanId = PlanId("01FAKEPLAN0000000000000001"),
        sections: List<GuardedSection> = emptyList(),
        base: Sha256 = zeroHash,
    ): Plan {
        val steps = ops.mapIndexed { i, op ->
            Step(i, op, renderer.render(op), prior = null, group = null, narration = "step $i", onFailure = FailurePolicy.STOP_AND_ROLLBACK)
        }
        return Plan(
            id = id, tvId = tvId, kind = kind, baseSnapshot = base, steps = steps, sections = sections,
            summary = PlanSummary(emptyList(), emptyList(), emptyList(), homeScreenPossible = true, replacesLauncher = false),
            hash = renderer.planHash(steps.map { it.rendered }), createdAtMs = NOW_MS, appVersion = "0.1.0",
        )
    }
}
