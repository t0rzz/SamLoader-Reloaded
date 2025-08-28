plugins {
    // Version placeholders; we’ll pin them upon first Gradle sync
    kotlin("multiplatform") version "2.0.0" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    id("com.android.application") version "8.5.0" apply false
}

allprojects {
    group = "dev.t0rzz.samloaderreloaded"
    version = "1.0.6"
}
