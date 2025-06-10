import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" // KSP version for Kotlin 2.0.0
    //id("com.android.library") // Removed Android plugin
}

kotlin {
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "17" // Set JVM target to 17 for Kotlin 2.0.0
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                val room_version = "2.6.1" // Define Room version
                implementation("androidx.room:room-runtime:$room_version")
                implementation("androidx.room:room-common:$room_version")
            }
        }
        val desktopMain by getting {
            dependencies {
                // Desktop-specific dependencies can go here
            }
        }
        // Add generated KSP source to the desktop compilation
        getByName("desktopMain") {
             kotlin.srcDir("build/generated/ksp/desktop/desktopMain/kotlin")
        }
    }
}

dependencies {
    val room_version = "2.6.1" // Define Room version again for this block
    // KSP dependency for the 'desktop' JVM target
    add("kspDesktop", "androidx.room:room-compiler:$room_version")
}

// android { // Removed Android configuration
//     // ...
// }
