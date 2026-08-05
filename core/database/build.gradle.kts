plugins {
    alias(libs.plugins.truevault.android.library)
    alias(libs.plugins.truevault.android.hilt)
    alias(libs.plugins.truevault.android.room)
}

android {
    namespace = "com.truevault.core.database"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.room.paging)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
}
