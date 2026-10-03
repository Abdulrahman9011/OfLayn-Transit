plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}
android {
    namespace = "com.oflayn.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.oflayn.transit"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    // Bundle the baseline fare manifest (already inside the signed APK) as an asset.
    sourceSets["main"].assets.srcDir("../domain/fare/src/main/resources")
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:fare"))
    implementation(project(":domain:sources"))
    implementation(project(":domain:transit"))
    implementation(project(":domain:ai"))
    implementation(project(":domain:voice"))
    implementation(libs.play.services.location)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.maplibre.android)
    implementation(libs.litertlm.android)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splash)
}
