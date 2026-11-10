plugins {
    `kotlin-dsl`
}

group = "ai.promptstudio.tvclean.buildlogic"

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    implementation(libs.serialization.gradlePlugin)
    implementation(libs.roborazzi.gradlePlugin)
    testImplementation(libs.junit4)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

gradlePlugin {
    plugins {
        register("root") {
            id = "tvclean.root"
            implementationClass = "RootConventionPlugin"
        }
        register("jvm") {
            id = "tvclean.jvm"
            implementationClass = "JvmConventionPlugin"
        }
        register("serialization") {
            id = "tvclean.serialization"
            implementationClass = "SerializationConventionPlugin"
        }
        register("androidLib") {
            id = "tvclean.android.lib"
            implementationClass = "AndroidLibConventionPlugin"
        }
        register("androidCompose") {
            id = "tvclean.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidRoborazzi") {
            id = "tvclean.android.roborazzi"
            implementationClass = "AndroidRoborazziConventionPlugin"
        }
        register("androidFeature") {
            id = "tvclean.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidApp") {
            id = "tvclean.android.app"
            implementationClass = "AndroidAppConventionPlugin"
        }
        register("tvApp") {
            id = "tvclean.tv.app"
            implementationClass = "TvAppConventionPlugin"
        }
        register("androidBenchmark") {
            id = "tvclean.android.benchmark"
            implementationClass = "AndroidBenchmarkConventionPlugin"
        }
    }
}
