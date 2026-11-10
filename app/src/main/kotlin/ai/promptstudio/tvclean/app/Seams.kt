package ai.promptstudio.tvclean.app

import ai.promptstudio.tvclean.core.backup.RestoreBundleWriter
import ai.promptstudio.tvclean.core.backup.RestoreNote
import ai.promptstudio.tvclean.core.backup.TvRestoreFiles
import ai.promptstudio.tvclean.core.engine.Engine
import ai.promptstudio.tvclean.core.engine.Journal
import ai.promptstudio.tvclean.core.flows.KeyInfo
import ai.promptstudio.tvclean.core.ops.ConfigTransport
import ai.promptstudio.tvclean.core.planner.Planner
import ai.promptstudio.tvclean.core.probe.Prober
import ai.promptstudio.tvclean.core.registry.ArtifactFetcher
import ai.promptstudio.tvclean.core.registry.HttpFetcher
import ai.promptstudio.tvclean.core.registry.Registry
import ai.promptstudio.tvclean.core.session.SessionManager
import ai.promptstudio.tvclean.core.spec.FactsTable
import ai.promptstudio.tvclean.core.spec.ProfileSource
import ai.promptstudio.tvclean.core.spec.ThemeSource
import ai.promptstudio.tvclean.core.store.BlobStore
import ai.promptstudio.tvclean.core.store.SnapshotStore
import ai.promptstudio.tvclean.core.trust.ApkInspector
import ai.promptstudio.tvclean.core.trust.CurtainPin
import ai.promptstudio.tvclean.core.trust.LauncherPin

// The implementations of section 5 that other streams own, as AppGraph wires them (T7, all real):
//   sessions      W2 AdbSessionManager(PlainSocketFactory, DeferredAdbSigner over KeystoreAdbSigner, tls = AndroidTlsUpgrader) — 0.2: TLS connect live; pairing = AndroidPairingRunner (RFC-PAIRING-1: TLS pairing from the phone)
//   keyInfo       W2 KeystoreAdbSigner through SignerKeyInfo (fingerprint, backing, reset)
//   prober, profiles, facts, themes  W4 AdbProber, BundledProfileSource, BundledFactsTable,
//                 BundledThemeSource behind MaterializedThemeSource (wallpapers as plain files)
//   planner, engine, journal, configTransport  W3 DefaultPlanner(..., profiles = profiles),
//                 DefaultEngine behind LiveSessionEngine, FileJournal, FileOverlayTransport
//   snapshots, restoreFiles, restoreNote, bundleWriter  W6 DocumentSnapshotStore, DefaultTvRestoreFiles,
//                 PlainRestoreNote, ZipRestoreBundleWriter with the real ShellRenderer
//   blobs, launcherPin, registry, fetcher, inspector, http  W5 FileBlobStore, BundledLauncherPin,
//                 IndexRegistry(sources = null), ResumableArtifactFetcher behind InstallableOnlyFetcher,
//                 PackageManagerApkInspector, UrlConnectionFetcher
//   curtainPin    W5 BundledCurtainPin (RFC-CURTAIN-1: the TV curtain, pinned like the launcher)
data class Seams(
    val sessions: SessionManager,
    val keyInfo: KeyInfo,
    val prober: Prober,
    val profiles: ProfileSource,
    val facts: FactsTable,
    val themes: ThemeSource,
    val planner: Planner,
    val engine: Engine,
    val journal: Journal,
    val configTransport: ConfigTransport,
    val snapshots: SnapshotStore,
    val restoreFiles: TvRestoreFiles,
    val restoreNote: RestoreNote,
    val bundleWriter: RestoreBundleWriter,
    val blobs: BlobStore,
    val launcherPin: LauncherPin,
    val registry: Registry,
    val fetcher: ArtifactFetcher,
    val inspector: ApkInspector,
    val http: HttpFetcher,
    val curtainPin: CurtainPin,
)
