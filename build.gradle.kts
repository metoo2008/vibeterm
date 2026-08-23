plugins {
    // AGP 8.11.x 是首个官方支持 compileSdk 36(Android 16)的稳定线;需 Gradle 8.13+
    id("com.android.application") version "8.11.1" apply false
    id("com.android.library") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
