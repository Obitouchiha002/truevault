# 01 — Project audit

Recorded **before** any fix was applied, at commit `cad5eb1`.

## Detected architecture

21 Gradle modules, single application id `com.truevault.app`, one Git repository.

```
:app
:core:model  :core:common  :core:capabilities  :core:data  :core:designsystem
:core:database  :core:datastore  :core:crypto  :core:storage  :core:testing
:feature:onboarding  :feature:authentication  :feature:home  :feature:launcher
:feature:vault  :feature:importfiles  :feature:scanner  :feature:privateapps
:feature:settings  :feature:backup
```

Feature modules depend on `:core:data`, never directly on `:core:crypto`, `:core:storage` or
`:core:database` — verified by reading every feature `build.gradle.kts`. The one intentional
exception is `:feature:launcher`, which uses `:core:crypto` solely to observe `VaultSession` lock
state for gating Launcher Visibility editing.

## Toolchain

| | Value | Assessment |
|---|---|---|
| AGP | 9.3.1 | Current stable |
| Gradle | 9.6.1 (wrapper) | Wrapper is used; no system Gradle dependency |
| Kotlin | 2.3.21 | Must equal AGP's bundled Kotlin — verified it does |
| KSP | 2.3.11 | |
| compileSdk / targetSdk | 37 / 37 | Not lowered to dodge restrictions |
| minSdk | 26 | Matches the stated support floor |
| Java toolchain / jvmTarget | 21 / 17 | |
| Product flavors | None | Documented decision in `docs/version-modes.md` |
| Signing config | None on release | Deliberate — release must be signed explicitly |
| R8 / resource shrinking | Enabled on release | Keep rules present for Room, Hilt, kotlinx.serialization, BouncyCastle |

**Dependency stability:** grep for `alpha|beta|rc|SNAPSHOT|+` across the version catalog returned
**no matches**. No dynamic versions.

## Manifest

Permissions declared: **`USE_BIOMETRIC` only.** Plus `uses-feature android.hardware.fingerprint`
with `required="false"`.

