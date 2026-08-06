package com.truevault.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlinx.kover")

        extensions.configure<LibraryExtension> {
            compileSdk = BuildConstants.COMPILE_SDK
            compileSdkMinor = BuildConstants.COMPILE_SDK_MINOR

            defaultConfig {
                minSdk = BuildConstants.MIN_SDK
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                consumerProguardFiles("consumer-rules.pro")
            }

            configureCompileOptions(compileOptions)
            configureLint(lint)
            configurePackaging(packaging)
            configureTestOptions(testOptions)

            buildFeatures {
                buildConfig = false
                resValues = false
            }

            // Non-UI core modules ship no resources; the Compose convention re-enables this.
            androidResources {
                enable = false
            }
        }

        configureKotlin()
        relaxEmptyTestTasks()

        // Most core modules have no instrumented tests. Without this, `connectedAndroidTest` still
        // builds and installs an empty test APK for each of them, and that APK crashes on launch
        // with ClassNotFoundException: AndroidJUnitRunner — because a module with no androidTest
        // sources never pulls the runner in. One green suite then reports as a failed build, which
        // is the worst kind of false alarm: it trains people to ignore the result.
        val hasInstrumentedTests = layout.projectDirectory.dir("src/androidTest").asFile.exists()
        if (!hasInstrumentedTests) {
            tasks.matching { it.name.contains("AndroidTest") }.configureEach { enabled = false }
        }

        dependencies {
            add("implementation", libs.library("kotlinx-coroutines-android"))

            if (hasInstrumentedTests) {
                add("androidTestImplementation", libs.library("androidx-test-junit"))
                add("androidTestImplementation", libs.library("androidx-test-core"))
                add("androidTestImplementation", libs.library("androidx-test-runner"))
                add("androidTestImplementation", libs.library("androidx-test-rules"))

                // configureTestOptions selects the Orchestrator for every module, so any module
                // that actually runs instrumented tests has to ship it — otherwise the run reports
                // "No test results" and looks like a failure with no failing test in it.
                add("androidTestUtil", libs.library("androidx-test-orchestrator"))
                add("androidTestUtil", libs.library("androidx-test-services"))
            }

            add("testImplementation", libs.library("junit4"))
            add("testImplementation", libs.library("truth"))
            add("testImplementation", libs.library("turbine"))
            add("testImplementation", libs.library("mockk"))
            add("testImplementation", libs.library("kotlinx-coroutines-test"))
        }
    }
}
