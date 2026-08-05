plugins {
    alias(libs.plugins.truevault.android.library)
    alias(libs.plugins.truevault.android.library.compose)
}

android {
    namespace = "com.truevault.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.material3.adaptive)
    implementation(projects.core.model)
}
