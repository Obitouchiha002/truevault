plugins {
    alias(libs.plugins.truevault.android.library)
    alias(libs.plugins.truevault.android.hilt)
}

android {
    namespace = "com.truevault.core.crypto"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.bouncycastle.provider)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
