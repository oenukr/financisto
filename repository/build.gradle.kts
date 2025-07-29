@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.room.gradle.plugin)
    alias(libs.plugins.ksp)
//    alias(libs.plugins.plugin.serialization)
    kotlin("plugin.serialization") version "2.1.21"
}

kotlin {

//    jvm {
//        val main by compilations.getting {
//            compileTaskProvider.configure {
//                compilerOptions {
//                    // Set up the Kotlin compiler options for the 'main' compilation:
//                    jvmTarget.set(JvmTarget.JVM_21)
//                }
//            }
//        }
//    }

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidLibrary {
        namespace = "ru.orangesoftware.financisto.repository"
        compileSdk = 35
        minSdk = 24

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "repositoryKit"

    iosX64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    linuxX64()

    wasmJs {
        browser()
    }

    js {
        browser {
            testTask {
                enabled = false
            }
        }
        binaries.executable()
    }

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin.stdlib)
                // Add KMP dependencies here
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.sqlite)
                implementation(libs.sqlite.bundled)
                implementation(libs.sqlite.ktx)

                implementation(libs.room.ktx)
                implementation(libs.room.runtime)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val androidMain by getting {
            dependencies {
                // Add Android-specific dependencies here. Note that this source set depends on
                // commonMain by default and will correctly pull the Android artifacts of any KMP
                // dependencies declared in commonMain.
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.test.runner)
                implementation(libs.test.core)
                implementation(libs.ext.junit)
            }
        }

//        val jvmMain by getting {
//            dependencies {
//            }
//        }
//
//        val jvmTest by getting { // Ensure this block exists
//            dependencies {
//                implementation(kotlin("test-junit")) // Or other JVM testing libraries
//                // Add any other dependencies specific to your JVM tests
//                implementation(libs.kotlin.test)
//            }
//        }

        iosMain {
            dependencies {
                // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
                // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
                // part of KMP’s default source set hierarchy. Note that this source set depends
                // on common by default and will correctly pull the iOS artifacts of any
                // KMP dependencies declared in commonMain.
            }
        }

        val jsMain by getting {

        }
    }

    // Configure all compilations of all targets:
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspWasmJs", libs.room.compiler)
    add("kspJs", libs.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

tasks.withType<KotlinJsCompile>().configureEach {
    compilerOptions {
        target.set("es2015")
    }
}
