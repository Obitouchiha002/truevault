package com.truevault.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            buildFeatures {
                compose = true
            }

            // Compose modules do ship Android resources: themes, strings, drawables.
            androidResources {
                enable = true
            }
        }

        configureComposeDependencies()
    }
}
