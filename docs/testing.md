# Testing

## Running

```bash
./gradlew testDebugUnitTest            # JVM unit tests — no device needed
./gradlew :app:lintDebug               # lint, configured to fail on error
./gradlew checkAll                     # assembleDebug + all unit tests + lint

./gradlew connectedDebugAndroidTest    # instrumented tests — needs a device or emulator
./gradlew bundleRelease                # release bundle, before shipping
```

## What is covered where

### Unit tests (JVM, no device)

| Area | File | What it pins down |
|------|------|-------------------|
| AES-GCM primitives | `AesGcmTest` | Round trip, distinct ciphertext per call, wrong key, flipped bit in ciphertext and nonce, associated-data mismatch, truncated blob, empty payload, `toString` leaking nothing |
| Container format | `VaultFileCipherTest` | Single/multi/exact-chunk and zero-byte round trips, wrong key, tampered byte, altered declared length, swapped chunks, dropped final chunk, mid-chunk truncation, bad magic, newer version, unknown algorithm, absurd chunk size, progress monotonicity, cancellation, shrinking source, cross-file splicing |
| Key derivation | `PasswordKeyDerivationTest` | Determinism, salt sensitivity, avalanche, key length, unicode and emoji passwords, short salt rejection, parameter versioning, buffer wiping |
| Vault lock | `VaultKeyManagerTest` | Create, unlock, wrong password, case-sensitivity, tampered and truncated records, unsupported KDF version, double-create, biometric enrol/unlock/invalidate/disable, password change, auto-lock, monotonic expiry, wall-clock tampering, screen-off |
| Recovery key | `RecoveryKeyTest` | Length and grouping, uniqueness, confusable characters excluded, lenient normalisation, wrong-length rejection, check value stability and non-disclosure |
| Privacy score | `PrivacyScoreTest` | Every deduction, per-category caps, floor at zero, determinism, breakdown counts |
| File classification | `MimeCategoryTest` | MIME first, extension fallback for `application/octet-stream`, missing MIME, unknown types, unicode names, unknown size |
| Formatting | `ByteFormatTest` | Units, decimal places, multi-gigabyte values |
| Authentication view models | `CreateVaultLockViewModelTest`, `UnlockViewModelTest` | Submission gating, mismatch, biometric opt-in and decline, unsupported devices, password never in UI state, failure counting, no data destruction after repeated failures, biometric invalidation fallback |

### Instrumented tests (device or emulator)

| Area | File | Why it cannot be a unit test |
|------|------|------------------------------|
| Room migrations | `TrueVaultDatabaseMigrationTest` | `MigrationTestHelper` needs a real SQLite instance and the exported schema assets |
| Real Keystore | `VaultLockingTest` | The Android Keystore does not exist on the JVM; the unit tests use an in-memory stand-in, this runs the same guarantees against the real one |
| First-run flow | `OnboardingFlowTest` | Compose UI, navigation, and the guarantee that Home is unreachable without creating a lock |

### Fakes

`:core:testing` holds the shared stand-ins:

- `FakeHardwareKeyStore` — in-memory Keystore with distinct device and biometric keys and a
  controllable invalidation flag.
- `FakeVaultLockStore` — in-memory lock record, with helpers to corrupt and truncate the stored blob
  the way a damaged file or an attacker would.
- `FakeTimeProvider` — wall-clock and monotonic time that move independently, so a test can simulate
  a user changing the device date without the monotonic timer moving.
- `MainDispatcherRule` — replaces `Dispatchers.Main` for view-model tests. Production code never
  references a dispatcher statically; this exists only because `viewModelScope` is hard-wired.

## Deliberate gaps

Being explicit about what is *not* covered is more useful than a coverage percentage.

- **The import engine has no end-to-end JVM test.** It needs `ContentResolver`, the Keystore and real
  file I/O at once. Its individual pieces — container format, key management, storage estimation,
  filename sanitisation — are each covered, and the engine's ordering guarantees are enforced by
  structure rather than by a test. An instrumented end-to-end import test is the highest-value thing
  to add next.
- **The scanner has no test with a real document tree**, for the same reason.
- **Backup export/restore is not covered end to end.** The format's rules — manifest validation,
  version refusal, per-entry hashing, path-traversal refusal — are implemented in one place and are
  the right target for the next round of tests.

## Build gates

Every phase of this project ran, and passed:

```bash
./gradlew clean
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew :app:lintDebug
```

Lint is configured with `abortOnError = true` and promotes nine security-relevant checks
(`UnsafeIntentLaunch`, exported components, world-readable files, `SetJavaScriptEnabled`,
`TrustAllX509TrustManager`, `UnsafeDynamicallyLoadedCode`) to errors. The one suppression in the
project is `NewerVersionAvailable`, documented in `lint.xml`, because the Kotlin compiler plugins
must match the version bundled inside AGP 9.
