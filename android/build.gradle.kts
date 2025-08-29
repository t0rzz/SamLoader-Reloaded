plugins {
    id("com.android.application")
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget()
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation(platform("androidx.compose:compose-bom:2024.08.00"))
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.foundation:foundation")
                implementation("androidx.compose.material:material")
                implementation("androidx.compose.ui:ui-text")
                implementation("androidx.activity:activity-compose:1.9.2")
                implementation("io.ktor:ktor-client-okhttp:2.3.12")
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
        versionCode = 10026
        versionName = "1.0.26"
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
