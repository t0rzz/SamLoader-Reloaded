plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    // Removed androidTarget() to avoid requiring AGP in :common
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Networking/JSON/etc. to be filled during port
            }
        }
        val commonTest by getting
        val jvmMain by getting
        // Removed androidMain to avoid AGP requirement in :common
        val iosMain by creating {
            dependsOn(commonMain)
        }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}
