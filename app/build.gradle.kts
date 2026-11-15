plugins { id("com.android.application") }
android {
    namespace = "com.couchjoust"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.couchjoust"
        minSdk = 25
        targetSdk = 37
        versionCode = 5
        versionName = "2.0.0"
        ndk { abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
        testInstrumentationRunner = "com.couchjoust.GameplayInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].assets.srcDir("../joust-kotlin-primer/assets")
    androidResources { ignoreAssetsPattern = "!*.md:!*.html:!tools:!reference:!dac-traces:!events" }
    packaging.resources.excludes += setOf("darwin-*/**", "linux-*/**", "win32-*/**")
}
dependencies {
    implementation("io.github.lux-uno:magic-wormhole:1.0.1")
    implementation("computer.iroh:iroh-android:1.2.0") {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    implementation("net.java.dev.jna:jna:5.19.1@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.core:core:1.18.0")
    testImplementation("junit:junit:4.13.2")
}
