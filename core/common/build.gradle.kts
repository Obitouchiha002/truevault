plugins {
    alias(libs.plugins.truevault.android.library)
    alias(libs.plugins.truevault.android.hilt)
}

android {
    namespace = "com.truevault.core.common"
}

dependencies {
    api(projects.core.model)
}
