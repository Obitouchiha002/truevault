import java.util.Properties

plugins {
    alias(libs.plugins.truevault.android.library)
    alias(libs.plugins.truevault.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The admin backend is opt-in at build time.
 *
 * With no `supabase.properties` at the repository root, both fields below are empty, [RemoteConfig]
 * reports the feature disabled, no check-in ever runs, and `app/src/main/AndroidManifest.xml` keeps
 * `INTERNET` removed — so the shipped app is byte-for-byte the offline one it has always been.
 *
 * That is deliberately the default. Enabling this changes what the app collects, and the claims on
 * the website, in the Play Data Safety form and in the privacy policy all have to be rewritten
 * before such a build goes out. See `docs/ADMIN.md`.
 */
val supabase: Properties? = rootProject.file("supabase.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

android {
    namespace = "com.truevault.core.remote"

    buildFeatures {
        buildConfig = true
    }

    // `tools:node` is resolved before manifest placeholders are substituted, so the permission
    // cannot be switched with a placeholder. Swapping the whole manifest is the mechanism that
    // actually works, and it keeps the two states readable as two files.
    sourceSets {
        getByName("main") {
            manifest.srcFile(
                if (supabase != null) "src/online/AndroidManifest.xml" else "src/main/AndroidManifest.xml",
            )
        }
    }

    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"${supabase?.getProperty("supabaseUrl").orEmpty()}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabase?.getProperty("supabaseAnonKey").orEmpty()}\"")
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    // No HTTP library. One POST to a PostgREST RPC endpoint does not justify adding Retrofit or
    // Ktor to an app whose whole pitch is that it is 4 MB and depends on almost nothing.
}
