import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    id("kotlin-parcelize")
}

android {
    namespace = "ru.orangesoftware.financisto.transaction"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }

        screenshotTests.create("screenshotTest") {
            engineVersion = libs.versions.screenshot.testing.get()
            imageDifferenceThreshold = 0.005f
            targetVariants.add("debug")

            dependencies {
                implementation(compose.ui.tooling)
                implementation(libs.screenshot.validation.api)
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.file("config/detekt.yml"))
    source.setFrom(
        "src/main/java",
        "src/main/kotlin",
        "src/test/java",
        "src/test/kotlin",
        "src/screenshotTest/kotlin",
    )
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
    implementation(project(":core:designsystem"))

    val composeBom = platform(compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(compose.material3)
    implementation(compose.extended.icons)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.navigationevent.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.savedstate)

    val koinBom = platform(libs.koin.bom)
    implementation(koinBom)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.glide.compose)
    implementation(libs.timber)

    testImplementation(libs.test.junit)
    testImplementation(libs.test.mockito.core)
    testImplementation(libs.test.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)

    detektPlugins(libs.detekt.formatting)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

kotlin {
    jvmToolchain(21)
}

abstract class LibraryScreenshotRJarArgumentProvider : CommandLineArgumentProvider {
    @get:Classpath
    abstract val classpathEntries: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> = listOf(
        "-DPreviewScreenshotTestEngineInput.Renderer.mainAllClassPath=${classpathEntries.asPath}",
    )
}

tasks.withType<Test>().configureEach {
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    if (name.contains("ScreenshotTest", ignoreCase = true)) {
        maxHeapSize = "2g"
        systemProperty("user.timezone", "UTC")
        systemProperty("user.language", "en")
        systemProperty("user.country", "US")
        jvmArgumentProviders.add(
            objects.newInstance<LibraryScreenshotRJarArgumentProvider>().apply {
                classpathEntries.from(
                    layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
                    layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
                    layout.buildDirectory.file(
                        "intermediates/compile_and_runtime_r_class_jar/debugScreenshotTest/processDebugScreenshotTestResources/R.jar",
                    ),
                )
            },
        )
    }
}
