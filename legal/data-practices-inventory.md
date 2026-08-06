# Data-practices inventory

**This document is a factual audit of what the application does, not a description of what it is
meant to do.** It was produced by reading the source, the manifests and the build files, and every
row names the evidence. The Privacy Policy and Terms are generated *from* this file; if the two ever
disagree, this file is wrong or the code changed, and both must be re-checked before release.

- Audit date: 2026-08-06
- Branch audited: `qa/autonomous-release-verification`
- Method: source reading + `grep` over all non-`build/` sources, all `AndroidManifest.xml`, all
  `build.gradle.kts`, and `gradle/libs.versions.toml`
- **Not** verified by: runtime network capture, decompilation of the release artefact, or a
  third-party SDK behavioural analysis. Those are recorded as gaps at the end.

## Summary of the two claims that matter

| Claim | Supported by this audit? |
|---|---|
| No user data is transmitted off the device by TrueVault | **Yes, for the current code.** No network library is in the dependency graph, `INTERNET` is not declared, and no HTTP/socket call exists in the sources. |
| No third-party SDK collects anything | **Yes, for the current code.** No analytics, crash-reporting, advertising or cloud SDK is present. |

Both claims are scoped to what was audited. They say nothing about the OS itself, about apps the
user shares a file with, or about a future version. The policy states them with that scope attached,
not as an absolute.

---

## Inventory

### 1. Android permissions

| | |
|---|---|
| Accessed | `android.permission.USE_BIOMETRIC` — the only declared permission |
| Collected | No |
| Purpose | Required by `BiometricPrompt`. Normal permission: no runtime dialog, no access to biometric data. |
| Processing location | On device |
| Storage | Not stored |
| Encryption | N/A |
| Retention | N/A |
| Leaves device | No |
| Third parties | None |
| User deletion | N/A |
| Optional | Biometric unlock itself is optional; the permission is inert if unused |

**Not requested:** any storage permission, `MANAGE_EXTERNAL_STORAGE`, `INTERNET`, `CAMERA`,
`RECORD_AUDIO`, location, contacts, `QUERY_ALL_PACKAGES`, `POST_NOTIFICATIONS`.
Evidence: `app/src/main/AndroidManifest.xml` is the only manifest in the project.

### 2. Photo Picker / Storage Access Framework selections

| | |
|---|---|
| Accessed | Yes — only URIs the user explicitly picks |
| Collected | No — TrueVault is the storage; nothing is sent anywhere |
| Purpose | To read the chosen file so it can be encrypted into the vault |
| Processing location | On device |
| Storage | The **content** becomes an encrypted container in app-private internal storage |
| Encryption | AES-GCM, chunked, per-file key |
| Retention | Until the user deletes the vault item |
| Leaves device | No |
| Third parties | None |
| User deletion | Delete the item, or Settings → Delete Vault Data |
| Optional | Entirely — nothing is imported without the user picking it |

### 3. MediaStore access

| | |
|---|---|
| Accessed | Only to request deletion of an original after Secure Move (`createDeleteRequest`) |
| Collected | No |
| Purpose | Removing the original the user asked to move |
| Leaves device | No |
| Notes | The delete is performed by Android after a system confirmation dialog. TrueVault records only the reported outcome. |

Evidence: `DocumentDeleteCapability`, `SecureImportEngine.recordDeletionOutcome`.

### 4. File metadata (name, size, MIME, timestamps)

| | |
|---|---|
| Accessed | Yes |
| Collected | Stored locally only |
| Purpose | Display name, category, duplicate detection, storage estimates |
| Storage | Room database, **sealed per row** (`encryptedMetadata`) |
| Encryption | Sealed with the vault key; the database column is ciphertext |
| Retention | Until item deletion |
| Leaves device | No |
| Notes | File names are never used to derive on-disk names — containers are `<random-uuid>.vault`. |

### 5. Content hashes / fingerprints

| | |
|---|---|
| Accessed | SHA-256 computed over selected file content |
| Purpose | Duplicate detection by the Privacy Scanner |
| Storage | A keyed *fingerprint* of the hash, not the raw hash |
| Leaves device | No |

### 6. Encrypted vault storage

| | |
|---|---|
| Location | App-private internal storage (`filesDir`), not external, not shared |
| Format | `TVLT` v1 container; header is AAD for every chunk; chunk index + isLast in AAD |
| Encryption | AES-GCM with a per-file key, wrapped by the vault master key |
| Retention | Until the user deletes it |
| Leaves device | Only if the user exports an encrypted backup and moves it themselves |
| Survives uninstall | **No.** Uninstalling Android removes app-private storage. Only a separately exported backup survives. |

### 7. Temporary files

| | |
|---|---|
| Created | Yes — decrypted plaintext in `cacheDir/<plaintext cache>` while a file is being viewed or shared; `<uuid>.vault.part` during import |
| Purpose | Viewing, sharing, and crash-safe import |
| Retention | Cleared on `recoverInterruptedImports()` at startup; `.part` files are discarded on failure |
| Leaves device | No |
| Gap | Whether the cache is empty in every exit path has **not** been verified on a device. Manual checklist item M3. |

Evidence: `VaultFileSystem.plaintextCacheDir`, `clearPlaintextCache()`.

### 8. Backup and restore

