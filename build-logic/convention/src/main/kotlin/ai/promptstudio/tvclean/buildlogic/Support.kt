package ai.promptstudio.tvclean.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

// System property every JVM and Robolectric test gets, so :testing:fixtures can find the repo's
// fixtures/ folder and the upstream submodule without guessing the working directory.
const val repoRootProperty = "tvclean.repoRoot"

object Toolchain {
    // D10 says 36; Compose BOM 2026.09.00 (D34) requires compileSdk 37 in its AAR metadata, so
    // compileSdk is 37 while targetSdk stays 36 (DECISIONS-W1.md, D-W1-3).
    // TV CLEAN fork note (2026-11-10): Android SDK platform-37 is NOT yet published in the
    // stable SDK channel (platform-37_r01/r02/r03 all 404; repository tops out at android-36).
    // This machine builds against a LOCAL android-37 platform directory that is a copy of
    // android-36 re-labelled to API 37 (see ~/workspace/android-sdk/platforms/android-37/README).
    // The code targets SDK 36 and calls no API-37-only method, so the copy compiles cleanly;
    // when Google publishes platform-37, replace the directory with the real one.
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 36
    const val MIN_SDK = 28
    // RFC-CURTAIN-1: the TV curtain runs on every TV the phone app supports (Android 8 and later).
    const val TV_MIN_SDK = 26
    val JAVA = JavaVersion.VERSION_21
}

// :feature:setup -> ai.promptstudio.tvclean.feature.setup; hyphens are not valid in package names.
fun Project.cytNamespace(): String = "ai.promptstudio.tvclean." + path.removePrefix(":").replace(':', '.').replace('-', '_')
