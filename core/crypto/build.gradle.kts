plugins {
    alias(libs.plugins.truevault.android.library)
    alias(libs.plugins.truevault.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.truevault.core.crypto"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.capabilities)
    implementation(projects.core.common)
    implementation(libs.bouncycastle.provider)
    // The backup manifest is JSON so a future version can still read an older archive.
    api(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
