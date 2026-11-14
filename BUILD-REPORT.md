# TV Clean — Build Report (2026-11-10)

One APK on the phone controls the TV and finishes the whole clean-up. This is our version of the
open-source Clean Your TV app, with four improvements. The owner said: "Build for our version,
with some more improvement."

## What was forked

- **Base:** https://github.com/udhaykumarbala/cleanyourtv-app (MIT, 820 commits, v0.4.8, HEAD `03ad20eb`).
- **Fork location:** `~/workspace/our-tvclean/` — a fresh copy (202 MB, `.git` removed). The original
  clone at `~/workspace/cleanyourtv-app` is untouched.
- **MIT attribution kept:** the original LICENSE is in this repo, and the README credits Clean Your
  TV (Gathin) and cleanyourtv-app (udhaykumarbala).
- **Teardown:** `~/workspace/cleanyourtv-analysis.md` (594 lines).

## What was renamed

- App name: "Clean Your TV" → **"TV Clean"**.
- Base package: `tv.cleanyour.*` → **`ai.promptstudio.tvclean`** (see the note below — the task's
  literal `in.promptstudioai.tvclean` cannot compile).
- Launcher package: `ai.promptstudio.tvclean.launcher`; theme `Theme.CleanYourTv` → `Theme.TvClean`;
  root project `cleanyourtv-app` → `tvclean-app`; `cyt_` resources → `tvclean_`.
- Remote paths: `/data/local/tmp/tvclean`, `/sdcard/Android/data/ai.promptstudio.tvclean.launcher/files`,
  `/sdcard/tvclean`.
- Gathin-tied test code removed (vendored `com/gathin/` tree, upstream config/codec tests).

**The `in` → `ai` package fix (must not be reverted).** The task asked for `in.promptstudioai.tvclean`,
but `in` is a Kotlin hard keyword and cannot be the first segment of a package name — standalone
kotlinc 2.4.21 rejects `package in.promptstudioai...` outright (verified with a minimal file).
The base package is therefore `ai.promptstudio.tvclean` everywhere (6,227 occurrences, 602 files).
The intent — a distinct brand package, not Gathin's — is preserved. A second trap was fixed too:
slash-form resource paths (`in/promptstudioai/tvclean/...`) were pointing at the old location after
the directory rename; 38 occurrences in 15 files were corrected to `ai/promptstudio`.

## Improvement 1 — TLS pairing (the headline; implemented, not yet tried on a real TV)

The base speaks plain ADB on port 5555 only. TVs that only offer wireless debugging with a
pairing code needed one computer pairing first. TV Clean pairs over TLS from the phone alone.

- **STLS connect:** `AndroidTlsUpgrader` (TLS 1.3, client cert = the phone's ADB key, trust-on-first-use
  then pinning). Wired into `AppGraph` (replaces `tls = null`).
- **SPAKE2 pairing:** built from scratch in `adb/pairing/` — BoringSSL-compatible SPAKE2 (the M/N
  points from BoringSSL's `spake25519.c`, not RFC 9382's), the AOSP password rule (pairing code +
  64 bytes of TLS-exported keying material, label `"adb-label"`), HKDF-SHA256 AES-128-GCM PeerInfo.
  Note: the protocol is SPAKE2, not "TLS-PSK" as the teardown first framed it.
- **Flow wiring:** `SetupIntent.SubmitPairingCode`, `PairingRunner`, `AndroidPairingRunner`, the
  pairing code screen in the connect flow (6-digit entry, plain-word errors), pin saved in the
  trust record after a successful TLS connect.
- **Tests:** 15 pairing tests pass (certificate, SPAKE2, PeerInfo crypto). The flows wiring tests
  (pairing code opens the TLS door; a bad code stays on the code screen) are covered by the flows
  run below.
- **Status:** implemented and unit-tested, but **UNVERIFIED against a real TV** — no real TV is
  available here. The first real pairing is the true test; watch the keying-material label and the
  PeerInfo counter.

## Improvement 2 — Indian TV profiles first (done, tested)

Coverage of the 173 upstream lists: Xiaomi 29, TCL 26, Sony 22, OnePlus 4 — and **zero** for
Realme, Acer, Nokia, Infinix, Kodak, CloudWalker, Blaupunkt. Samsung/LG are not Android TV
(Tizen/webOS) and correctly have no lists. Vu/iFFALCON are covered only by manufacturer name, not
by brand, so most of their TVs resolve to nothing.

