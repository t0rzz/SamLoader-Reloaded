plugins {
    id("com.android.application")
    kotlin("multiplatform")
    id("org.jetbrains.compose")
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
        versionCode = 1
        versionName = "0.0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
