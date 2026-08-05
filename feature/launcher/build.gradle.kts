plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.launcher"
}

dependencies {
    implementation(projects.core.capabilities)
    implementation(projects.core.datastore)
    implementation(projects.core.crypto)
    // The hidden-icon list lives in the vault preferences file, so DataStore types are used here.
    implementation(libs.androidx.datastore.preferences)
}
