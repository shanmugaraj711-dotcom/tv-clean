#!/usr/bin/env bash
# Build TV Clean's own launcher APK (module :tv:launcher) and place it in the app's assets.
#
#   scripts/build-launcher.sh
#
# What it does:
#   1. Builds :tv:launcher:assembleRelease (unsigned, no R8) with Gradle.
#   2. Signs it with the TV Clean release key (apksigner, v2 + v3) and verifies the signature.
#   3. Copies the signed APK to android/platform/src/main/assets/bundled/tvclean-launcher.apk.
#   4. Prints the size, SHA-256 and signer SHA-256, so PinnedLauncher in core/trust (Pins.kt)
#      can be updated to the new asset — the verifyLauncherPin task fails the build until they
#      agree. Update ASSET_NAME ("tvclean-launcher.apk"), SHA256, SIZE, PACKAGE
#      ("ai.promptstudio.tvclean.launcher"), VERSION_CODE, VERSION_NAME and SIGNER_SHA256.
#
# The signing key (a NEW key for TV Clean — Gathin's key in PinnedLauncher is his, and a launcher
# signed with a different key is a different app to Android) does NOT exist yet. Create it once,
# on the owner's machine, and keep it safe — it is the project's signing identity:
#   keytool -genkeypair -v -keystore tvclean-launcher-release.keystore -alias tvclean-launcher \
#     -keyalg RSA -keysize 4096 -validity 10000
# Then point this script at it:
#   TVCLEAN_LAUNCHER_KEYSTORE=/path/to/tvclean-launcher-release.keystore \
#   TVCLEAN_LAUNCHER_KEY_ALIAS=tvclean-launcher \
#   scripts/build-launcher.sh
# The keystore password is read from the terminal (never on the command line or in a file).
# Until this runs, the app installs Gathin's bundled cleanyourtv-launcher.apk (see README).
set -euo pipefail
cd "$(dirname "$0")/.."

KEYSTORE="${TVCLEAN_LAUNCHER_KEYSTORE:-}"
ALIAS="${TVCLEAN_LAUNCHER_KEY_ALIAS:-tvclean-launcher}"
OUT="android/platform/src/main/assets/bundled/tvclean-launcher.apk"
UNSIGNED="tv/launcher/build/outputs/apk/release/launcher-release-unsigned.apk"

[ -n "$KEYSTORE" ] && [ -f "$KEYSTORE" ] || {
    echo "TV Clean launcher release key not found." >&2
    echo "Create it once on the owner's machine (see the header of this script), then run:" >&2
    echo "  TVCLEAN_LAUNCHER_KEYSTORE=/path/to/tvclean-launcher-release.keystore scripts/build-launcher.sh" >&2
    exit 1
}

./gradlew :tv:launcher:assembleRelease
[ -f "$UNSIGNED" ] || { echo "build did not produce $UNSIGNED" >&2; exit 1; }

read -r -s -p "Keystore password for $KEYSTORE: " KSPASS; echo
SIGNED="$(mktemp --suffix=.apk)"
apksigner sign --ks "$KEYSTORE" --ks-key-alias "$ALIAS" --ks-pass "pass:$KSPASS" \
    --v2-signing-enabled true --v3-signing-enabled true --out "$SIGNED" "$UNSIGNED"
apksigner verify --print-certs "$SIGNED" | grep -E "Signer #1|SHA-256 digest" || true
cp "$SIGNED" "$OUT"
rm -f "$SIGNED"

echo "Bundled launcher: $OUT"
sha256sum "$OUT"
echo "Now update PinnedLauncher in core/trust/src/main/kotlin/ai/promptstudio/tvclean/core/trust/Pins.kt:"
echo "  ASSET_NAME = \"tvclean-launcher.apk\""
echo "  PACKAGE = \"ai.promptstudio.tvclean.launcher\""
echo "  (size and SHA-256 above; signer SHA-256 from the apksigner output; version from the build)"
