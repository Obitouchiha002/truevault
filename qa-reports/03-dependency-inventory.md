# 03 — Dependency inventory

Every third-party artefact TrueVault ships or builds with, taken from `gradle/libs.versions.toml`
and the module `build.gradle.kts` files. Nothing here is inferred from memory: the file is the
source, and the resolved-versions check at the bottom names what was and was not executed.

## Build toolchain

| Component | Version | Note |
|---|---|---|
| Gradle | 9.6.1 | Wrapper only. No system Gradle is used. |
| Android Gradle Plugin | 9.3.1 | Requires Gradle ≥ 9.5.0 |
| Kotlin | 2.3.21 | **Bundled inside AGP 9.** `org.jetbrains.kotlin.android` is deliberately not applied. |
| KSP | 2.3.11 | KSP2 unified versioning, pinned to the bundled Kotlin |
| Java toolchain | 21 | `jvmTarget = 17` |
| compileSdk / targetSdk | 37 (minor 1) | `platforms;android-37.1` |
| minSdk | 26 | Android 8.0 |

## Runtime dependencies (shipped in the APK)

| Group | Artifact | Version | Why it is in the app |
|---|---|---|---|
| androidx.core | core-ktx | 1.19.0 | Platform helpers |
| androidx.core | core-splashscreen | 1.2.0 | Cold-start splash |
| androidx.activity | activity-compose | 1.13.0 | Compose host |
| androidx.lifecycle | runtime-ktx / runtime-compose / viewmodel-compose / process | 2.11.0 | MVI state, process lifecycle for auto-lock |
| androidx.compose | compose-bom | 2026.06.01 | Pins every Compose artifact |
| androidx.compose.material3 | material3, adaptive | via BOM | Design system base |
| androidx.navigation | navigation-compose | 2.9.8 | Type-safe routes |
| androidx.datastore | datastore-preferences | 1.2.1 | Settings |
| androidx.room | runtime, ktx, paging | 2.8.4 | Vault index |
| androidx.paging | paging-runtime, paging-compose | 3.5.0 | Large vault lists |
| androidx.biometric | biometric | 1.1.0 | 1.4.x is alpha-only; 1.1.0 is the newest stable |
| com.google.dagger | hilt-android | 2.60.1 | DI |
| androidx.hilt | hilt-navigation-compose | 1.4.0 | ViewModel scoping |
| org.jetbrains.kotlinx | coroutines-android/core | 1.11.0 | Async |
| org.jetbrains.kotlinx | serialization-json | 1.11.0 | Sealed metadata payloads |
| org.jetbrains.kotlinx | datetime | 0.7.1 | Timestamps |
| io.coil-kt.coil3 | coil-compose | 3.5.0 | Decrypted thumbnails |
| androidx.media3 | exoplayer, ui | 1.10.1 | In-vault playback |
| org.bouncycastle | bcprov-jdk18on | 1.85 | **Argon2id.** Pure JVM — no NDK, no native `.so` in the APK. |

### Declared but unused

`androidx.work:work-runtime-ktx` (2.11.2) is in the version catalog and is referenced by **no
module's** `build.gradle.kts`. It therefore does not reach the APK. It was catalogued for background
integrity checks that were never implemented. Either the feature lands or the entry should go —
leaving it invites someone to assume periodic verification is running when nothing schedules it.

Verified by: `grep -rn "androidx.work" --include=build.gradle.kts` → no matches outside the catalog,
and no `WorkManager`/`CoroutineWorker` reference anywhere in the Kotlin sources.

### What is deliberately absent

No analytics SDK, no crash reporter, no advertising library, no network client
(Retrofit/OkHttp/Ktor) — the app has no server. This is checked again in
[10-security-review.md](10-security-review.md) against the merged manifest and the release mapping.

## Test-only dependencies (never shipped)

| Artifact | Version |
|---|---|
| junit | 4.13.2 |
| com.google.truth | 1.4.5 |
| app.cash.turbine | 1.2.1 |
| io.mockk / mockk-android | 1.14.11 |
| org.robolectric | 4.16.1 |
| androidx.test.ext:junit | 1.3.0 |
| androidx.test:core / runner / rules | 1.7.0 |
| androidx.test.espresso:espresso-core | 3.7.0 |
| androidx.room:room-testing | 2.8.4 |
| androidx.navigation:navigation-testing | 2.9.8 |
| androidx.compose.ui:ui-test-junit4, ui-test-manifest | via BOM |

## Coverage tooling

| Artifact | Version |
|---|---|
| org.jetbrains.kotlinx:kover-gradle-plugin | 0.9.9 |

Applied at the root and aggregated across every subproject. Results:
[06-coverage-report.md](06-coverage-report.md).

## Version policy

Stable releases only. The one documented exception is `androidx.biometric`, held at 1.1.0 because
1.4.x has no stable release; the reason is recorded inline in the catalog rather than in a commit
message someone would have to go looking for.

## Not executed

| Check | Status |
|---|---|
| `./gradlew dependencyUpdates` (Ben Manes plugin) | **NOT RUN — TOOL NOT PRESENT.** The plugin is not in the build. Newer versions may exist for any artifact above; this report states what is pinned, not that each pin is the latest. |
| CVE / vulnerability scan (OWASP dependency-check, Snyk, GitHub Dependabot alerts) | **NOT RUN — ENVIRONMENT UNAVAILABLE.** No scanner is installed and there is no CI integration in this environment. No claim is made that these versions are free of known vulnerabilities. |
| License audit | **NOT RUN.** All artifacts above are Apache-2.0, MIT or BSD by their publishers' own documentation, but no tool verified the POMs in this environment. |
