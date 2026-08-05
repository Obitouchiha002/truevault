package com.truevault.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Applies the Compose compiler plugin and the BOM-managed Compose dependencies.
 *
 * The `compose = true` build feature itself is set by the caller, because AGP 9 types that flag on
 * the concrete extension (`LibraryBuildFeatures` / `ApplicationBuildFeatures`).
 */
internal fun Project.configureComposeDependencies() {
    pluginManager.apply(libs.pluginId("kotlin-compose"))

    // Material 3 still marks the top-app-bar and scroll-behaviour APIs experimental. They are the
    // supported way to build these screens, so the opt-in is declared once for every Compose module
    // instead of being repeated as an annotation on each screen.
    extensions.configure(KotlinAndroidProjectExtension::class.java) {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        }
    }

    dependencies {
        val bom = libs.library("androidx-compose-bom")
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))

        add("implementation", libs.library("androidx-compose-ui"))
        add("implementation", libs.library("androidx-compose-ui-graphics"))
        add("implementation", libs.library("androidx-compose-foundation"))
        add("implementation", libs.library("androidx-compose-material3"))
        add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
        add("implementation", libs.library("androidx-lifecycle-runtime-compose"))

        add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
        add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))

        add("androidTestImplementation", libs.library("androidx-compose-ui-test-junit4"))
    }
}

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

internal fun VersionCatalog.pluginId(alias: String) = findPlugin(alias).get().get().pluginId
