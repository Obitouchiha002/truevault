# TrueVault

A local-first Android privacy app.

> **The promise:** secure files independently, remove originals safely, detect remaining copies, and
> show the user an honest privacy status.

TrueVault does not claim to be unhackable, does not claim military-grade anything, and does not
pretend to inspect data Android will not let it read. Where the platform limits what is possible,
the app says so in the place the limitation matters.

---

## Status

All six phases are built. Every gate below was run from a clean tree.

| Phase | Scope | State |
|-------|-------|-------|
| 0 | Build foundation, modules, DI, navigation, design system, error model | **Complete** |
| 1 | Onboarding, vault password, Keystore master key, biometrics, session and auto-lock | **Complete** |
| 2 | Secure file vault: pickers, streaming AES-256-GCM, Room, transaction engine, viewer, original deletion, sharing | **Complete** |
| 3 | Privacy leak scanner and explainable privacy score | **Complete** |
| 4 | Encrypted local backup and restore, recovery key | **Complete** |
| 5 | Private Apps capability detection and guided setup | **Complete** |
| 6 | Hardening, threat model, documentation, release checklist | **Complete** |
| 7 | Version-based product modes: capability engine, Modern/Core experiences, Secure Launcher Mode | **Complete** |

| Gate | Result |
|------|--------|
| `./gradlew :app:assembleDebug` | Passes |
| `./gradlew testDebugUnitTest` | 114 unit tests, 0 failures |
| `./gradlew :app:lintDebug` | Clean — 0 errors, 0 warnings |
| `./gradlew :app:assembleDebugAndroidTest` | Compiles (needs a device to run) |
| `./gradlew :app:bundleRelease` | Passes with minification and resource shrinking |

What is deliberately **not** built is listed in [Known limitations](docs/known-limitations.md) —
cloud backup, perceptual hashing, expiring links, background imports and app cloning are all absent
on purpose, and the app says so where a user would otherwise expect them.

---

## How to build

Requirements:

| Tool | Version | Note |
|------|---------|------|
| JDK | 21 | Android Studio's bundled JBR works |
| Gradle | 9.6.1 | supplied by the wrapper, no separate install |
| Android Gradle Plugin | 9.3.1 | requires Gradle ≥ 9.5.0 |
| Kotlin | 2.3.21 | bundled inside AGP 9; the standalone `kotlin-android` plugin is **not** applied |
| Android SDK Platform | 37.1 | `sdkmanager "platforms;android-37.1"` |
| Build Tools | 37.0.0 | |

```bash
# from the project root
./gradlew assembleDebug          # build the debug APK
./gradlew testDebugUnitTest      # unit tests
./gradlew :app:lintDebug         # lint
./gradlew checkAll               # all three, the phase gate
```

If `JAVA_HOME` is not set:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

`local.properties` holds `sdk.dir` and is not checked in.

## How to run

Open the project in Android Studio and run the `app` configuration, or:

```bash
./gradlew :app:installDebug
adb shell am start -n com.truevault.app.debug/com.truevault.app.MainActivity
```

## Supported Android versions

- **Minimum:** API 26 (Android 8.0)
- **Target / compile:** API 37

## Product modes

One app, one application id, one encryption format. What differs is which capabilities a device
actually offers, decided at runtime:

| Mode | Android | Adds |
|------|---------|------|
| **TrueVault Modern** | 15+ | Private Space guidance, optional Secure Launcher Mode |
| **TrueVault Core** | 8–14 | Nothing missing from the file-security product |

Full detail, including the capability matrix and what is never done on any version, is in
[Product modes](docs/version-modes.md).

## Feature support matrix

| Feature | Android 8–9 | Android 10–13 | Android 14+ |
|---------|-------------|---------------|-------------|
| Encrypted vault (AES-256-GCM, Keystore-wrapped keys) | Yes | Yes | Yes |
| Hardware-backed Keystore | Device dependent | Device dependent | Device dependent |
| Photo Picker | Via support library backport | Yes | Yes |
| Storage Access Framework | Yes | Yes | Yes |
| System delete request for media originals | Manual confirmation | `createDeleteRequest` from API 30 | Yes |
| Biometric unlock | If enrolled | If enrolled | If enrolled |
| Android Private Space guidance | No | No | Android 15+ only |

Rows marked "device dependent" are detected at runtime and reported honestly in the app rather than
assumed.

## How encryption works, at a high level

The key hierarchy below is implemented as of Phase 1; per-file encryption arrives in Phase 2.

```
password ──Argon2id(random salt, versioned params)──▶ password key
                                                          │ seals
                                     vault master key ◀────┘
                                            │
        Android Keystore device key ──seals──┘  → this is what is stored on disk
        (generated in Keystore, non-exportable, AES-256-GCM)

        Android Keystore biometric key ──seals──▶ vault master key   (optional second path,
        (per-use auth, invalidated on new enrolment)                  only if the user opts in)

        vault master key ──wraps──▶ a fresh random key per file  (Phase 2)
```

