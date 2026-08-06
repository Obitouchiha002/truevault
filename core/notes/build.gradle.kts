plugins {
    alias(libs.plugins.truevault.android.library)
    alias(libs.plugins.truevault.android.hilt)
    alias(libs.plugins.truevault.android.room)
}

android {
    namespace = "com.truevault.core.notes"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
}
