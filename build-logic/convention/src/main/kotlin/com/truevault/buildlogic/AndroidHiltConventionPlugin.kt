package com.truevault.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply(libs.pluginId("ksp"))
            apply(libs.pluginId("hilt"))
        }

        dependencies {
            add("implementation", libs.library("hilt-android"))
            add("ksp", libs.library("hilt-compiler"))
            // Instrumented tests need their own Hilt components generated. Without this every
            // @HiltAndroidTest fails at runtime with "missing generated file ..._TestComponentDataSupplier".
            // Unit tests deliberately do not get the processor: it registers a test source set in
            // modules that have no unit tests, which makes Gradle fail the run for finding none.
            add("kspAndroidTest", libs.library("hilt-compiler"))
        }
    }
}
