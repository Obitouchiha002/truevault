package com.truevault.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Everything a `:feature:*` module needs: Android library + Compose + Hilt + navigation, plus the
 * core modules every screen legitimately depends on (design system, models, shared utilities).
 *
 * Features deliberately do NOT get `:core:database`, `:core:crypto` or `:core:storage` here — those
 * are reached through repositories, never touched from a Composable.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("truevault.android.library")
            apply("truevault.android.library.compose")
            apply("truevault.android.hilt")
            apply(libs.pluginId("kotlin-serialization"))
        }

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))

            add("implementation", libs.library("androidx-compose-material3"))
            add("implementation", libs.library("androidx-navigation-compose"))
            add("implementation", libs.library("androidx-lifecycle-runtime-ktx"))
            add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-hilt-navigation-compose"))
            add("implementation", libs.library("kotlinx-serialization-json"))

            add("testImplementation", project(":core:testing"))
            add("androidTestImplementation", project(":core:testing"))
            add("androidTestImplementation", libs.library("androidx-test-junit"))
        }
    }
}
