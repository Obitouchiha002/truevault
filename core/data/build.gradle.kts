plugins {
    alias(libs.plugins.truevault.android.library)
    alias(libs.plugins.truevault.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.truevault.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.crypto)
    implementation(projects.core.storage)
    implementation(projects.core.datastore)

    // Room types are used directly here: withTransaction and PagingSource.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
