plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.backup"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.crypto)
    implementation(projects.core.datastore)
    implementation(libs.androidx.activity.compose)
}
