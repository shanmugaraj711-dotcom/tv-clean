package ai.promptstudio.tvclean.core.trust

import ai.promptstudio.tvclean.core.model.LauncherArtifact
import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.model.Sha256
import ai.promptstudio.tvclean.core.store.BlobStore

// Trust anchors compiled into the app. Changing one is a reviewed code change, never a data file.
object TrustRoots {
    // Root of the module index chain: signs keys.json only (KeysFile). Release key K2R, generated
    // 2026-10-20 for 0.1.0; it replaced the development keys of K2, which never signed a public
    // build. The private half stays offline with the maintainer (docs/history/DECISIONS-W5.md, K2R).
    const val INDEX_ROOT_PUBLIC_KEY = "RWTBm+H9rjMuaX944TBay7uDMxQEC8Q+E8yF1p7hyGl7Fa+8bzBKE4io"   // key id 692E33AEFDE19BC1

    // The sequence of the keys.json bundled with this build: an older keys file is refused.
    const val BUNDLED_KEYS_SEQUENCE = 1L

    val indexRoot: MinisignKey get() = Ed25519Minisign.parsePublicKey(INDEX_ROOT_PUBLIC_KEY)

    fun indexVerifier(): MinisignIndexVerifier = MinisignIndexVerifier(indexRoot, keysFloor = BUNDLED_KEYS_SEQUENCE)
}

// The launcher APK bundled in the phone app (D13, Q1): upstream github.com/Gathin23/tvclean at
// main after v3.0.0, commit b06ab21 (third_party/tvclean), launcher 3.0 (versionCode 9),
// `assembleRelease` twice, both re-signed with the dedicated launcher release key and compared by
// scripts/build-launcher-on-forge.sh (docs/history/reviews/LAUNCHER-300-build-output.txt). The
// verifyLauncherPin task (part of :core:trust check and of :android:platform preBuild) fails the
// build when the asset differs.
// PACKAGE IS THE MANIFEST PACKAGE OF THE PINNED APK, verified with `aapt2 dump badging` on the
// bundled asset (com.gathin.tvhome, 2026-11-10): the pin check verifies size, sha256 and signer
// ONLY, never the package. The 2026-11-10 rebrand renamed PACKAGE to ai.promptstudio.tvclean
// .launcher (our own launcher module's package, which is NOT bundled) while the asset stayed
// upstream's: every install check, StartActivity, SetHome and VerifyHome then looked for a
// package that can never exist on a TV, the switch failed at openSection on every run, and the
// "another home screen app, Gathin Home" stop card looped on every "Use the new home screen"
// tap (Mi TV 4I, Nov 2026). If the asset is ever re-fetched or our own launcher ships,
// re-verify PACKAGE with aapt2 and update it together with the pin — and update Launcher.pkg
// (Ports.kt), SwitchOffRule.OUR_LAUNCHER, the theme authority, the overlay folder and the
// curtain's PACKAGE in the same change.
object PinnedLauncher {
    const val ASSET_NAME = "tvclean-launcher.apk"
    const val UPSTREAM_COMMIT = "b06ab21105c49900cb1ed76b24414dba1e21d0db"
    const val PACKAGE = "com.gathin.tvhome"
    const val VERSION_CODE = 9L
    const val VERSION_NAME = "3.0"
    const val SIZE = 540_541L
    const val SHA256 = "9fcc21c0eb81548c2432b3bf89c1d9c6b6ad17dcf7f7fc2e8d90d7ccbea7d60e"
    // SHA-256 of the signing certificate (DER), "O=cleanyourtv, CN=Clean Your TV launcher", v2 and v3.
    const val SIGNER_SHA256 = "576418154bec6505bfc709ccaa6ae85b6e09d98eb89f4a25147ca1b4b0505132"
}

// The TV curtain bundled in the phone app (RFC-CURTAIN-1): module :tv:curtain of this repo, built
// twice and compared, then signed with the launcher release key by scripts/build-curtain-on-forge.sh.
// One signer to pin: SIGNER_SHA256 is the launcher's. The verifyCurtainPin task (part of :core:trust
// check and of :android:platform preBuild) fails the build when the asset differs.
object PinnedCurtain {
    const val ASSET_NAME = "tvclean-curtain.apk"
    // The manifest package of the pinned asset, verified with `aapt2 dump badging`
    // (tv.cleanyour.curtain, 2026-11-10) — same warning as PinnedLauncher above: this must name
    // the asset's own package, not our curtain module's (which is not bundled).
    const val PACKAGE = "tv.cleanyour.curtain"
    const val ACTIVITY = ".CurtainActivity"
    const val RECEIVER = ".CueReceiver"
    const val VERSION_CODE = 1L
    const val VERSION_NAME = "0.1"
    const val SIZE = 65_955L
    const val SHA256 = "eb21e112c97121a3ed7f4db1c7fbf2169bc172a14bb3f8ca781079de4651662e"
    const val SIGNER_SHA256 = PinnedLauncher.SIGNER_SHA256
}

// LauncherPin over the BlobStore's bundled asset. bundled() refuses an asset whose bytes are not the
// pinned ones, so a swapped asset can never be offered as the launcher.
class BundledLauncherPin(private val blobs: BlobStore) : LauncherPin {
    override fun bundled(): LauncherArtifact {
        val blob = blobs.bundled(PinnedLauncher.ASSET_NAME)
        check(blob.sha256.hex == PinnedLauncher.SHA256 && blob.size == PinnedLauncher.SIZE) {
            "bundled launcher does not match its pin (sha256 ${blob.sha256.hex}, ${blob.size} bytes)"
        }
        return LauncherArtifact(
            pkg = PackageName(PinnedLauncher.PACKAGE),
            versionCode = PinnedLauncher.VERSION_CODE,
            versionName = PinnedLauncher.VERSION_NAME,
            sha256 = Sha256(PinnedLauncher.SHA256),
            signer = Sha256(PinnedLauncher.SIGNER_SHA256),
            blob = blob,
        )
    }
}

// CurtainPin over the BlobStore's bundled asset, refusing an asset whose bytes are not the pinned ones.
class BundledCurtainPin(private val blobs: BlobStore) : CurtainPin {
    override fun bundled(): LauncherArtifact {
        val blob = blobs.bundled(PinnedCurtain.ASSET_NAME)
        check(blob.sha256.hex == PinnedCurtain.SHA256 && blob.size == PinnedCurtain.SIZE) {
            "bundled curtain does not match its pin (sha256 ${blob.sha256.hex}, ${blob.size} bytes)"
        }
        return LauncherArtifact(
            pkg = PackageName(PinnedCurtain.PACKAGE),
            versionCode = PinnedCurtain.VERSION_CODE,
            versionName = PinnedCurtain.VERSION_NAME,
            sha256 = Sha256(PinnedCurtain.SHA256),
            signer = Sha256(PinnedCurtain.SIGNER_SHA256),
            blob = blob,
        )
    }
}
