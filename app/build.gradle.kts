plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

android {
    namespace = "ru.orangesoftware.financisto"

    compileSdk = 28

    defaultConfig {
        applicationId = "ru.orangesoftware.financisto"
        minSdk = 19
        targetSdk = 28
        versionCode = 122
        versionName = "1.8.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
        javaCompileOptions {
            annotationProcessorOptions {
                argument("androidManifestFile", "$projectDir/src/main/AndroidManifest.xml".toString())
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
    }

    testOptions {
        animationsDisabled = true
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
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
        abortOnError = false
        checkReleaseBuilds = false
    }
}

val AAVersion = "4.6.0"

dependencies {
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("androidx.multidex:multidex:2.0.1")

    implementation("com.google.android.gms:play-services-base:17.0.0")
    implementation("com.google.android.gms:play-services-drive:17.0.0")
    implementation("com.google.android.gms:play-services-plus:17.0.0")

    kapt("org.androidannotations:androidannotations:$AAVersion")
    implementation("org.androidannotations:androidannotations-api:$AAVersion")

    implementation("com.google.code.gson:gson:2.8.5")
    implementation("net.sf.trove4j:trove4j:3.0.3")

    implementation("com.dropbox.core:dropbox-core-sdk:3.0.8")
    implementation("com.squareup.okhttp3:okhttp:3.10.0")
    implementation("com.squareup.okio:okio:1.14.0")
    implementation("org.greenrobot:eventbus:3.1.1")
    implementation("com.wdullaer:materialdatetimepicker:4.2.3")
    implementation("commons-io:commons-io:2.5")
    implementation("com.mtramin:rxfingerprint:2.2.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.2")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.0")
    implementation("org.reactivestreams:reactive-streams:1.0.2")
    implementation("com.mlsdev.rximagepicker:library:2.1.5")
    implementation("com.github.bumptech.glide:glide:4.10.0")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    testImplementation("junit:junit:4.12")
    testImplementation("androidx.test:core:1.2.0")
    testImplementation("org.robolectric:robolectric:4.2.1")
}

kapt {
    keepJavacAnnotationProcessors = true
    showProcessorStats = true
    useBuildCache = true
    correctErrorTypes = true
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
//        vendor = JvmVendorSpec.ADOPTIUM
    }
}
kotlin {
    jvmToolchain(8)
//    (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(8))
}