plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // alias(libs.plugins.compose.compiler) // Removed Compose compiler plugin
    alias(libs.plugins.kover)
}

android {
    namespace = "ru.orangesoftware.financisto"

    compileSdk = 35

    defaultConfig {
        applicationId = "ru.orangesoftware.financisto"
        minSdk = 28
        targetSdk = 30
        versionCode = 122
        versionName = "1.8.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        javaCompileOptions {
            annotationProcessorOptions {
                argument("androidManifestFile", "$projectDir/src/main/AndroidManifest.xml")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
        // compose = true // Removed Compose feature
    }

    testOptions {
        animationsDisabled = true
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    sourceSets["test"].resources.srcDirs("src/test/resources")

    packaging {
        resources.excludes.add("META-INF/DEPENDENCIES.txt")
        resources.excludes.add("META-INF/LICENSE")
        resources.excludes.add("META-INF/LICENSE.txt")
        resources.excludes.add("META-INF/NOTICE.txt")
        resources.excludes.add("META-INF/NOTICE")
        resources.excludes.add("META-INF/LICENSE")
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/notice.txt")
        resources.excludes.add("META-INF/license.txt")
        resources.excludes.add("META-INF/dependencies.txt")
        resources.excludes.add("META-INF/LGPL2.1")
    }

    lint {
        // If set to true, turns off analysis progress reporting by lint.
        quiet = true
        // If set to true (default), stops the build if errors are found.
        abortOnError = false
        // If set to true, lint only reports errors.
        ignoreWarnings = false
        // If set to true, lint also checks all dependencies as part of its analysis.
        // Recommended for projects consisting of an app with library dependencies.
        checkDependencies = true

        checkReleaseBuilds = false
    }
}

dependencies {
    // Compose
    // val composeBom = platform(compose.bom) // Removed Compose BOM
    // implementation(composeBom)
    // androidTestImplementation(composeBom)

    // Material Design 3
    // implementation(compose.material3) // Removed Material Design 3

    // Android Studio Preview support
    // implementation(compose.ui.tooling.preview) // Removed Tooling Preview
    // debugImplementation(compose.ui.tooling) // Removed Tooling

    // UI Tests
    // androidTestImplementation(compose.ui.test.junit4) // Removed UI Tests
    // debugImplementation(compose.ui.test.manifest) // Removed UI Test Manifest

    // Compose integration with activities
    // implementation(libs.activity.compose) // Removed Activity Compose

    // Permissions
    // implementation(libs.accompanist.permissions) // Removed Accompanist Permissions

    // Webview
    // implementation(libs.accompanist.webview) // Removed Accompanist Webview

    // Preferences DataStore (SharedPreferences like APIs)
    implementation(libs.datastore.preferences)
    implementation(libs.datastore.preferences.rx)

    // Koin - dependency injection
    // Koin - BOM
    val koinBom = platform(libs.koin.bom)
    implementation(koinBom)
    androidTestImplementation(koinBom)
    // Koin - core
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    // Koin - Java Compatibility
    implementation(libs.koin.android.compat)
    // implementation(libs.koin.compose) // Removed Koin Compose
    // Koin - tests
    testImplementation(libs.koin.test)

    // Biometrics
    implementation(libs.biometric)

    // WorkManager
    implementation(libs.workmanager)

    // Chart
    implementation(libs.koala.chart)

    // Google Kotlin extensions
    implementation(libs.core.ktx)
    implementation(libs.collection.ktx)

    // Lifecycle extensions
    implementation(libs.lifecycle.runtime.ktx)

    // Timber logging
    implementation(libs.timber)

    // implementation(project(":shared")) // Removed shared module dependency
    implementation(libs.legacy.support)
    implementation(libs.appcompat)

    implementation(libs.google.play.base)
    implementation(libs.google.play.drive)
    implementation(libs.google.play.plus)

    implementation(libs.androidannotations.api)

    implementation(libs.java.collections)

    implementation("com.dropbox.core:dropbox-core-sdk:5.4.5")
    implementation(libs.okhttp)
    implementation(libs.okio)
    implementation(libs.eventbus)
    implementation(libs.materialdatetimepicker)
    implementation(libs.commons.io)
    implementation(libs.glide)
    // implementation(libs.glide.compose) // Removed Glide Compose

    // Rx libs
    implementation(libs.rxjava3.bridge)
    implementation(libs.rxjava3.rxjava)
    implementation(libs.rxjava3.rxandroid)
    implementation(libs.rxjava2.rxjava)
    implementation(libs.rxjava2.rxandroid)
    implementation(libs.reactive.streams)
    implementation(libs.rximagepicker)

    // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar")))) // Removed as rfc2445-no-joda.jar was the only file

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.11.1") // can't be used because of the current target sdk
    testImplementation("org.hamcrest:hamcrest:2.2")
}

kover {
    reports {
        filters {
            excludes {
        // annotatedBy("androidx.compose.ui.tooling.preview.Preview") // Removed Preview annotation
            }
        }

        verify {
            rule("line-coverage") {
                minBound(20)
            }
            rule("branch-coverage") {
                minBound(20)
            }
            rule("instruction-coverage") {
                minBound(20)
            }
        }

        total {
        }
    }
}

// composeCompiler { // Removed Compose compiler options
//     enableStrongSkippingMode = true
// }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
kotlin {
    jvmToolchain(21)
}

tasks.withType<Test> {
    testLogging {
        events("failed", "passed", "skipped")
        setExceptionFormat("full")
        showStandardStreams = true
    }
    reports.junitXml.required.set(true)
    reports.html.required.set(true)
    outputs.upToDateWhen { false }
}
