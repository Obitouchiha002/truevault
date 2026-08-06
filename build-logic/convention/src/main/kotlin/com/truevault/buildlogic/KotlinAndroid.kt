package com.truevault.buildlogic

import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.TestOptions
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Shared Android DSL configuration.
 *
 * AGP 9 no longer exposes these blocks through `CommonExtension` in Kotlin, so each convention
 * plugin resolves them from its own concrete extension (`LibraryExtension` / `ApplicationExtension`)
 * and hands the pieces to the functions below. That keeps one definition of the rules.
 */
internal fun configureCompileOptions(compileOptions: CompileOptions) {
    compileOptions.sourceCompatibility = BuildConstants.JAVA_COMPATIBILITY
    compileOptions.targetCompatibility = BuildConstants.JAVA_COMPATIBILITY
}

internal fun Project.configureLint(lint: Lint) {
    lint.lintConfig = rootProject.file("lint.xml")
    lint.abortOnError = true
    lint.warningsAsErrors = false
    lint.checkDependencies = true
    lint.checkReleaseBuilds = true
    // Security-relevant checks are promoted to errors so they can never be merged as warnings.
    lint.error.addAll(
        listOf(
            "UnsafeIntentLaunch",
            "ExportedContentProvider",
            "ExportedReceiver",
            "ExportedService",
            "WorldReadableFiles",
            "WorldWriteableFiles",
            "SetJavaScriptEnabled",
            "TrustAllX509TrustManager",
            "UnsafeDynamicallyLoadedCode",
        ),
    )
}

internal fun configurePackaging(packaging: Packaging) {
    packaging.resources.excludes.addAll(
        listOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
            "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",

            // BouncyCastle ships lookup tables for its post-quantum "picnic"
            // signature scheme as resources. TrueVault uses exactly one thing from
            // that library — Argon2id — and never touches picnic, but resources are
            // not reachability-shrunk the way code is, so roughly 1.1 MB of dead
            // weight was riding along in every APK. Excluded rather than tolerated:
            // a privacy app asking people to sideload should be as small as it
            // honestly can be.
            "/org/bouncycastle/pqc/**",
            "/org/bouncycastle/x509/**",
        ),
    )
}

internal fun configureTestOptions(testOptions: TestOptions) {
    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.isReturnDefaultValues = false

    // Each instrumented test runs in its own process, which is what lets `clearPackageData` wipe the
    // app between tests. It costs a few seconds per test and buys a suite whose result does not
    // depend on which test happened to run first.
    testOptions.execution = "ANDROIDX_TEST_ORCHESTRATOR"
}

/**
 * Gradle 9 fails a test task that has a test source set but discovers no tests.
 *
 * That is a useful signal in a module that is supposed to have tests, but several core modules are
 * pure declarations (models, DI wiring) with nothing meaningful to unit test, and annotation
 * processors still register a test source set for them. Failing those builds would push us toward
 * writing empty tests to keep the build green, which is worse than having none.
 */
internal fun Project.relaxEmptyTestTasks() {
    tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach {
        failOnNoDiscoveredTests.set(false)
    }
}

/**
 * Kotlin compiler settings.
 *
 * The `kotlin { }` extension here is the one registered by AGP 9's built-in Kotlin support; the
 * standalone `org.jetbrains.kotlin.android` plugin is never applied.
 */
internal fun Project.configureKotlin() {
    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(BuildConstants.JAVA_TOOLCHAIN))
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(BuildConstants.JVM_TARGET))
            allWarningsAsErrors.set(false)
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-Xconsistent-data-class-copy-visibility",
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
}
