plugins {
    id("com.android.application")
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

kotlin {
    androidTarget()
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":common"))
                // Explicit Compose versions to avoid deprecated platform(...) BOM usage
                val composeVersion = "1.7.3"
                implementation("androidx.compose.ui:ui:$composeVersion")
                implementation("androidx.compose.foundation:foundation:$composeVersion")
                implementation("androidx.compose.material:material:$composeVersion")
                implementation("androidx.compose.material:material-icons-extended:$composeVersion")
                implementation("androidx.compose.ui:ui-text:$composeVersion")
                implementation("androidx.activity:activity-compose:1.9.2")
                implementation("io.ktor:ktor-client-okhttp:2.3.12")
                implementation("org.slf4j:slf4j-android:1.7.36")
                implementation("androidx.documentfile:documentfile:1.0.1")
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
        versionCode = 10035
        versionName = "1.0.35"
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


// Configure Kotlin compilerOptions with the modern DSL to set jvmTarget
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
