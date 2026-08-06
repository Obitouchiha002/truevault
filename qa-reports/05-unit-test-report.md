# 05 — Unit test report

**Executed.** `./gradlew testDebugUnitTest`. Raw JUnit XML in `qa-reports/junit/`.

## Result

| | |
|---|---|
| Suites | 17 |
| Tests | **147** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |

## Per suite

| Suite | Tests | Time | What it protects |
|---|---|---|---|
| `core.crypto.vault.VaultKeyManagerTest` | 22 | 9.77 s | Password → Argon2id → sealed master key; biometric and recovery paths; throttle |
| `core.crypto.file.VaultFileCipherTest` | 23 | 0.36 s | Container format, chunk AAD, truncation and splice detection |
| `core.crypto.aead.AesGcmTest` | 11 | 0.95 s | AEAD, and the regression for the Keystore-supplied IV |
| `core.crypto.kdf.PasswordKeyDerivationTest` | 9 | 0.76 s | Argon2id parameters and determinism |
| `core.crypto.recovery.RecoveryKeyTest` | 7 | 0.07 s | Recovery-key generation and unwrap |
| `core.crypto.file.SyntheticCorpusRoundTripTest` | 7 | 0.86 s | Zero-byte, unicode names, duplicates, corrupt containers, wrong key |
| `core.data.BackupRekeyingTest` | 4 | 4.58 s | An exported item opens in a **different** vault |
| `core.data.ImportModelsTest` | 8 | 0.05 s | Which outcomes may be offered for deletion; progress arithmetic |
| `core.data.PrivacyScanReportTest` | 7 | 0.06 s | Scanner pre-filter and report counters |
| `core.model.DeletionResolutionTest` | 8 | 1.08 s | The "original deleted" claim, for every outcome |
| `core.model.LockThrottleTest` | 8 | 0.05 s | Lockout schedule and clock-rollback safety |
| `core.model.PasswordStrengthTest` | 12 | 0.22 s | Strength estimation |
| `core.model.PrivacyScoreTest` | 7 | 0.03 s | Score arithmetic |
| `core.model.MimeCategoryTest` | 6 | 0.15 s | MIME and extension classification |
| `core.model.VaultLockTypeTest` | 3 | 0.02 s | PIN lengths |
| `core.capabilities.CapabilityResolutionTest` | 9 | 0.59 s | Product mode for API 26–40; private-apps resolution |
| `core.common.format.ByteFormatTest` | 4 | 0.74 s | Size formatting |

## Added during this pass

| Suite | Why it did not exist before |
|---|---|
| `DeletionResolutionTest` | The mapping lived inside `SecureImportEngine`, which needs Room, a `ContentResolver` and a device. It was extracted to `DeletionOutcome.resolve()` in `:core:model` specifically so the app's most consequential claim could be tested exhaustively — including a sweep over every enum value, so a newly added outcome cannot default into "secured". |
| `PrivacyScanReportTest` | Same reason. `candidateSources()` was extracted so the scanner's pre-filter could be checked, in particular that a file of unknown size is excluded rather than reported. |
| `ImportModelsTest` | Proves failed and cancelled imports are never offered for deletion — the path by which an app could delete a file it did not actually secure. |
| `CapabilityResolutionTest` | Covers API 26–40 for the one place an SDK level decides anything. No device farm here can cover that range; a pure function can. |
| `SyntheticCorpusRoundTripTest` | Edge-case corpus: zero bytes, one byte, unicode and emoji names, no extension, duplicate content, identical size with different content, corrupt containers. |

## A bug this run found

`:feature:authentication:testDebugUnitTest` did not fail — it **hung**. A thread dump of the test
worker showed one coroutine burning 154 seconds of CPU inside `UnlockViewModel`:

```
UnlockViewModel$2.invokeSuspend(UnlockViewModel.kt:65)
  kotlinx.coroutines.DelayKt.delay
```

The unlock screen ran `while (true) { delay(1_000); ... }` to count down the lockout. Under
`runTest`'s virtual clock that loop never yields the scheduler, so the suite never finished. The same
loop also woke once a second for as long as the unlock screen was open, in the overwhelmingly common
case where there is no lockout at all — a wake per second to re-read a zero.

Fixed: the countdown now runs only while there is something to count, and is restarted by the failed
attempt that creates a new delay. The class documentation was also corrected — it still claimed
there was "no lockout timer", which stopped being true when the throttle was added.

Worth recording plainly: the test did not catch a product bug by failing. It caught it by hanging,
and only a thread dump explained why.

## Not covered here

These numbers say nothing about anything that needs a device. Coverage is 9.6 % overall
([06](06-coverage-report.md)), the Android Keystore package is at 1 %, and `SecureImportEngine` — the
Secure Copy / Secure Move state machine — is at 2 %. Both gaps are release blockers in
[FINAL_TEST_REPORT.md](../FINAL_TEST_REPORT.md), not footnotes.
