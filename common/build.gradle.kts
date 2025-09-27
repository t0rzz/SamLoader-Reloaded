plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    linuxX64()
    mingwX64()

    // Only register iOS targets when explicitly enabled (e.g., on macOS CI)
    val enableIos = providers.gradleProperty("enableIos").map { it.toBoolean() }.orElse(false).get()

    if (enableIos) {
        iosArm64()
        iosX64()
        iosSimulatorArm64()

        // Register CommonCrypto cinterop for all Kotlin/Native iOS targets with per-target SDK selection
        targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
            // Only apply to iOS families
            if (konanTarget.family.isAppleFamily) {
                compilations.getByName("main").cinterops {
                    val commoncrypto by creating {
                        defFile(project.file("src/nativeInterop/cinterop/ios/commoncrypto.def"))

                        val isSimulator = (konanTarget.name == org.jetbrains.kotlin.konan.target.KonanTarget.IOS_X64.name) ||
                                (konanTarget.name == org.jetbrains.kotlin.konan.target.KonanTarget.IOS_SIMULATOR_ARM64.name)
                        val deviceSdk = System.getenv("SDKROOT")
                        val simSdk = System.getenv("SIM_SDKROOT")
                        val chosenSdk = when {
                            isSimulator && !simSdk.isNullOrBlank() -> simSdk
                            !isSimulator && !deviceSdk.isNullOrBlank() -> deviceSdk
                            // fallback to device SDK if simulator SDK missing
                            !deviceSdk.isNullOrBlank() -> deviceSdk
                            else -> null
                        }
                        if (!chosenSdk.isNullOrBlank()) {
                            // Configure include dirs and compiler opts pointing to the correct SDK
                            includeDirs(project.file("$chosenSdk/usr/include"))
                            compilerOpts(
                                "-isysroot", chosenSdk,
                                "-I$chosenSdk/usr/include",
                                "-F$chosenSdk/System/Library/Frameworks",
                                "-framework", "CommonCrypto",
                                "-fno-modules"
                            )
                        }
                    }
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("io.ktor:ktor-client-core:2.3.12")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
                implementation("io.ktor:ktor-client-logging:2.3.12")
                implementation("io.ktor:ktor-utils:2.3.12")
                implementation("com.soywiz.korlibs.krypto:krypto:4.0.10")
                implementation("com.russhwolf:multiplatform-settings:1.1.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:2.3.12")
                implementation("com.fleeksoft.ksoup:ksoup:0.2.4")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }
        val linuxX64Main by getting {
            dependencies {
                implementation("io.ktor:ktor-client-curl:2.3.12")
                implementation("com.russhwolf:multiplatform-settings:1.1.1")
            }
        }
        val mingwX64Main by getting {
            dependencies {
                implementation("io.ktor:ktor-client-winhttp:2.3.12")
                implementation("com.russhwolf:multiplatform-settings:1.1.1")
            }
        }
        // Removed androidMain to avoid AGP requirement in :common
        if (enableIos) {
            val iosMain by creating {
                dependsOn(commonMain)
                dependencies {
                    implementation("io.ktor:ktor-client-darwin:2.3.12")
                    implementation("com.soywiz.korlibs.krypto:krypto:4.0.10")
                    implementation("com.russhwolf:multiplatform-settings:1.1.1")
                }
            }
            val iosArm64Main by getting { dependsOn(iosMain) }
            val iosX64Main by getting { dependsOn(iosMain) }
            val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        }
    }
}
