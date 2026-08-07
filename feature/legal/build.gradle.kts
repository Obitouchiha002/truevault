plugins {
    alias(libs.plugins.truevault.android.feature)
}

android {
    namespace = "com.truevault.feature.legal"
}

dependencies {
    implementation(projects.core.legal)
    implementation(projects.core.datastore)
    implementation(projects.core.storage)
    implementation(projects.core.capabilities)
    // The delete-everything flow clears both databases by name. The file-system reset is
    // deliberately scoped to the vault's own folders and never walks `databases/`.
    implementation(projects.core.database)
    implementation(projects.core.notes)
}
