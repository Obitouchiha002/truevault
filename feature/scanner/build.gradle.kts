plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.scanner"
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.androidx.activity.compose)
}
