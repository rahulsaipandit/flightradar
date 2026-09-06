// Android phone app: Jetpack Compose UI over :common.
plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.flightpulse.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.flightpulse.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":common"))
    implementation(compose.material3)
    implementation("androidx.activity:activity-compose:1.9.2")
}
