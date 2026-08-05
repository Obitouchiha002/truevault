package com.truevault.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = BuildConstants.COMPILE_SDK
            compileSdkMinor = BuildConstants.COMPILE_SDK_MINOR

            defaultConfig {
                applicationId = BuildConstants.APPLICATION_ID
                minSdk = BuildConstants.MIN_SDK
                targetSdk = BuildConstants.TARGET_SDK
                testInstrumentationRunner = BuildConstants.TEST_INSTRUMENTATION_RUNNER
                vectorDrawables.useSupportLibrary = true
            }

            configureCompileOptions(compileOptions)
            configureLint(lint)
            configurePackaging(packaging)
            configureTestOptions(testOptions)

            buildFeatures {
                buildConfig = true
            }
        }

        configureKotlin()
        relaxEmptyTestTasks()

        dependencies {
            add("implementation", libs.library("kotlinx-coroutines-android"))

            add("testImplementation", libs.library("junit4"))
            add("testImplementation", libs.library("truth"))
            add("testImplementation", libs.library("turbine"))
            add("testImplementation", libs.library("mockk"))
            add("testImplementation", libs.library("kotlinx-coroutines-test"))

            add("androidTestImplementation", libs.library("androidx-test-junit"))
            add("androidTestImplementation", libs.library("androidx-test-core"))
            add("androidTestImplementation", libs.library("androidx-test-runner"))
            add("androidTestImplementation", libs.library("androidx-test-rules"))
        }
    }
}
