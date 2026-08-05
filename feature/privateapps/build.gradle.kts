plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.privateapps"
}

dependencies {
    implementation(projects.core.capabilities)
    implementation(projects.core.datastore)
}
