// Wear OS app: Compose for Wear Material UI over :common.
plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.deskradar.wear"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.deskradar.wear"
        minSdk = 30 // Wear OS 3+
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        debug {
            // Dev convenience: screen would otherwise time out mid-testing (looks like a crash).
            // Real battery cost — never do this in a release build. See docs/design.md.
            buildConfigField("boolean", "KEEP_SCREEN_ON", "true")
        }
        release {
            buildConfigField("boolean", "KEEP_SCREEN_ON", "false")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":common"))
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.wear:wear:1.4.0")
}
