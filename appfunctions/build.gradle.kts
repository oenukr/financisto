plugins {
    alias(libs.plugins.android.library)
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

dependencies {
    implementation(libs.appfunctions.core)
    ksp(libs.appfunctions.compiler)

    implementation(libs.kotlinx.coroutines.core)
    
    val koinBom = platform(libs.koin.bom)
    implementation(koinBom)
    implementation(libs.koin.core)
}
