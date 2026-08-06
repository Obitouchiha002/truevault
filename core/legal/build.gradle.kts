plugins {
    alias(libs.plugins.truevault.android.library)
    alias(libs.plugins.truevault.android.hilt)
}

android {
    namespace = "com.truevault.core.legal"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
}
