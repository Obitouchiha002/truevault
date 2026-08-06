# 02 — Build report

All gates below were **executed** by `scripts/run-full-verification.sh --no-device` on
2026-08-06. Log: `qa-reports/tool-versions.txt` plus the gate output in the run transcript.

## Gate results

| Gate | Task | Result |
|---|---|---|
| Clean | `clean` | **PASS** |
| Debug build | `assembleDebug` | **PASS** |
| Release build | `assembleRelease` | **PASS** |
| Release bundle | `bundleRelease` | **PASS** |
| Unit tests | `testDebugUnitTest` | **PASS** |
| Lint (debug) | `:app:lintDebug` | **PASS** |
| Lint (release) | `:app:lintRelease` | **PASS** |
| Coverage | `koverXmlReport koverHtmlReport` | **PASS** |
| Instrumented | `connectedDebugAndroidTest` | Run separately — see [07](07-instrumented-test-report.md) |

`bundleRelease` had failed in an earlier pass. That failure was caused by a source edit made while
the build was running, not by a defect in the project; it has now been re-run from a clean state and
passes. This report records the re-run, not the earlier result.

## Artefacts produced

| Artefact | Size |
|---|---|
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 5 112 154 bytes (≈ 4.9 MiB) |
| `app/build/outputs/bundle/release/app-release.aab` | 9 483 686 bytes (≈ 9.0 MiB) |

The APK is **unsigned**: no release keystore exists in this environment. Signing and any
signature-dependent check are **NOT RUN — CREDENTIALS UNAVAILABLE**.

## R8

`minifyReleaseWithR8` runs and succeeds. The release build therefore exercises shrinking,
obfuscation and resource optimisation.

**Not verified:** that the shrunk app behaves correctly at runtime. R8 breaking reflection,
serialization or Room is a classic release-only failure, and nothing in this pass installed the
release APK on a device. Manual checklist item M1 covers it and has not been run.

## Baseline profile

`assets/dexopt/baseline.prof` and `baseline.profm` **are** present in the release APK, together with
`androidx.profileinstaller`. These come from the profiles the AndroidX libraries publish, merged by
AGP.

There is **no app-specific baseline profile module** in this project, so TrueVault's own hot paths —
unlock, vault list, decryption — are not profiled. Adding a Macrobenchmark module would be the
single largest first-run improvement available. See [11](11-performance-notes.md).

## Toolchain actually used

| | |
|---|---|
| Gradle | 9.6.1 (wrapper) |
| AGP | 9.3.1 |
| Kotlin | 2.3.21 (bundled in AGP; the standalone Kotlin plugin is not applied) |
| KSP | 2.3.11 |
| JDK | 21 (Android Studio JBR), `jvmTarget = 17` |
| compileSdk / targetSdk | 37.1 / 37 |
| minSdk | 26 |

Configuration cache is enabled and reused across runs. Parallel configuration cache is in use and is
reported by Gradle as incubating.

## Merged-manifest finding — fixed during this pass

The release APK's merged manifest contained three permissions the project never declared:

| Permission | Merged from | Action |
|---|---|---|
| `USE_FINGERPRINT` | `androidx.biometric:1.1.0` | **Kept.** Genuinely required below API 28, and minSdk is 26. Disclosed in the Privacy Policy. |
| `ACCESS_NETWORK_STATE` | `androidx.media3:media3-exoplayer:1.10.1` | **Removed** via `tools:node="remove"` |
| `WAKE_LOCK` | `androidx.media3:media3-exoplayer:1.10.1` | **Removed** via `tools:node="remove"` |

media3 declares the last two for adaptive streaming and background playback. TrueVault plays only
local decrypted files and never plays in the background, so it uses neither — and an app whose whole
claim is that nothing leaves the device cannot ship with "network" in its permission list. Anyone
who checked would be right to disbelieve the claim.

This was found by dumping the built artefact (`aapt2 dump permissions`), not by reading source. It
is a good argument for making that dump a standing gate.

## Reproducibility

The verification script takes no absolute paths, discovers `JAVA_HOME` and `ANDROID_HOME` or accepts
them from the environment, and exits non-zero on any failed gate. A Windows counterpart
(`scripts/run-full-verification.ps1`) exists with the same gates and the same exit contract.
