plugins {
    // compileSdk 36(Android 16)官方最低要求 AGP 8.9.1;此处用 8.11.1 + Gradle 8.13
    id("com.android.application") version "8.11.1" apply false
    id("com.android.library") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
