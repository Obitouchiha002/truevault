plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.settings"
}

dependencies {
    implementation(projects.core.datastore)
}
