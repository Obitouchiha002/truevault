plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.legal"
}

dependencies {
    implementation(projects.core.legal)
    implementation(projects.core.datastore)
    implementation(projects.core.storage)
    implementation(projects.core.capabilities)
}
