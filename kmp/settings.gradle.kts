rootProject.name = "FlightPulse"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":common")
include(":app")
include(":wearApp")
include(":compose-desktop")
