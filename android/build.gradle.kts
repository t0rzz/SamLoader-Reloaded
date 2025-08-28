plugins {
    id("com.android.application")
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget()
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation("org.jetbrains.compose.ui:ui-text:${org.jetbrains.compose.ComposeBuildConfig.composeVersion}")
                implementation("androidx.activity:activity-compose:1.9.2")
            }
        }
    }
}

android {
    namespace = "dev.t0rzz.samloaderreloaded"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.t0rzz.samloaderreloaded"
        minSdk = 26
        targetSdk = 34
        versionCode = 10003
        versionName = "1.0.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
