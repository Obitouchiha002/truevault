import java.util.Properties

plugins {
    alias(libs.plugins.truevault.android.application)
    alias(libs.plugins.truevault.android.application.compose)
    alias(libs.plugins.truevault.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.truevault.app"

    defaultConfig {
        versionCode = 3
        versionName = "0.1.2"
    }

    /**
     * Release signing, read from `keystore.properties` at the project root.
     *
     * That file and the keystore itself are gitignored. Losing either means never
     * being able to publish an update to an app already installed under this key —
     * Android refuses the upgrade, and every existing user would have to uninstall
     * and lose their vault. Back both up.
     *
     * When the file is absent the release build simply stays unsigned rather than
     * falling back to the debug key: a release accidentally signed with a shared
     * debug certificate is worse than one that cannot be installed.
     */
    val signingProps: Properties? = rootProject.file("keystore.properties")
        .takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
                enableV1Signing = false   // v1 is obsolete and minSdk is 26
                enableV2Signing = true
                enableV3Signing = true
            }
        }
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
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(projects.core.remote)
    implementation(projects.feature.admin)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.datastore)
    implementation(projects.core.capabilities)
    implementation(projects.core.crypto)
    implementation(projects.core.data)

    implementation(libs.androidx.biometric)

    implementation(projects.feature.onboarding)
    implementation(projects.feature.authentication)
    implementation(projects.feature.home)
    implementation(projects.feature.launcher)
    implementation(projects.feature.vault)
    implementation(projects.feature.importfiles)
    implementation(projects.feature.scanner)
    implementation(projects.feature.privateapps)
    implementation(projects.core.legal)
    implementation(projects.feature.legal)
    implementation(projects.feature.notes)
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
