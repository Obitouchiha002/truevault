plugins {
    alias(libs.plugins.truevault.android.library)
    alias(libs.plugins.truevault.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.truevault.core.datastore"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    // The lock record's shape is defined by the crypto module; this module only persists it.
    api(projects.core.crypto)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
}
