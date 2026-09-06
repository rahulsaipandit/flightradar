// Business-logic-only module: data models, OpenSky API client, radar projection math.
// Consumed by :app (Android phone), :wearApp (Wear OS), and :compose-desktop directly
// as a Gradle dependency, and by the native SwiftUI apps in /iosApp and /watchosApp via
// this module's iOS framework (XCFramework) output.
plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(17)

    jvm("desktop")

    androidTarget()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { target ->
        target.binaries.framework {
            baseName = "common"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // These three appear in public signatures (OpenSkyApiClient's default HttpClient
                // param, Flow-returning functions, JsonElement in Aircraft.kt) so consumers like
                // wearApp need them resolvable too — api, not implementation.
                api("io.ktor:ktor-client-core:2.3.12")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

                implementation("io.ktor:ktor-client-cio:2.3.12")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
            }
        }
        val commonTest by getting
        val desktopMain by getting
        val androidMain by getting {
            dependencies {
                implementation("com.google.android.gms:play-services-location:21.3.0")
            }
        }
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.12")
            }
        }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}

android {
    namespace = "com.flightpulse.common"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
