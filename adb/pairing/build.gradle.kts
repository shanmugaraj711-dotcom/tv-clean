plugins { id("tvclean.jvm") }

// TV CLEAN 0.2 (TLS pairing + connect, D9). The pairing client (SPAKE2 + PeerInfo) and the
// shared ADB TLS identity (self-signed client cert from the phone's ADB RSA key).
dependencies {
    implementation(project(":core:model"))
    implementation(project(":adb:wire"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.bouncycastle.bctls)
    testImplementation(libs.kotlinx.coroutines.test)
}
