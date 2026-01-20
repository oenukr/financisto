plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kover)
}

android {
    namespace = "ru.orangesoftware.financisto"

    compileSdk = 36

    defaultConfig {
        applicationId = "ru.orangesoftware.financisto"
        minSdk = 29
        targetSdk = 30 // must be 35+ for publishing to Google Play
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
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
    val composeBom = platform(compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Material Design 3
    implementation(compose.material3)
    implementation(compose.extended.icons)

    // Android Studio Preview support
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    // UI Tests
    androidTestImplementation(compose.ui.test.junit4)
    debugImplementation(compose.ui.test.manifest)

    // Compose integration with activities
    implementation(libs.activity.compose)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Webview
    implementation(libs.accompanist.webview)

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
    implementation(libs.koin.compose)
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
    implementation(libs.lifecycle.viewmodel.ktx)

    // Timber logging
    implementation(libs.timber)

    implementation(libs.kotlinx.datetime)


    implementation(libs.appcompat)

    implementation(libs.google.play.base)
    implementation(libs.google.play.drive)

    implementation(libs.androidannotations.api)

    implementation(libs.java.collections)

    implementation("com.dropbox.core:dropbox-core-sdk:5.4.5")
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.okio)
    implementation(libs.eventbus)
    implementation(libs.materialdatetimepicker)
    implementation(libs.commons.io)
    implementation(libs.glide)
    implementation(libs.glide.compose)

    // Rx libs
    implementation(libs.rxjava3.bridge)
    implementation(libs.rxjava3.rxjava)
    implementation(libs.rxjava3.rxandroid)
    implementation(libs.rxjava2.rxjava)
    implementation(libs.rxjava2.rxandroid)
    implementation(libs.reactive.streams)
    implementation(libs.rximagepicker)

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.11.1") // can't be used because of the current target sdk
    testImplementation("org.hamcrest:hamcrest:3.0")
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
}

kover {
    reports {
        filters {
            excludes {
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
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
