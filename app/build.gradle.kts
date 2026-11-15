plugins { id("com.android.application") }
android {
    namespace = "com.couchjoust"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.couchjoust"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "com.couchjoust.GameplayInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].assets.srcDir("../joust-kotlin-primer/assets")
    androidResources { ignoreAssetsPattern = "!*.md:!*.html:!tools:!reference:!dac-traces:!events" }
}
dependencies {
    implementation("androidx.activity:activity:1.13.0")
    testImplementation("junit:junit:4.13.2")
}
