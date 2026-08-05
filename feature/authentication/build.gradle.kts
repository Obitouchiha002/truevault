plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.authentication"
}

dependencies {
    implementation(projects.core.crypto)
    implementation(projects.core.datastore)
    implementation(libs.androidx.biometric)
}
