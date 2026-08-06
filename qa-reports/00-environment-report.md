# 00 — Environment report

Recorded before any modification.

## Git state at start of verification

| | |
|---|---|
| Branch at start | `main` |
| Commit under test | `cad5eb10c44fef903bf984e3925593d4c4621ef1` |
| Working tree | Clean — `git status --porcelain` returned no output |
| Verification branch created | `qa/autonomous-release-verification` |

No uncommitted work existed, so nothing was at risk of being overwritten. All QA changes are made
on the verification branch.

## Host

| | |
|---|---|
| OS | macOS 26.5.2 (build 25F84) |
| Architecture | x86_64 |
| Java | OpenJDK 21.0.10 (Android Studio bundled JBR) |
| `JAVA_HOME` | `/Applications/Android Studio.app/Contents/jbr/Contents/Home` — valid |
| `ANDROID_HOME` | **Not set in the shell environment.** Exported per command as `~/Library/Android/sdk`, which is valid |
| Gradle | Wrapper, 9.6.1 — no system Gradle is used |
| adb | 1.0.41 / 37.0.0-14910828 |

## Android SDK

| Component | Installed |
|---|---|
| Platforms | android-30, android-34, android-36, android-36.1, android-37.0, android-37.1 |
| Build tools | 34.0.0, 35.0.0, 36.1.0, 37.0.0 |
| System images | `android-30/default/x86_64`, `android-34/google_apis/x86_64` |
| Emulators (AVDs) | `sg` (API 30, Pixel 5, x86_64), `sg34` (API 34, google_apis, Pixel 5, x86_64) |
| Hardware acceleration | **Available** — Hypervisor.Framework, `emulator -accel-check` returned 0 |

## Physical devices

```
$ adb devices -l
List of devices attached
(none)
```

**No physical device is connected.**

## Consequences for the test plan

| Capability | Status |
|---|---|
| Debug / release / bundle builds | Available |
| JVM unit tests | Available |
| Lint and static analysis | Available |
| Instrumented tests, API 34 | Available via AVD `sg34` |
| Instrumented tests, API 30 | Available via AVD `sg` |
| Instrumented tests, API 26 / 27 | **NOT RUN — ENVIRONMENT UNAVAILABLE.** No system image installed for these levels |
| Instrumented tests, API 35+ (Modern mode) | **NOT RUN — ENVIRONMENT UNAVAILABLE.** No API 35+ system image installed |
| Physical-device testing | **NOT RUN — ENVIRONMENT UNAVAILABLE.** No device attached |
| Private Space tests | **NOT RUN — ENVIRONMENT UNAVAILABLE.** Requires API 35+ |
| Firebase Test Lab | **NOT RUN — CREDENTIALS OR QUOTA UNAVAILABLE** |
| Play pre-launch report | **NOT RUN — PLAY CONSOLE ACCESS UNAVAILABLE** |
| Macrobenchmark on real hardware | **NOT RUN — ENVIRONMENT UNAVAILABLE.** Benchmarks on an emulator are not device-representative and are not reported as such |

## Signing material

No keystore, `.jks`, `.p12` or `keystore.properties` file is present or committed. `.gitignore`
excludes `*.keystore`, `*.jks` and `keystore.properties`. The release build has **no signing config
attached**, so `assembleRelease` produces an unsigned artifact by design — a release must be signed
explicitly.

## Test-data policy

No personal files are used. All test data is synthetic and generated into a temporary directory
under the project's build output, then removed. No access is made to the tester's Gallery,
Downloads or Documents.
