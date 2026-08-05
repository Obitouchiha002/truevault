// Root build file. No plugin is applied here — every module opts in through a convention plugin
// from `:build-logic:convention`. Declaring them `apply false` puts them on the build classpath
// with a single, catalog-controlled version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

/**
 * Convenience aggregate used by the phase build gates:
 *   ./gradlew checkAll
 */
tasks.register("checkAll") {
    group = "verification"
    description = "Assembles the debug app and runs every module's unit tests and lint checks."
    dependsOn(
        ":app:assembleDebug",
        subprojects.map { "${it.path}:testDebugUnitTest" },
        ":app:lintDebug",
    )
}
