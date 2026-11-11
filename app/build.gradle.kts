plugins {
    id("tvclean.android.app")
    id("tvclean.serialization")
}

android {
    // The app's version, in one place for every store flavour and for F-Droid's checkupdates
    // (STORES-PLAN 0 and 3): GitHub, F-Droid and Google Play ship the same versionCode and versionName.
    defaultConfig {
        // The 0.5.0 release: code 31. 0.4.8's code 30 shipped twice (the v2 build and the
        // v3 package-identity fix), so the two could not be told apart on a phone — the
        // friend's Mi TV 4I test of v3 could not prove which build had run. A fix release
        // always takes a new code from here, so Settings > Apps names the build under test.
        // The name is 0.5.0, not 0.4.9 (owner's pick): a visible jump from 0.4.8, so the
        // friend can tell at a glance that it is the new build — the change (real cache
        // clearing, four new looks) is big enough to earn it. Same code 31.
        versionCode = 31
        versionName = "0.5.0"
    }
    // STORES-PLAN 1: one codebase, two store builds, the same applicationId and signing certificate.
    // `full` (GitHub and F-Droid) checks GitHub for a newer release and offers the optional add-ons;
    // `play` (Google Play) has no update check, no add-on downloads, keeps the TV's software updaters
    // on and names no GitHub host. The differences live in src/full and src/play (Store.kt and the
    // network security config) and nowhere else.
    flavorDimensions += "store"
    productFlavors {
        create("full") {
            dimension = "store"
            isDefault = true
        }
        create("play") {
            dimension = "store"
        }
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    // A text report per linted variant (build/reports/lint-results-<variant>.txt). No shared
    // textOutput: with two linted variants (fullDebug under `lint`, playDebug under `check`) their
    // report copies raced for one file and failed `check` at random (RF-STORES-030 M3).
    lint {
        textReport = true
    }
    packaging {
        resources {
            // Our TLS pairing pulls in Bouncy Castle 1.86 (bcprov, bcpkix, bctls, bcutil);
            // all four jars ship META-INF/LICENSE.md and the Java-resource merge refuses
            // the duplicate. The licence text is not needed inside the APK.
            excludes += "META-INF/LICENSE.md"
        }
    }
}

dependencies {
    implementation(project(":feature:connect"))
    implementation(project(":feature:setup"))
    implementation(project(":feature:manage"))
    implementation(project(":feature:launcher"))
    implementation(project(":feature:modules"))
    implementation(project(":feature:backup"))
    implementation(project(":ui:design"))
    implementation(project(":core:flows"))
    implementation(project(":android:work"))
    implementation(project(":android:platform"))
    implementation(project(":adb:session"))
    // T7: the real implementations behind Seams (layer 9 may use every lower layer).
    implementation(project(":adb:wire"))
    // RFC-PAIRING-1: AppGraph wires the TLS identity through AndroidTlsUpgrader /
    // AndroidPairingRunner / KeystoreAdbSigner, whose signatures name :adb:pairing types
    // (TlsIdentity, PairingClient, PairingCrypto, PairingException, AdbCertificate).
    implementation(project(":adb:pairing"))
    implementation(project(":core:ops"))
    implementation(project(":core:spec"))
    implementation(project(":core:probe"))
    implementation(project(":core:planner"))
    implementation(project(":core:engine"))
    implementation(project(":core:backup"))
    implementation(project(":core:trust"))
    implementation(project(":core:registry"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":testing:fixtures"))
    testImplementation(project(":testing:fake-adbd"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// :benchmark measures the full build (it asks for store=full); the play benchmark variant is not
// built. Unit tests run on both flavours (testFullDebugUnitTest and testPlayDebugUnitTest under
// `check`); the benchmark build type is release code with the debug key and gets no unit tests.
androidComponents {
    beforeVariants(selector().withBuildType("benchmark")) { variant ->
        if (variant.productFlavors.contains("store" to "play")) variant.enable = false
        variant.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
    }
}

// Lint runs on the default flavour (full) with `lint`; the play sources and resources too.
tasks.named("check") { dependsOn("lintPlayDebug") }

// The size gate for every store build: the full APK and the play APK (its AAB holds the same code).
tasks.register("checkApkSizeRelease") {
    group = "verification"
    description = "Fails when the fullRelease or the playRelease APK is larger than the size gate."
    dependsOn("checkApkSizeFullRelease", "checkApkSizePlayRelease")
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    maxHeapSize = "1280m"
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
