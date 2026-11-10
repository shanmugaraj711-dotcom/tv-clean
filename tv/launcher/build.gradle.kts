// The TV launcher (TV Clean 0.2): our own leanback home screen for the TV, package
// ai.promptstudio.tvclean.launcher. It lists the TV's apps in a grid and launches them, shows the
// look the phone sends (the wallpaper from the ThemeProvider), and exposes the ThemeProvider the
// phone writes the look through (authority ai.promptstudio.tvclean.launcher.theme).
//
// It is the replacement for Gathin's bundled cleanyourtv-launcher.apk. It is NOT yet the bundled
// APK: building it needs the Gradle build (blocked in this sandbox), then signing it with a NEW
// TV Clean release key (the pinned signer in core/trust PinnedLauncher is Gathin's key, and the
// owner's key must be created and kept safe by the owner), then updating PinnedLauncher
// (asset name, size, sha256, package, version) and copying the signed APK into
// android/platform/src/main/assets/bundled/. Until then the bundled APK stays Gathin's and the
// README says so. See the build script scripts/build-launcher-on-forge.sh.
plugins {
    id("tvclean.tv.app")
}

android {
    namespace = "ai.promptstudio.tvclean.launcher"
    defaultConfig {
        applicationId = "ai.promptstudio.tvclean.launcher"
        versionCode = 1
        versionName = "0.1"
    }
}