Two layers, not one, and for different reasons. The password layer means the stored blob is useless
without what the user knows. The device layer means it is useless off this device, because the
Keystore key cannot be exported — so the password cannot be attacked on hardware of the attacker's
choosing. Both must be satisfied to reach the master key.

- Every vault item gets its own random file key and every encryption operation gets its own random
  nonce. A nonce is never reused with a key.
- Only the **wrapped** file key is stored in the database. Plain keys are never persisted.
- Files are streamed in chunks; a multi-gigabyte video is never loaded into memory.
- Decryption is authenticated. A failed authentication tag is treated as corruption or tampering and
  the output is discarded — partially decrypted data is never returned.
- The user's password is never used directly as an encryption key. It goes through a memory-hard KDF
  (Argon2id) with a random salt and versioned parameters.

## How Secure Copy works

1. Read the source through the URI the user granted.
2. Stream it through encryption into the vault.
3. Verify the encrypted container.
4. Record the vault item.

The original file is left exactly where it was. The app says so afterwards, rather than implying the
file is now private everywhere.

## How Secure Move works

Secure Move is a recoverable transaction, in this order:

```
validate source → check free space → create vault item id → write <uuid>.vault.part
→ close streams → verify the encrypted output → commit metadata → atomically rename to <uuid>.vault
→ ask the system to delete the original → record the real deletion result → limited duplicate scan
```

It is never "copy, then delete". Encryption is verified and committed *before* deletion is even
requested, so a crash or a cancellation at any point leaves the original untouched.

## Why original deletion requires confirmation

TrueVault cannot delete a file the user chose through the Photo Picker or the Storage Access
Framework on its own — and it should not be able to. Deleting media goes through the platform's own
delete request, which shows Android's confirmation dialog. The user can decline.

Every outcome is recorded as observed, never assumed:

`DELETED`, `USER_CANCELLED`, `PROVIDER_NOT_SUPPORTED`, `PERMISSION_LOST`, `ALREADY_MISSING`,
`FAILED`.

If deletion does not happen, the vault copy is still safe and the app says:

> Your file is secured in TrueVault, but the original may still be visible.

The app never displays "Original deleted" until the system has confirmed it.

## What the scanner can and cannot inspect

**Can:** files and folders the user has explicitly granted access to, compared by size, MIME type and
SHA-256 content hash.

**Cannot:** other apps' private storage, end-to-end encrypted chats, cloud accounts without an
integration, another device, or storage outside the supported APIs. The platform trash is reported as
`TRASH_STATUS_UNKNOWN` rather than guessed at.

The privacy score is always shown with its breakdown, so a number is never presented without the
reason behind it.

## Private Apps limitations

TrueVault does **not** clone apps, virtualise APKs, install packages silently, use an accessibility
service, or take device-admin rights. On Android versions that offer Private Space it detects the
capability and guides the user into the system's own setup; everywhere else it says "not supported on
this device". The file vault works fully regardless.

## Backup and recovery warning

**TrueVault cannot recover your local vault without your password or recovery key.** There is no
account, no server, and no reset link — which is the point, and also the risk. Generate a recovery
key and keep an encrypted backup.

Android's automatic backup is disabled. Copying ciphertext to the cloud without the Keystore key that
opens it would produce a backup that looks valid and can never be restored.

## Permissions

TrueVault requests **no permissions at install time**. Photos and videos come from the Android Photo
Picker, documents from the Storage Access Framework — both hand back a URI the user chose
deliberately. `MANAGE_EXTERNAL_STORAGE` is never requested. Biometric permission is added in Phase 1,
alongside the screen that needs it.

## Project layout

```
:app                     single activity, navigation host, app shell
:core:model              pure Kotlin models, enums, typed errors
:core:common             dispatchers, Outcome type, safe logging, time
:core:designsystem       theme, tokens, reusable components
:core:database           Room entities, DAOs, migrations          (Phase 2)
:core:datastore          user preferences
:core:crypto             key management, streaming encryption      (Phase 1–2)
:core:storage            pickers, ContentResolver, file streaming  (Phase 2)
:core:testing            shared test fixtures and rules
:feature:*               onboarding, authentication, home, vault, importfiles,
                         scanner, privateapps, settings, backup
:build-logic:convention  Gradle convention plugins
```

## Documentation

- [Architecture](docs/architecture.md) — data flow, module graph, build logic, security boundaries
- [Product modes](docs/version-modes.md) — capability matrix, Private Space handling, Secure Launcher Mode
- [Threat model](docs/threat-model.md) — 20 threats, each with what remains true after mitigation
- [Encrypted file format](docs/encrypted-file-format.md) — container layout, AAD design, parser rules
- [Database schema and migrations](docs/database.md) — what is sealed, what is not, and why
- [Permissions](docs/permissions.md) — the one permission, and everything deliberately not requested
- [Testing](docs/testing.md) — coverage map and honest gaps
- [Known limitations](docs/known-limitations.md)
- [Release checklist](docs/release-checklist.md)
