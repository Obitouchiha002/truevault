package com.truevault.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

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

        dependencies {
            add("implementation", libs.library("kotlinx-coroutines-android"))

            add("testImplementation", libs.library("junit4"))
            add("testImplementation", libs.library("truth"))
            add("testImplementation", libs.library("turbine"))
            add("testImplementation", libs.library("mockk"))
            add("testImplementation", libs.library("kotlinx-coroutines-test"))
        }
    }
}
