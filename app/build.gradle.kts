plugins {
    alias(libs.plugins.truevault.android.application)
    alias(libs.plugins.truevault.android.application.compose)
    alias(libs.plugins.truevault.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.truevault.app"

    defaultConfig {
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            // Shrinking is enabled from Phase 6 onward, after the keep rules below are verified
            // against Room, Hilt and kotlinx.serialization on a real device build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // No debug signing config is attached: a release build must be signed explicitly.
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.datastore)

    implementation(projects.feature.onboarding)
    implementation(projects.feature.authentication)
    implementation(projects.feature.home)
    implementation(projects.feature.vault)
    implementation(projects.feature.importfiles)
    implementation(projects.feature.scanner)
    implementation(projects.feature.privateapps)
    implementation(projects.feature.settings)
    implementation(projects.feature.backup)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.navigation.testing)
}
