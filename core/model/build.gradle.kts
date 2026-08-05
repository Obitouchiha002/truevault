plugins {
    alias(libs.plugins.truevault.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.truevault.core.model"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
