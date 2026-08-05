plugins {
    alias(libs.plugins.truevault.android.library)
    alias(libs.plugins.truevault.android.hilt)
}

android {
    namespace = "com.truevault.core.testing"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    // The crypto fakes stand in for the Android Keystore, which does not exist on the JVM.
    api(projects.core.crypto)

    api(libs.junit4)
    api(libs.truth)
    api(libs.turbine)
    api(libs.mockk)
    api(libs.kotlinx.coroutines.test)
    api(libs.hilt.android.testing)
    api(libs.androidx.test.core)
    api(libs.androidx.test.runner)
    api(libs.androidx.test.rules)
}
