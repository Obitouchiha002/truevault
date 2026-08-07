plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.admin"
}

dependencies {
    implementation(projects.core.remote)
}