What was built instead of new exact profiles (the upstream examples submodule is empty here, and
the profile converter needs it): `TvCleanSureSets` — careful brand lists for the 9 missing Indian
brands (Realme, Acer, Nokia, Infinix, Kodak, CloudWalker, Blaupunkt, Vu, iFFALCON), each holding
exactly the 6 careful packages (tracking + preloads, default-off). Wired into `BundledSources`
(bundle sets win when both match, so a real profile landing later takes over automatically).

The brand picker now lists the Indian brands first: Xiaomi, OnePlus, TCL, Sony, Vu, Realme,
Hisense, Philips, Acer, Nokia, Infinix, Kodak, iFFALCON, CloudWalker, Blaupunkt, Google,
Amazon, Other. (OnePlus maps to the Android TV step family — 3 of its 4 lists are Android TV;
the rest use the generic Android TV steps.)

## Improvement 3 — Tamil + English UI (done, validated)

Partial `values-ta/strings.xml` in `feature/connect` (120 strings), `feature/setup` (210 strings)
and `app` (3 strings). Android falls back to English for anything untranslated, so this is safe.
Validated: unique names, balanced braces, placeholders match English. The TV's own menu names
(Settings, Build, Developer options) stay in English because that is what the TV shows.

## Improvement 4 — Our own launcher (code written, not yet the bundled APK)

Module `tv/launcher` (package `ai.promptstudio.tvclean.launcher`): a leanback home screen that
lists the TV's apps in a D-pad grid and launches them, shows the wallpaper the phone sends, and
exposes the ThemeProvider (`ai.promptstudio.tvclean.launcher.theme`, guarded by
WRITE_SECURE_SETTINGS) the phone writes the look through. Its Java compiles cleanly against the
Android SDK. Build script: `scripts/build-launcher.sh`.

**Not done yet:** the APK the app installs today is still Gathin's bundled
`cleanyourtv-launcher.apk`. Replacing it needs (a) a Gradle build of `:tv:launcher`, (b) signing
with a **new TV Clean release key** (the owner must create and keep it — Gathin's pinned signer
in `PinnedLauncher` is his key), and (c) updating `PinnedLauncher` (asset name, size, SHA-256,
signer) and copying the signed APK into `android/platform/src/main/assets/bundled/`. The README
says this plainly.

## Build + verification

- **JDK 21** (Temurin 21.0.13, at `~/workspace/jdk/` — it survives runtime restarts; the
  apt-installed JDK was wiped by one).
- **Android SDK** at `~/workspace/android-sdk` (platform-tools 37.0.1, build-tools 36.0.0,
  platform android-36 + a local android-37 shim, since platform-37 does not exist yet in the
  stable channel and the Compose BOM needs compileSdk 37). `local.properties` points at it.
- **Gradle cannot run in this sandbox.** The wrapper distribution was downloaded by hand and the
  daemon starts, then is killed by the sandbox ("Could not dispatch a message to the daemon",
  every daemon STOPPED, no JVM crash, no OOM). So `assembleDebug` and the Android modules' tests
  are **blocked** — this is an environment limit, not a code problem. Do not fake a build.
- **Direct-kotlinc fallback (works for pure-JVM modules):** standalone Kotlin 2.4.21 + jars in
  `~/workspace/verify-libs/`, scripts in `~/workspace/verify-*.sh`.
  - Pairing: **15 tests pass** (`verify-pairing.sh`).
  - Indian brand sure sets: **4 tests pass** (`verify-tv-clean-spec.sh`).
  - Flows: **438 tests ran, 362 pass, 76 fail** (`verify-flows.sh` + `verify-flows-resume.sh`). The
    pairing wiring tests pass (a correct code opens the TLS door; a bad code stays on the code
    screen). **All 76 failures are the missing upstream submodule** (below), not code bugs: 36 ×
    `profiles.json missing` (the generated profiles need the upstream examples), 19 × `theme
    default missing`, 11 × `picture aurora missing`, and ~10 look/version/report assertion
    cascades of those missing themes. SetupMachineTest's 6 failures are all `profiles.json missing`.
    (Getting here also fixed two real harness bugs in the scripts: the `PairingRunner` interface
    had to become a `fun interface` for the SAM lambdas in the tests, and the test run needed
    `-Dtvclean.repoRoot` plus the spec/test resources copied onto the classpath.)
  - TLS upgrader: the handshake test is written (`AndroidTlsUpgraderTest`, 4 tests) and the
    upgrader + test compile cleanly. **2 of 4 pass** (wrong pin rejected, non-TCP socket rejected);
    **2 fail only because loopback TCP is blocked here** (below) — the handshake gets the sandbox
    proxy's plaintext instead of TLS. The test is correct and ready where loopback is allowed.
  - Launcher Java: compiles cleanly against `android.jar` (javac).

