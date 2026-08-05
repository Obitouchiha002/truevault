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
- **Android Keystore contract violations cannot be caught on the JVM.** The JVM crypto provider
  accepts a caller-supplied GCM nonce; Android Keystore keys created with
  `setRandomizedEncryptionRequired(true)` reject one. A real bug of exactly this shape made every
  vault operation fail on device while all 114 JVM tests passed. `VaultLockingTest` exercises
  `createLock` against the real Keystore and does catch it — but only when run on a device or
  emulator, which is why `connectedDebugAndroidTest` is a release gate and not optional.
- **Backup export/restore is not covered end to end.** The format's rules — manifest validation,
  version refusal, per-entry hashing, path-traversal refusal — are implemented in one place and are
  the right target for the next round of tests.

## Version testing matrices

Both modes share one binary, so the matrix is about *devices*, not build variants.

### Android 15+ (Modern)

| Scenario | What must hold |
|----------|----------------|
| Private Space not configured | Guided setup offered; warnings shown before the settings intent |
| Private Space configured and unlocked | Ready state; installation guidance shown |
| Private Space configured and **locked** | No app names, no icons, no counts, no search results for it |
| Private Space restricted by policy | `DEVICE_POLICY_BLOCKED`; no setup button offered |
| Managed device | Same, and the work-profile banner appears |
| TrueVault **not** the default launcher | Private listing unavailable; Secure Launcher Mode offered from Advanced Privacy only |
| TrueVault **is** the default launcher | Listing and launching work; profile badges are distinct |
| Home role removed while running | Detected on resume; listing stops without a crash |
| Private profile removed while running | State updates without a restart |
| App installed in both profiles | Removal dialog reports a verified private copy |
| App installed only in the private profile | Main copy is absent; nothing to remove |
| Android 16+ device or emulator | No regression; capability detection still runs |
| 16 KB memory-page device | Native libraries load; no page-size crash |

Also verify: locked private apps vanish from the UI, private labels never leak, profile changes need
no restart, main-app uninstall uses the system confirmation, no screen claims app data was
transferred, the file vault keeps working when Private Apps fails, and the background/notification
warnings appear before setup.

### Android 8–14 (Core)

| Axis | Values |
|------|--------|
| API level | 26, 27, 28, 29, 30, 31, 32, 33, 34 |
| Vendor | Pixel, Samsung, and at least one of OnePlus / Oppo / Vivo / Xiaomi |
| Work profile | Present and absent |
| Document provider | Normal, and one that refuses deletion |
| Biometrics | None, weak only, strong enrolled |
| Keystore | Hardware-backed and software-only |

Also verify: Private Space is **never** shown as supported, the full file vault remains available, no
API 35 class-loading crash occurs anywhere, an OEM settings screen that is missing does not crash the
app, work-profile URIs never mix with personal ones, Secure Copy and Move work on each API level, the
deletion result is accurate, cancelling preserves the original, and backups remain cross-version
compatible.

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
