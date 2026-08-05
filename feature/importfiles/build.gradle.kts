plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.importfiles"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
