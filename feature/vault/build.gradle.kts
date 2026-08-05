plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.vault"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.compose)
}
