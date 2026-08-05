package com.truevault.buildlogic

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room wiring.
 *
 * `room.schemaLocation` is set through KSP arguments rather than the separate Room Gradle plugin so
 * the schema JSONs are checked into the repository. Those schemas are what the migration tests read,
 * so they are a required build output, not a convenience.
 *
 * They land in `src/androidTest/assets/schemas` because that is where `MigrationTestHelper` looks,
 * and because AGP 9's Kotlin DSL no longer allows re-typing the androidTest source set to add an
 * extra assets directory.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("ksp"))

        // Exported into the androidTest assets tree so MigrationTestHelper can read them at runtime
        // without any source-set surgery, and so they are checked in next to the tests that use them.
        val schemaDir = layout.projectDirectory.dir("src/androidTest/assets/schemas")

        extensions.configure<KspExtension> {
            arg("room.schemaLocation", schemaDir.asFile.absolutePath)
            arg("room.generateKotlin", "true")
        }

        dependencies {
            add("implementation", libs.library("androidx-room-runtime"))
            add("implementation", libs.library("androidx-room-ktx"))
            add("ksp", libs.library("androidx-room-compiler"))
            add("testImplementation", libs.library("androidx-room-testing"))
        }
    }
}