Not present anywhere: `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `READ_EXTERNAL_STORAGE`,
`READ_MEDIA_*`, `POST_NOTIFICATIONS`, accessibility service, device administrator.

Exported components: `MainActivity` (LAUNCHER) and `SecureLauncherActivity` (HOME). Both are
intentional entry points. `FileProvider` is `exported="false"` with `grantUriPermissions="true"`,
scoped by `file_paths.xml` to the single cache subdirectory `tv_plaintext/`.

`allowBackup="false"`, `fullBackupContent="false"`, and `data_extraction_rules.xml` excludes every
domain for both cloud backup and device transfer.

## Findings

Severity uses the report's own scale. Every finding below was verified by reading the code, not
inferred.

### QA-01 — HIGH — Backup export loads each whole vault container into memory

`core/data/.../BackupRepository.kt`, export path:

```kotlin
entries += zip.writeSealed(
    name = "${BackupManifest.ITEM_ENTRY_PREFIX}${item.id}",
    plaintext = file.readBytes(),      // <-- entire container
    key = archiveKey,
)
```

`writeSealed` then calls `AesGcm.encrypt`, which allocates a second array of the same size.

**Impact:** backing up a vault containing a large video allocates roughly 2× the file size on the
heap. A 500 MB item is already beyond a typical Android heap; a multi-gigabyte item is a certain
`OutOfMemoryError`. The import path is carefully streamed in 1 MiB chunks — backup silently
discards that guarantee, so a user whose vault works perfectly cannot back it up.

**Class of defect:** data-loss adjacent. The backup is the only protection against uninstall, and it
is the operation most likely to fail on exactly the vaults that need it most.

### QA-02 — HIGH — Backup restore loads each whole sealed entry into memory

Same file, `unpackAndVerify`:

```kotlin
val sealedBytes = zip.readBytes()                       // entire sealed entry
val digest = MessageDigest.getInstance("SHA-256").digest(sealedBytes)
val plaintext = AesGcm.decrypt(key, SealedData.fromByteArray(sealedBytes), AAD_BACKUP)
```

Three full-size allocations live simultaneously: the sealed bytes, the `SealedData` copy, and the
plaintext.

**Impact:** an archive that exported successfully on a high-memory device cannot be restored on a
smaller one. Restore is the recovery path; failing there is worse than failing at export.

### QA-03 — MEDIUM — Text viewer reads an entire file to show a 20 000-character preview

`feature/vault/.../VaultItemViewerViewModel.kt`:

```kotlin
val bytes = file.readBytes()
val text = String(bytes.copyOf(minOf(bytes.size, TEXT_PREVIEW_LIMIT)))
```

**Impact:** opening a large `text/*` item — a log file, an exported database dump — allocates the
whole file plus a String copy, to display the first 20 000 characters. Crash on open, for an item
that is stored perfectly well.

### QA-04 — MEDIUM — Broad `catch (e: Exception)` in coroutine bodies swallows cancellation

55 occurrences of `catch (e: Exception)` exist. In Kotlin coroutines, `CancellationException` is an
`Exception`, so a broad catch inside a suspend function silently converts "the user cancelled" into
"the operation failed", and can leave a coroutine appearing to complete after its scope was
cancelled.

`SecureImportEngine.importOne` handles this correctly — it catches `CancellationException` first and
rethrows. Other suspend/flow bodies (`BackupRepository.export`, `BackupRepository.restore`,
`PrivacyScanEngine`) do not.

**Impact:** cancelling a backup or a scan can report a failure instead of a cancellation, and the
enclosing scope may not tear down promptly. No data loss, but the status shown is not the truth,
which this product treats as a defect in itself.

### QA-05 — INFORMATIONAL — `ANDROID_HOME` is not set in the environment

Not an application defect. Recorded because the verification script must not assume it.

## Verified as NOT defective

Checked specifically, found correct:

- **`SecureLog.e` is guarded.** `if (!loggingEnabled) return` precedes the `Log.e` call; release
  builds emit nothing. Throwable messages are never logged, only the exception class name.
- **No `GlobalScope`, no `runBlocking`, no `Thread.sleep`** in production code.
- **All `Cipher.init(ENCRYPT_MODE, …)` call sites omit a caller IV**, which is what the Keystore
  requires. (This was a real device-breaking defect fixed in `cad5eb1`, immediately before this
  audit; re-verified here.)
- **Every API 35 call site is SDK-guarded and `@RequiresApi`-annotated**, and
  `Android15PrivateAppsController` is constructed only when `SDK_INT >= 35`.
- **No advertising or analytics SDK** anywhere in the dependency graph.

## Missing tests identified

To be added before the final unit-test run:

- One-byte and small-file container round trips
- Wrapped-key round trip and rejection of a corrupted wrapped key
- Empty-password rejection at the domain level
- KDF salt uniqueness across generations
- Recovery-key rejection of corrupted recovery data
- Import-transaction state-machine transitions
- Scanner: same size but different content, and different name but same content
- Privacy Score: explanation matches the deductions exactly
- Capability detection across product modes and every `PrivateSpaceState`
- Backup: wrong password, truncated archive, modified manifest, unsupported version, duplicate id,
  restore into a non-empty vault
- Streaming backup memory behaviour (added with the QA-01/QA-02 fix)

## Likely crash points

1. Backup or restore of a vault containing any large item (QA-01, QA-02) — **most likely crash in
   the product today**.
2. Opening a large text item (QA-03).
3. `PdfRenderer` on a password-protected or malformed PDF — already handled: the exception is caught
   and reported as "cannot preview".
4. `MediaMetadataRetriever` on a corrupt video — already handled, returns no thumbnail.

## Version-compatibility risks

- API 26–28 cannot delete MediaStore originals without all-files access. Handled: reported as
  `PROVIDER_NOT_SUPPORTED` rather than requesting the permission.
- `getScaledFrameAtTime` is API 27+. Guarded with a full-frame downscale fallback on API 26.
- `ContextCompat.registerReceiver` used for the API 33 flags overload.
- No API 35 symbol is reachable on lower levels.