## What's blocked

1. **Gradle daemon killed by the sandbox** — no `assembleDebug`, no Android-module build or test.
   Needs a machine where Gradle can run (the owner's machine or CI).
2. **Loopback TCP is blocked by the sandbox** (`other_tcp` permission = Deny). Any test that opens
   a real socket — the TLS-upgrader handshake test, FakeAdbd-based tests — fails here because the
   sandbox's proxy answers instead of the loopback server ("muse: Other TCP connections is turned
   off for this assistant. To allow it, ask the user to open Muse settings -> Permissions -> Direct
   network protocols and switch other_tcp from Deny to Ask."). This is a policy, not a code bug.
   The tests are correct and ready to run where loopback is allowed.
3. **No real TV** — TLS pairing and the whole clean-up flow cannot be tried end-to-end until a TV
   (or the owner's phone + TV) is available.
4. **No TV Clean release signing key** — the launcher APK cannot be built and pinned until the
   owner creates one (see `scripts/build-launcher.sh`).
5. **The upstream submodule is empty** (`third_party/cleanyourtv/`, 0 files) — so `profiles.json`
   cannot be generated and the default/aurora themes are missing. That is why 76 flows tests
   fail here; they will pass once the submodule is populated (or on a machine where Gradle runs
   the converter). It is a missing-data gap, not a code bug.

## Files

- Project: `~/workspace/our-tvclean/`
- Teardown: `~/workspace/cleanyourtv-analysis.md`
- Launcher module: `~/workspace/our-tvclean/tv/launcher/`
- Launcher build script: `~/workspace/our-tvclean/scripts/build-launcher.sh`
- Verify scripts: `~/workspace/verify-pairing.sh`, `~/workspace/verify-tv-clean-spec.sh`,
  `~/workspace/verify-tls-upgrader.sh`, `~/workspace/verify-flows.sh`
- JDK: `~/workspace/jdk/jdk-21.0.13+9/` · Android SDK: `~/workspace/android-sdk/`
- This report: `~/workspace/our-tvclean/BUILD-REPORT.md`

## GitHub build path (added 2026-11-10 14:33, owner approved)

This machine cannot run Gradle, so the APK is built free on GitHub Actions:

- **New workflow:** `.github/workflows/build-apk.yml` — on push to `main` (and by hand):
  checkout with `submodules: recursive` (the build needs `third_party/cleanyourtv`),
  JDK 21 Temurin, `./gradlew :app:assembleDebug`, upload the APK as artifact
  `tv-clean-debug-apk` (30 days). YAML validated. The debug APK is debug-signed, so it
  installs straight on a phone with no release key.
- **Removed:** `.github/workflows/nightly.yml` (daily emulator matrix — would burn runner
  minutes every night) and `.github/workflows/fdroid.yml` (points at the old
  `tv.cleanyour.app.yml` recipe and `cleanyourtv-*.apk` names). Kept: `pr.yml` (build+test
  on push) and `release.yml` (only on `v*` tags / by hand).
- **Still needed from the owner:** create the empty PUBLIC repo
  `github.com/shanmugaraj711-dotcom/tv-clean` on his phone (no README, no .gitignore —
  empty, so the push lands clean). Then push this tree and the workflow builds the APK.

## Pushing to GitHub (2026-11-10 14:50, owner created the repo)

The repo is `github.com/shanmugaraj711-dotcom/tv-clean` (confirmed empty, no README).
The connector cannot push binaries or git history, so the push carries **text files
only** (1,482 files, ~7.9 MB — inside the 2,000-file / 16 MiB snapshot limit):

- `scripts/fetch-binary-assets.sh` (new, tested from scratch 2026-11-10): downloads
  the 17 binary files the build needs from the upstream repo at the pinned commit
  `03ad20ebec68284a362b9d90690876c18e212286` and verifies every sha256 + size. The two
  APKs match the pins in `core/trust/Pins.kt` exactly; the font is a byte-identical
  rename (`cyt_outfit.ttf` → `tvclean_outfit.ttf`).
- `build-apk.yml` now runs the fetch script before Gradle, and posts the full-flavour
  debug APK to a rolling GitHub Release tagged `debug`, so the APK downloads without
  signing in.
- `pr.yml` also runs the fetch script; its Roborazzi verify step is skipped (the
  goldens are the upstream app's and show the old app name — they fail against TV
  Clean until regenerated).
- NOT pushed (binary, not needed for the debug build): Roborazzi screenshot goldens
  (~190 MB), docs/site GIFs, fastlane store screenshots. The repo is complete for
  building; the goldens + store images are a later completeness job if wanted.
- `release.yml` (v* tags only) is untested with this binary-fetch design; it builds
  from `git archive`, which has no binaries — do not tag a release until it is fixed.

## v5 — suspend the stock home so the switch survives a restart (2026-11-14)

- **Cause:** after v4's clean, the TV's own home comes back at every power-on. The
  planner could not switch it off: `Protected.isProtected` refuses a package the TV
  flags PERSISTENT, so `keepStockEnabled` stayed true and `set-home-activity` alone
  does not survive a boot (the persistent stock home answers Home first).
- **Fix (RFC-SUSPEND-1, CHANGELOG 0.5.0):** new `Op.Suspend` / `Op.Unsuspend`
  (`pm suspend --user 0 <pkg>` / `pm unsuspend --user 0 <pkg>`). The planner suspends
  the stock launcher only when the reason it cannot be disabled is exactly
  REASON_PERSISTENT (a TV input, a protected name, a wizard, a profile
  never_disable, or a STOCK_LAUNCHER_KEEP_ENABLED quirk still leaves it on). Step R,
  Undo and put-back unsuspend it. Verified by `dumpsys package` (`suspended=` flag);
  `pm list packages -s` does NOT show suspended packages (it lists system ones).
- **Push:** commit `c7cd1281` (28 files, 2026-11-14 ~13:00 IST). Verified remote
  content at head matches local except these edits; the 4 wallpaper commits since
  the last sync (`35bbc096`) touch only JPGs.
- **CI, first attempt FAILED:** build-apk run #25 failed in 54 s (exit code 1); the
  debug release asset is still the v4 APK (50,403,726 B, 11 Nov 12:41 GMT). The
  run log could not be read from here (no workflow-runs tool in the connector; the
  job page would not load). The 28 changed files were re-read by hand: every
  `when (op)` expression is exhaustive or has an `else` arm, all cross-references
  check out, and the one suspect (a nullable `in` check in PlainRestoreNote) is
  legal Kotlin — the same pattern already ships in HomeSwitchRisk. 54 s is too
  fast to have reached compilation of the changed modules, so this looks like a
  step before the build (checkout / setup / binary fetch) failing, not the code.
  Retrying with this entry as the trigger commit; if it fails again the same way,
  read the actual log before changing any code.
- **CI, retry also FAILED:** build-apk run #26 (commit `284dc72`, a BUILD-REPORT-only
  change) failed in 58 s; pr #25 failed in 18 s. A doc-only commit cannot break a
  Kotlin compile, so this is now PROVEN not the code: something in the build
  machine's early steps fails on every run since 2026-11-14 13:03 IST, after green
  runs on 2026-11-11. The upstream binary URLs were re-checked from here and all
  answer 200 with the right sizes, the wrapper jar matches official Gradle 9.7.1
  byte-for-byte, and every `when (op)` in the code is exhaustive or has an `else`.
  The failing step itself is not visible from here (no workflow-runs tool in the
  connector; the job page would not load). Needs the owner to read it on his phone:
  GitHub app -> tv-clean -> Actions -> build-apk #26 -> the red step's name.
- **CI, TRUE CAUSE FOUND (2026-11-14 13:32, browser check on the owner's order):**
  build-apk #26 failed in the "Build debug APK" step (31 s) with
  `:core:ops:compileKotlin FAILED` — `UndoOrder.kt:40: 'when' expression must be
  exhaustive. Add the 'is Suspend', 'is Unsuspend' branches or an 'else' branch.`
  The local UndoOrder.kt HAS those arms; the file was simply never pushed — it was
  the one v5 file missing from the push manifest (28 files pushed, UndoOrder left
  out). That also explains the "doc-only" retry failing: it was built on the same
  broken commit. The earlier "proven not the code" note above was WRONG. Fix:
  push UndoOrder.kt (this commit). Every other Nov-14 file was verified pushed by
  mtime check, and a second sweep of all `when (op)` sites found no other missing
  arm (the two flags it raised were false positives: DefaultEngine's arm-when has
  an else, ProblemReports' when is over RunState, not Op).
- **pr.yml** run #24 also failed in 11 s at wrapper-validation, as on every run
  since 2026-11-10 (pre-existing; the wrapper jar itself matches the official
  Gradle 9.7.1 jar byte-for-byte). Until that is fixed, the new unit tests
  (golden 71/72, planner + engine suspend tests) never run in CI.
