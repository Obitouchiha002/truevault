plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.onboarding"
}

dependencies {
    implementation(projects.core.capabilities)
    implementation(projects.core.datastore)
}
