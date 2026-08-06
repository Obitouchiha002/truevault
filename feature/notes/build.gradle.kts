plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.notes"
}

dependencies {
    implementation(projects.core.notes)
}
