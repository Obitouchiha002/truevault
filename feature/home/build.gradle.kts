plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.home"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.datastore)
}
