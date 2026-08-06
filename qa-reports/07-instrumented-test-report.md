# 07 — Instrumented test report

**Executed** on emulator `sg34` (AVD, API 34, google_apis, x86_64).

```
./gradlew :core:database:connectedDebugAndroidTest :app:connectedDebugAndroidTest --max-workers=1
BUILD SUCCESSFUL
```

## Result

| Module | Tests | Passed | Failed |
|---|---|---|---|
| `:core:database` | 3 | **3** | 0 |
| `:app` | 4 | **4** | 0 |
| **Total** | **7** | **7** | **0** |

| Suite | What it proves |
|---|---|
| `TrueVaultDatabaseMigrationTest` | Schema 1 → 2 migration adds `wrapped_file_key` and preserves rows |
| `OnboardingFlowTest` | Onboarding can be skipped; lock creation cannot; the unrecoverable warning appears **before** a lock method is chosen |
| `VaultLockingTest` | Lock and unlock behaviour on a real device |

## Four defects this run found

Every one of these was invisible to the unit suite.

### 1. Room schemas exported to the wrong directory

`MigrationTestHelper` resolves schemas from the assets root; the convention plugin wrote them to
`src/androidTest/assets/schemas/`. The migration test could not run at all — it failed with
"Missing file: …/1.json". Fixed in `AndroidRoomConventionPlugin`; the three migration tests now pass.

### 2. Hilt never generated its androidTest components

All four app tests failed with "missing generated file … `_TestComponentDataSupplier`", because
`kspAndroidTest` was never configured. Fixed in `AndroidHiltConventionPlugin`.

### 3. Empty test APKs crashed on launch

Every library module inherited `testInstrumentationRunner` but modules with no `src/androidTest`
never pulled the runner in, so `connectedAndroidTest` built and installed an empty test APK for each
of them, which then crashed with `ClassNotFoundException: AndroidJUnitRunner`. One green suite
reported as a failed build — the worst kind of false alarm, because it teaches people to ignore the
result. Fixed: modules without instrumented tests no longer build one.

### 4. The suite depended on which test ran first

This is the interesting one, and it cost the most time.

The first test to press "Skip" recorded `onboardingCompleted = true` in DataStore. Every later test —
in that run **and in every subsequent run** — then started on a different screen than it expected.
The symptom was a suite that passed once and afterwards failed with a *different* error each time:
first "not displayed", then "could not find Skip", then a crashed process. It reads exactly like
flakiness and is really order dependence plus persistent state.

Fixed properly rather than by retrying:

- `testOptions.execution = "ANDROIDX_TEST_ORCHESTRATOR"` — each test in its own process
- `testInstrumentationRunnerArguments["clearPackageData"] = "true"` — app wiped between tests
- `androidTestUtil` orchestrator + test-services in both convention plugins

A separate, genuine timing race also existed: `waitForIdle` returned before the navigation
transition settled. `OnboardingFlowTest` now waits for the node to appear and **still asserts it is
displayed**. Weakening it to `assertExists` would have made the test pass while proving nothing
about whether the user can see the warning.

One assertion was also simply wrong: it pinned the app-bar title "Create your vault lock", but the
first stage of that screen asks "How do you want to lock your vault?". The test now asserts the
question, which is the thing that must not disappear.

## What this run does **not** cover

| Area | Status |
|---|---|
| Android Keystore behaviour | **Almost entirely untested.** `core.crypto.keystore` sits at 1 % coverage, and it is where the bug that broke vault creation on every real device lived. |
| `SecureImportEngine` (Secure Copy / Secure Move) | **No instrumented test.** The ordering that guarantees no original is deleted before its encrypted copy is verified is asserted by review only. |
| Backup export / restore end to end | **NOT RUN** |
| Private Space / Modern mode | **NOT RUN — no API 35+ system image installed** |
| API 26–33 | **NOT RUN — no system images installed** |
| Physical device, StrongBox, biometric enrolment | **NOT RUN — ENVIRONMENT UNAVAILABLE** |

7 passing instrumented tests is a floor, not a pass mark. See
[08-device-matrix.md](08-device-matrix.md) and [FINAL_TEST_REPORT.md](../FINAL_TEST_REPORT.md).
