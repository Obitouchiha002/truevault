package com.truevault.buildlogic

import org.gradle.api.JavaVersion

/**
 * Single source of truth for build numbers that every TrueVault module shares.
 *
 * These intentionally live in Kotlin rather than the version catalog: the catalog holds dependency
 * coordinates, while these are compiler/platform settings that the convention plugins apply.
 */
internal object BuildConstants {
    const val COMPILE_SDK = 37
    const val COMPILE_SDK_MINOR = 1
    const val TARGET_SDK = 37
    const val MIN_SDK = 26

    const val JAVA_TOOLCHAIN = 21

    val JAVA_COMPATIBILITY: JavaVersion = JavaVersion.VERSION_17
    const val JVM_TARGET = "17"

    const val APPLICATION_ID = "com.truevault.app"
    const val NAMESPACE_PREFIX = "com.truevault"

    const val TEST_INSTRUMENTATION_RUNNER = "com.truevault.core.testing.TrueVaultTestRunner"
}