| | |
|---|---|
| Type | **Local encrypted export only.** No cloud backup, no provider integration. |
| Contents | Containers verbatim (already ciphertext) + file keys re-wrapped through a per-archive key |
| Where it goes | Wherever the user saves it. From that point it is outside TrueVault's control. |
| Android auto-backup | Disabled — `allowBackup="false"`, `fullBackupContent="false"`, `dataExtractionRules` declared |
| Leaves device | Only if the user moves the exported file |

### 9. File sharing / export

| | |
|---|---|
| Mechanism | `FileProvider`, not exported, `grantUriPermissions`, scoped to one cache subdirectory (`file_paths.xml`) |
| What is shared | A temporarily decrypted copy of one file, to an app the user picks |
| Leaves device | **Possibly — this is the user's choice.** Once handed to another app, TrueVault has no control over it. The policy must say so plainly. |

Evidence: `SecureShareCoordinator`.

### 10. Private Apps capability detection / installed-app information

| | |
|---|---|
| Accessed | `LauncherApps.getActivityList` — **launchable activities only** |
| Collected | No. Nothing is stored and nothing is transmitted. |
| `QUERY_ALL_PACKAGES` | **Not requested.** Non-launchable packages are never enumerated. |
| Purpose | Showing private-profile app entries in Secure Launcher Mode |
| Leaves device | No |

Evidence: `LauncherAppsRepository`, `PrivateSpaceCapabilityProvider`, `Android15PrivateAppsController`.

### 11. Biometric data

| | |
|---|---|
| Accessed | **No biometric data ever reaches the app.** Android performs the verification and returns a result. |
| Purpose | Unlocking a Keystore-held key |
| Storage | None — the app stores only a key handle |
| Leaves device | No |

### 12. Password, PIN and recovery key

| | |
|---|---|
| Collected | Never stored in any form that can be reversed |
| Processing | Argon2id derivation, in memory; `CharArray` buffers are wiped after use |
| Storage | Only the *sealed* vault key blobs; the password itself is not stored |
| Recovery key | Shown once, stored only as a sealed key copy. Losing it and the password means the vault is unrecoverable — and the app says so before the lock is created. |
| Leaves device | No |

### 13. Diagnostics, crash reporting, analytics, advertising

| | |
|---|---|
| Present | **None.** |
| Evidence | No Firebase, Crashlytics, Sentry, GA, AppsFlyer, AdMob, or Play Services dependency in any `build.gradle.kts` or in the version catalog. |
| Advertising ID | Not accessed |
| Consequence | The optional-consent screen described in §90 must **not** be shown, because there is nothing to consent to. |

### 14. Logs

| | |
|---|---|
| Written | Only through `SecureLog`, and only when explicitly enabled — which the application does for debuggable builds only |
| Contents | Short fixed technical strings. `describeSafely()` emits a category and a size, never a name. Error logs deliberately drop the throwable's message because provider exceptions embed file names. |
| Leaves device | No |

### 15. Notifications

| | |
|---|---|
| Present | **None.** No `NotificationManager` or `NotificationCompat` usage anywhere in the sources; `POST_NOTIFICATIONS` is not declared. |
| Consequence | Policy sections about notification content are not applicable to the current build. When §115 lands, this row must be rewritten before release. |

### 16. Network

| | |
|---|---|
| Present | **None.** `INTERNET` is not declared; no Retrofit/OkHttp/Ktor/Volley dependency exists; no `URL`, `HttpURLConnection` or socket usage in the sources. |

### 17. Third-party SDKs

Every runtime dependency is a library, not a service. None of them phones home:

AndroidX (core, activity, lifecycle, compose, navigation, datastore, room, paging, biometric),
Dagger/Hilt, kotlinx (coroutines, serialization, datetime), Coil (local images only),
Media3 (local playback only), BouncyCastle (Argon2id, pure JVM — no native library ships).

`androidx.work` is declared in the catalog but referenced by **no module** and does not reach the
APK. See [../qa-reports/03-dependency-inventory.md](../qa-reports/03-dependency-inventory.md).

### 18. Data retention

| Data | Retained until |
|---|---|
| Vault containers | User deletes the item, or resets the app |
| Sealed metadata rows | Same |
| Scan findings | User resolves them, or resets |
| Import transaction rows | Terminal state; cleaned by recovery |
| Temporary plaintext | Cleared at startup and after use |
| Acceptance record (once §91 lands) | Until app reset |

No server-side retention exists, because no server exists.

### 19. Deletion behaviour

| Action | Effect |
|---|---|
| Delete a vault item | Container, thumbnail and row removed |
| Delete all vault data | Containers, thumbnails, database, preferences and wrapped keys removed |
| Uninstall | Android removes all app-private storage |
| **Not** affected by any of the above | Originals the user kept outside the vault, files already shared with other apps, and any backup the user exported |

---

## Gaps in this audit

| Gap | Consequence |
|---|---|
| Release artefact not decompiled | The merged manifest and R8-processed code were not inspected. A dependency could in principle add a permission via manifest merge. **Should be checked before publishing.** |
| No runtime network capture | The "no data leaves the device" claim rests on static evidence only. A proxy-based check on a device would make it observational. |
| No third-party SDK behavioural analysis | Not applicable today (no such SDKs), but must be repeated if one is ever added. |
| Cache-emptiness not verified on device | Manual checklist M3 covers it; it has not been run. |

## Rule for keeping this file true

Any change that adds a permission, a dependency, a notification, a network call, or a new place data
is written **must** update this file in the same commit as the code. The legal release gate
(`qa-reports/13-legal-and-privacy-report.md`) exists to catch the case where it does not.
