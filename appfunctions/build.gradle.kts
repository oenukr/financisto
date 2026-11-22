import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ru.orangesoftware.financisto.appfunctions"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.file("config/detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(true)
        sarif.required.set(true)
        md.required.set(true)
    }
}

dependencies {
    implementation(libs.appfunctions.core)
    ksp(libs.appfunctions.compiler)

    implementation(libs.kotlinx.coroutines.core)

    val koinBom = platform(libs.koin.bom)
    implementation(koinBom)
    implementation(libs.koin.core)

    detektPlugins(libs.detekt.formatting)
}
