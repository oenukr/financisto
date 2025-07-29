// At the top of your app/build.gradle.kts
import com.android.build.api.artifact.MultipleArtifact

// Define a custom task to handle the complex 'CLASSES' artifact.
abstract class CopyClassesTask @Inject constructor() : DefaultTask() {

    // THIS IS THE KEY: ScopedArtifact.CLASSES produces both jars and directories.
    // We must declare two separate inputs to receive them.
    @get:InputFiles
    @get:Optional
    abstract val allJars: RegularFileProperty

    @get:InputFiles
    @get:Optional
    abstract val allDirectories: RegularFileProperty

    // This is the single output directory where everything will be copied.
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun taskAction() {
        // Use standard Gradle file operations to copy the contents.
        project.copy {
            // First, copy the contents of all the loose-class directories.
            if (allDirectories.isPresent) {
                from(allDirectories)
            }
            // Then, copy the contents of all the JAR files.
            // project.zipTree() opens a JAR/ZIP without extracting it to disk first.
            if (allJars.isPresent) {
                from(allJars.map { project.zipTree(it) })
            }
            // Set the destination.
            into(outputDir)
            // Exclude files that are not .class files (like module-info.class or metadata)
            exclude("META-INF/**")
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kover)
    alias(libs.plugins.room.gradle.plugin)
    alias(libs.plugins.ksp)
}

// The androidComponents block that uses our custom task.
//androidComponents {
//    onVariants { variant ->
//        // Register an instance of our custom task for each variant.
//        val copyTaskProvider = tasks.register(
//            "copy${variant.name.capitalize()}ClassesToAssets",
//            CopyClassesTask::class.java
//        )
//
//        // THIS IS THE CORRECT WIRING:
//        // Use our task to transform the CLASSES artifact.
//        variant.artifacts.use(copyTaskProvider)
//            // Wire the two different kinds of outputs from the artifact
//            // to the two different input properties on our task.
//            .wiredWithFiles(
//                CopyClassesTask::allJars,
//                CopyClassesTask::allDirectories
//            )
//            // Specify that we want to transform the ScopedArtifact.CLASSES.
//            .toTransform(ScopedArtifact.CLASSES)
//
//        // Finally, add the output of our task to the assets directory
//        // so it gets packaged into the APK.
//        variant.sources.assets?.addGeneratedSourceDirectory(
//            copyTaskProvider,
//            CopyClassesTask::outputDir
//        )
//    }
//}
androidComponents { onVariants { variant -> // This is a simple diagnostic task.
    tasks.register("print${variant.name.capitalize()}Classes") {
        doLast {
            println("======================================================")
            println("DIAGNOSTIC REPORT FOR VARIANT: ${variant.name}")
            println("======================================================")
        }            // Get the Provider for the artifact directly.
    val classesProvider = variant.artifacts.getAll(MultipleArtifact.NATIVE_DEBUG_METADATA)//ScopedArtifact.CLASSES)

    // At execution time (inside doLast), it's safe to check the provider.
    if (classesProvider.isPresent) {
        try {
            val files = classesProvider.get()
            if (files.isEmpty()) {
                println("--> Artifact ScopedArtifact.CLASSES exists, but is an EMPTY list.")
            } else {
                println("--> Found ${files.size} items in ScopedArtifact.CLASSES:")
                files.forEach { file ->
                    println("    - $file")
                }
            }
        } catch (e: Exception) {
            println("--> FAILED to get() value from provider: $e")
        }
    } else {
        println("--> Provider for ScopedArtifact.CLASSES has NO VALUE.")
    }
    println("======================================================\n")
}
}}


// Your existing android { ... } block follows
android {
    namespace = "ru.orangesoftware.financisto"
    compileSdk = 36
    // ... all your other android settings
}

android {
    namespace = "ru.orangesoftware.financisto"
    compileSdk = 36

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
//        resources.pickFirsts.add("ru/orangesoftware/financisto/model/**/*.class")
        resources.pickFirsts.add("/**")
//        resources.pickFirsts.add("ru/orangesoftware/orb/**/*.class")

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

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Compose
    val composeBom = platform(compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Material Design 3
    implementation(compose.material3)

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
    testImplementation(koinBom)
    androidTestImplementation(koinBom)
    // Koin - core
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    // Koin - Java Compatibility
    implementation(libs.koin.android.compat)
    // Koin - Compose
    implementation(libs.koin.compose)
    // Koin - tests
    testImplementation(libs.koin.test)
    androidTestImplementation(libs.koin.test)

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

    // SQLite
    implementation(libs.sqlite)
    implementation(libs.sqlite.bundled)
    implementation(libs.sqlite.framework)
    implementation(libs.sqlite.ktx)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.core)

    // ASM to inspect bytecode - needed for reading annotations
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-commons:9.8")

    // Room
    add("ksp", libs.room.compiler)
    implementation(libs.room.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.rxjava2)
    implementation(libs.room.rxjava3)


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
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.11.1") // can't be used because of the current target sdk
    testImplementation("org.hamcrest:hamcrest:2.2")
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

composeCompiler {
    // StrongSkipping is enabled by default
//    featureFlags = setOf(ComposeFeatureFlag.StrongSkipping.disabled())
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
