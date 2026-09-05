// Root build script. Plugins are applied per-module with apply false here so
// versions stay centralized while each module opts in to what it needs.
plugins {
    kotlin("multiplatform") version "2.0.20" apply false
    kotlin("android") version "2.0.20" apply false
    kotlin("jvm") version "2.0.20" apply false
    id("com.android.library") version "8.5.2" apply false
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    kotlin("plugin.serialization") version "2.0.20" apply false
}
