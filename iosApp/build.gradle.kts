plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    // Always declare iOS targets to satisfy KMP requirement of at least one target.
    // Building these targets still requires macOS; on other hosts they won't be executed by default.
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    // Ensure iOS Native targets produce Framework binaries so link*Framework* tasks exist
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        if (konanTarget.family.isAppleFamily) {
            binaries.framework {
                baseName = "Duofrost"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
            }
        }
    }
}
