# TrueVault — final verification report

**Branch:** `qa/autonomous-release-verification`
**Date:** 2026-08-06
**Verdict:** **CONDITIONAL — not ready for public release**

Every gate that could run, ran and passed. The reason the verdict is not "ready" has nothing to do
with a failing test: it is that several things which must be tested before a privacy app ships were
**never executed**, and this report names each one rather than rounding them into a pass.

---

## 1. What was executed, and what it returned

| Gate | Command | Result |
|---|---|---|
| Clean | `clean` | **PASS** |
| Debug build | `assembleDebug` | **PASS** |
| Release build | `assembleRelease` | **PASS** |
| Release bundle | `bundleRelease` | **PASS** |
| Unit tests | `testDebugUnitTest` — 147 tests | **PASS**, 0 failures |
| Lint (debug) | `:app:lintDebug` | **PASS**, 0 issues |
| Lint (release) | `:app:lintRelease` | **PASS**, 0 issues |
| Coverage | `koverXmlReport koverHtmlReport` | **PASS** — 9.6 % line |
| Instrumented | `connectedDebugAndroidTest` on API 34 emulator — 7 tests | **PASS**, 0 failures |

Artefacts: `app-release-unsigned.apk` 4.9 MiB, `app-release.aab` 9.0 MiB. R8 runs and succeeds.

## 2. Defects found and fixed during this pass

Nine, all real. Not one would have been caught by static analysis.

| # | Severity | Defect |
|---|---|---|
| 1 | **Critical** | `AesGcm.encrypt` supplied its own IV. Keystore keys created with `setRandomizedEncryptionRequired(true)` reject that, so **vault creation failed on every real device** while every JVM unit test passed. |
| 2 | **Critical** | Backup format v1 wrapped file keys with the source vault's master key. Restoring into any new vault — a new phone, a reinstall, the exact scenario a backup exists for — produced permanently unopenable items. Fixed by format v2 re-keying + schema v2. |
| 3 | **High** | Backup read whole containers into memory. First large video would have thrown `OutOfMemoryError`. Now 64 KiB streaming. |
| 4 | **High** | `SecureLauncherActivity` shipped enabled, so Android asked **every** user to pick a home app on every Home press. Now disabled at install and toggled only by the feature. |
| 5 | **High** | Biometric enrolment failed silently — StrongBox requested on the auth-bound key, plus bare-null failure returns. |
| 6 | **Medium** | `UnlockViewModel` ran `while (true) { delay(1s) }`, which hung the unit suite under virtual time and woke once a second on every unlock screen with no lockout to count. |
| 7 | **Medium** | Three permissions merged in from libraries: `ACCESS_NETWORK_STATE` and `WAKE_LOCK` from media3. An app claiming nothing leaves the device cannot ship with "network" in its permission list. Removed. |
| 8 | **Medium** | Instrumented suite depended on which test ran first, via persistent DataStore state. Fixed with the Orchestrator + `clearPackageData`, not with retries. |
| 9 | **Low** | Modules with no instrumented tests built and installed empty test APKs that crashed on launch, failing the whole build. |

A tenth is worth recording because it was mine: the documentation claimed a throttled 4-digit PIN
took "years" to brute-force. The test failed, the real figure turned out to be **≈ 104 days average**,
and both the doc and the test were corrected. The test caught its author overstating security, which
is the only reason to write that kind of test.

## 3. Release blockers

### B1 — Android Keystore is effectively untested

`core.crypto.keystore` sits at **1 % coverage**. It is the package where defect #1 lived: invisible
to unit tests, fatal on every device. There are no instrumented tests for key creation, biometric
key invalidation, or StrongBox.

### B2 — Secure Copy / Secure Move has no end-to-end test

`SecureImportEngine` is at **2 % coverage**. The ordering it enforces — encrypt, fsync, verify,
atomically rename, commit, *only then* ask about the original — is the single most consequential
property in the app, and it is currently guaranteed by review rather than by a test. Two pieces were
extracted so they could be covered (`DeletionOutcome.resolve()`, `candidateSources()`); the engine
itself was not.

### B3 — Modern mode has never executed

No API 35+ system image is installed. Everything behind `TrueVaultProductMode.MODERN` — Private
Space detection, private-app listing, guided setup — has run zero times. The *branch* is unit-tested
across API 26–40; what happens on the other side of it is unknown.

