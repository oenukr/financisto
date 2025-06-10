pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // needed for: rximagepicker
    }

    versionCatalogs {
        create("libs") {
            from(files("libs.versions.toml"))
        }
        create("compose") {
            from(files("compose.versions.toml"))
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

include(":app")
include(":shared")
