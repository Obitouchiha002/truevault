# 10 — Security review

A review of what TrueVault actually does, written to be checkable rather than reassuring. Where a
claim is only supported by reading the code, it says so.

**TrueVault is not unbreakable, and this document does not claim it is.** It is an app that encrypts
files on a device, on a platform whose own security model it cannot exceed. Everything below is
bounded by that.

## 1. Permissions and attack surface

Read from `app/src/main/AndroidManifest.xml`:

| Item | Value |
|---|---|
| Declared permissions | `android.permission.USE_BIOMETRIC` only |
| Storage permissions | **None.** Files arrive as user-chosen URIs via the Photo Picker and SAF. |
| `MANAGE_EXTERNAL_STORAGE` | Never requested |
| `INTERNET` | **Not declared.** The app has no server and no network code. |
| `allowBackup` | `false` |
| `fullBackupContent` | `false` |
| `dataExtractionRules` | Declared, `@xml/data_extraction_rules` |
| Exported components | `MainActivity` (launcher) and `SecureLauncherActivity` (Home, **disabled at install**) |
| FileProvider | Not exported, `grantUriPermissions`, scoped to one cache subdirectory in `file_paths.xml` |

`USE_BIOMETRIC` is a normal permission: no runtime dialog, no access to biometric data.

`SecureLauncherActivity` ships `android:enabled="false"`. This was a real defect found during this
pass: declaring `category.HOME` enabled makes Android ask *every* user to pick a home app on every
Home press, whether or not they ever wanted Secure Launcher Mode. The component is now toggled by
`SecureLauncherComponent` only when the user turns that mode on.

**Verification status:** read from source. A `dumpsys`-level check of the *merged* manifest in the
release APK is **NOT RUN** — see gaps.

## 2. Key hierarchy

```
password/PIN ──Argon2id(salt)──► password key ──seals──► vault master key
                                                              │
Android Keystore device key ──seals──────────────────────────►│  (double sealed)
                                                              │
biometric-bound Keystore key ──seals──► second copy ──────────┤
recovery key ────────────────seals──► third copy  ────────────┘  (deliberately NOT device-bound)
```

The double sealing is the design's core: the stored blob is useless without the password *and*
useless off the device. The recovery key is the one deliberate exception, because otherwise a broken
phone means permanent loss.

The device key requests StrongBox where available (`setIsStrongBoxBacked(true)`), but **only for the
non-auth-bound key**. The auth-bound biometric key does not, because requesting both on the same key
made biometric enrolment fail silently on real devices — a defect found and fixed during this pass.

### A Keystore bug this pass found and fixed

`AesGcm.encrypt` originally generated its own IV. Keystore keys created with
`setRandomizedEncryptionRequired(true)` reject a caller-supplied IV with
`InvalidAlgorithmParameterException`, so **vault creation failed on every real device** while every
JVM unit test passed — the JVM provider accepts either form. The fix takes the IV from the Cipher.

The lesson is recorded in `docs/testing.md`: this class of bug is invisible to unit tests and only
`connectedDebugAndroidTest` can catch it.

## 3. Container format

`TVLT` v1: `magic ‖ formatVersion ‖ algorithm ‖ flags ‖ chunkSize ‖ plaintextSize ‖ wrappedKeyLen ‖
wrappedKey ‖ metadataLen ‖ metadata`, then chunks of `nonce ‖ ciphertext ‖ tag`.

- The **header is the AAD for every chunk**, so header tampering breaks every chunk's tag.
- Each chunk's AAD includes its **index** and an **isLast** flag, which is what makes reordering,
  truncation and splicing detectable rather than merely unlikely.
- Chunked streaming means a 4 GB video never needs 4 GB of memory.

**Executed:** `VaultFileCipherTest` (23 tests), `AesGcmTest` (11), `SyntheticCorpusRoundTripTest`
(7) — all passing. Decryption under the wrong key throws and writes **zero bytes**.

## 4. Brute-force resistance — with the real numbers

`LockThrottle`: 4 free attempts, then 30 s → 60 s → 5 min → 15 min → 30 min (capped).

| Lock | Keyspace | Average time to exhaust half, throttled |
|---|---|---|
| 4-digit PIN | 10⁴ | **≈ 104 days** (208 days worst case) |
| 6-digit PIN | 10⁶ | **≈ 28 years** |
| Passphrase | depends on the phrase | Argon2id dominates |

These figures were **computed and unit-tested** (`LockThrottleTest`), not estimated. They matter
because an earlier draft of the documentation claimed a throttled 4-digit PIN took "years"; the test
failed, the real number turned out to be months, and the documentation was corrected. A 4-digit PIN
is offered because users want it, and the choose-lock screen states its weakness in plain words
rather than burying it.

The throttle is clock-rollback safe: it stores a monotonic-anchored deadline, so setting the system
clock backwards does not clear a wait.

## 5. What TrueVault deliberately does not do

Every one of these was available and was rejected:

| Technique | Why not |
|---|---|
| APK cloning / virtualization | Running another app's code inside this process breaks the platform's own isolation and violates Play policy |
| Silent install / uninstall | Requires privileges no ordinary app should hold |
| Accessibility Service | Reading other apps' screens to "hide" things is surveillance wearing a privacy costume |
| Root / Shizuku / device-owner | Turns a security app into a privilege-escalation vector |
| Hidden or reflective platform APIs | Breaks unpredictably and evades platform review |

The Private Apps feature is built entirely on the **documented** Private Space / launcher APIs, and
degrades to guidance text where they are unavailable.

## 6. Data at rest and in transit

| Item | Storage |
|---|---|
| Containers | App-private internal storage, `<uuid>.vault` — names derived from randomness, never from the file's real name |
| Thumbnails | Sealed with the item's file key, `<uuid>.thumb` |
| Vault index (Room) | Metadata sealed per row; the original URI is sealed and retained **only** for Secure Move, only until deletion resolves |
| Scan findings | Matched URIs sealed — they name a file and a folder on the user's device |
| Logs | `SecureLog`; `describeSafely()` never emits a file name |
| Network | None. No network dependency is in the build. |

## 7. Gaps — not run, not verified

| Check | Status |
|---|---|
| Static security analysis (MobSF, semgrep, detekt security rules) | **NOT RUN — TOOL NOT INSTALLED** |
| Dependency CVE scan | **NOT RUN — TOOL NOT INSTALLED.** See [03-dependency-inventory.md](03-dependency-inventory.md). |
| Release APK / AAB decompilation review | **NOT RUN.** R8 mapping and merged manifest have not been inspected in the built artefact. |
| StrongBox behaviour on real secure hardware | **NOT RUN — ENVIRONMENT UNAVAILABLE.** The emulator has no StrongBox, so that branch has never executed. |
| Biometric key invalidation on enrolment change | **NOT RUN — ENVIRONMENT UNAVAILABLE** |
| Independent cryptographic review | **NOT PERFORMED.** The design is documented and unit-tested; it has not been reviewed by anyone other than its author. |
| Screenshot / FLAG_SECURE coverage audit | **NOT RUN** as a systematic per-screen check |

## 8. Honest summary

What is supported by executed evidence: the container format authenticates correctly and fails
closed; the key hierarchy behaves as designed under unit test; the throttle numbers are real; the
app requests no storage or network permissions; backups restore into a different vault.

What is not: anything about physical devices, secure hardware, Private Space, or OEM builds. Those
are recorded as **NOT RUN**, and the release verdict in
[FINAL_TEST_REPORT.md](../FINAL_TEST_REPORT.md) is conditional because of them.