### B4 — minSdk has never executed

No API 26/27 image installed. The oldest supported configuration is untested.

### B5 — No physical device

StrongBox, biometric enrolment and invalidation, real MediaStore delete dialogs, OEM skins: none of
it ran. Emulator biometrics do not model enrolment invalidation faithfully.

### B6 — Release APK never installed

R8 builds successfully; nobody checked that the shrunk app works. R8 breaking reflection,
serialization or Room is a classic release-only failure.

### B7 — Legal and privacy layer incomplete

Documents, inventory and the Data Safety map exist; the in-app acceptance flow is partially built
and `legal-config.json` still contains unresolved placeholders. See
[13-legal-and-privacy-report.md](qa-reports/13-legal-and-privacy-report.md).

## 4. NOT RUN — environment unavailable

Recorded so no reader mistakes absence for success.

| Item | Reason |
|---|---|
| Physical device testing | No device attached |
| API 35+ / Private Space | No system image installed |
| API 26–33 | No system images installed |
| Firebase Test Lab | No `gcloud`, no project, no credentials, no billing |
| Play Console / Data Safety submission | No access |
| Release signing | No keystore |
| CVE / dependency scanning | No scanner installed |
| MobSF / semgrep / detekt | Not installed or not configured |
| Runtime network capture | Not performed — the "nothing leaves the device" claim rests on static evidence |
| Macrobenchmark, cold start, jank, throughput, battery | Nothing measured; `qa-reports/benchmarks/` is empty because nothing ran |
| Independent cryptographic review | Not performed |

## 5. Coverage, stated plainly

9.6 % line overall. The security core is genuinely covered — KDF 97 %, container format 87 %, AEAD
82 %, model 81 %, key manager 62 %. The DAOs, all Compose UI, storage, capability providers and the
import engine are at or near zero.

No `koverVerify` threshold is enforced. Setting one at today's number would enforce nothing; setting
one the project does not meet would break every build. The number is published instead.

## 6. What must happen before a public release

1. Instrumented tests for `core.crypto.keystore` — highest value per test in the project.
2. An end-to-end Secure Move test, including a killed process mid-import.
3. One API 35+ device or image; one API 26–28 device or image.
4. One physical device with real biometric hardware.
5. Install and exercise the **release** APK — [MANUAL_TEST_CHECKLIST.md](qa-reports/MANUAL_TEST_CHECKLIST.md) M1.
6. Backup → uninstall → reinstall → restore, on two devices.
7. Resolve every legal placeholder and complete a human legal review.
8. A runtime network capture, so "no data leaves the device" is observed rather than inferred.

## 7. Reports

| | |
|---|---|
| [00 Environment](qa-reports/00-environment-report.md) | Host, SDK, devices |
| [01 Project audit](qa-reports/01-project-audit.md) | Structure and conventions |
| [02 Build](qa-reports/02-build-report.md) | Gates, artefacts, merged-manifest finding |
| [03 Dependencies](qa-reports/03-dependency-inventory.md) | Every artefact, and what is absent |
| [04 Static analysis](qa-reports/04-static-analysis-report.md) | Lint, R8, what was not run |
| [05 Unit tests](qa-reports/05-unit-test-report.md) | 147 tests, per suite |
| [06 Coverage](qa-reports/06-coverage-report.md) | 9.6 %, and which 9.6 % |
| [07 Instrumented](qa-reports/07-instrumented-test-report.md) | 7 tests, four defects found |
| [08 Device matrix](qa-reports/08-device-matrix.md) | One emulator; everything else NOT RUN |
| [09 Recovery](qa-reports/09-recovery-and-data-integrity.md) | Keys, backup v2, migration |
| [10 Security](qa-reports/10-security-review.md) | Design, real numbers, stated limits |
| [11 Performance](qa-reports/11-performance-notes.md) | Nothing measured on a device |
| [12 Test Lab](qa-reports/12-firebase-test-lab.md) | NOT RUN, and what it would cover |
| [Manual checklist](qa-reports/MANUAL_TEST_CHECKLIST.md) | Everything automation could not reach |

## 8. Statement

Every result in this report and its appendices was produced by a command that actually ran. No test
result was simulated, no command output was invented, and nothing skipped is described as passed.
Where a check could not run, the reason is stated and the item is marked **NOT RUN**.
