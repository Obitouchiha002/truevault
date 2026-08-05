# TrueVault architecture

## Data flow

One direction, everywhere:

```
Compose UI  ──user event──▶  ViewModel  ──▶  Use case  ──▶  Repository  ──▶  Room / Crypto / Storage
     ▲                                                                              │
     └──────────────────────── immutable UI state ─────────────────────────────────┘
```

Every screen exposes the same three types:

```kotlin
data class ScreenUiState(...)     // immutable, exposed as StateFlow
sealed interface ScreenAction     // what the user did
sealed interface ScreenEffect     // one-shot: navigation, snackbars
```

State is collected with `collectAsStateWithLifecycle`. Effects are a `SharedFlow` with
`extraBufferCapacity = 1` and `DROP_OLDEST`, so a navigation event is never lost to a slow collector
and never replays on rotation.

### Rules the codebase holds to

- No business logic inside a Composable.
- No `Context` threaded through domain layers.
- No `GlobalScope`; the one long-lived scope is `@ApplicationScope`, provided by Hilt.
- No file, hashing or crypto work on the main thread — dispatchers are injected, never referenced
  statically, so tests can substitute a scheduler.
- No raw password, PIN, key or recovery phrase in Room, DataStore or a navigation route.

## Module graph

```
                     ┌──────────┐
                     │   :app   │
                     └────┬─────┘
                          │
        ┌─────────────────┼──────────────────┐
        ▼                 ▼                  ▼
  :feature:home    :feature:vault     :feature:settings   … (9 features)
        │                 │                  │
        └────────┬────────┴──────────────────┘
                 ▼
   :core:designsystem ──▶ :core:model
   :core:common       ──▶ :core:model
   :core:datastore    ──▶ :core:common, :core:model
   :core:database     ──▶ :core:common, :core:model      (Phase 2)
   :core:crypto       ──▶ :core:common, :core:model      (Phase 1–2)
   :core:storage      ──▶ :core:common, :core:model      (Phase 2)
   :core:testing      ──▶ :core:common, :core:model
```

Feature modules deliberately do **not** depend on `:core:database`, `:core:crypto` or
`:core:storage`. A screen reaches those only through a repository, which is what keeps encryption
and file handling out of the UI layer by construction rather than by convention.

`:core:model` and `:core:crypto` contain no Compose code.

## Build logic

All shared Gradle configuration lives in `:build-logic:convention` as compiled plugins:

| Plugin id | What it does |
|-----------|--------------|
| `truevault.android.application` | app module: SDK levels, Java/Kotlin target, lint, packaging, test deps |
| `truevault.android.application.compose` | Compose compiler and BOM for the app |
| `truevault.android.library` | library module baseline; resources off by default |
| `truevault.android.library.compose` | Compose plus Android resources for UI modules |
| `truevault.android.feature` | library + compose + Hilt + navigation + the core modules a screen may use |
| `truevault.android.hilt` | KSP and Hilt wiring |
| `truevault.android.room` | Room dependencies and the checked-in schema location |

Two AGP 9 specifics worth knowing before editing these:

1. **AGP 9 has Kotlin built in.** Applying `org.jetbrains.kotlin.android` is an error. The Kotlin
   version is whatever AGP bundles (2.3.21), and every Kotlin compiler plugin in the catalog is
   pinned to exactly that.
2. **`CommonExtension` no longer exposes `defaultConfig`, `lint`, `packaging` or `testOptions` to
   Kotlin.** Those members moved to `LibraryExtension` / `ApplicationExtension` with concrete types.
   The convention plugins therefore resolve their own extension and pass the individual blocks to
   shared helpers in `KotlinAndroid.kt`.

## Navigation

Type-safe routes, declared with `@Serializable` next to the screen that owns them:

```kotlin
@Serializable data object HomeRoute
@Serializable data class VaultItemRoute(val vaultItemId: String)
```

Each feature exports a `NavGraphBuilder.xScreen(...)` extension and `NavController.navigateToX()`.
`:app` wires them together and owns nothing about a feature's internals.

**Routes carry identifiers only.** No file path, URI, password, key or file name ever appears in a
route — routes survive in the back stack, are written to saved state, and are logged by the
navigation library.

Top-level navigation uses `popUpTo(start) { saveState = true }`, `launchSingleTop` and
`restoreState`, so the bottom bar never grows an unbounded stack.

## Design system

Tokens live in `:core:designsystem/theme`:

- `Color.kt` — the branded light and dark schemes, plus `TvStatusColors` for success / warning /
  info, which Material 3 has no slot for. `success` is intentionally distinct from `primary`:
  primary means "do this", success means "this is proven done".
- `Type.kt` — system sans-serif, three weights maximum.
- `Dimens.kt` — 20dp screen padding, 20dp card radius, 16dp button radius, 48dp minimum touch target.
- `Motion.kt` — durations and easings. Everything routes through `TvMotion.duration()`, which returns
  0 when the user has switched animations off in system settings, so reduced-motion support is a
  property of the design system instead of something each screen re-implements.

Dynamic colour is opt-in and off by default; wallpaper-derived colours would break the meaning the
status palette carries.

## Error model

`VaultError` is a sealed interface in `:core:model`. It deliberately carries no `Throwable`, which is
why `Outcome<T>` exists instead of Kotlin's `Result`: provider exceptions routinely embed file names
and URIs, and those must not travel toward the UI.

`SecureLog` is the only logging entry point. It is a no-op unless the application enabled it, and the
application only does that for debuggable builds. Errors log the throwable's class name, never its
message.

## Security boundaries

```
┌─────────────────────────────────────────────────────────────────┐
│ Untrusted / outside our control                                 │
│   other apps · cloud providers · the platform trash · backups   │
└───────────────────────────┬─────────────────────────────────────┘
                            │  user-granted URIs only
┌───────────────────────────▼─────────────────────────────────────┐
│ :core:storage — the only module that touches ContentResolver    │
│   Photo Picker · SAF · streaming · delete requests              │
└───────────────────────────┬─────────────────────────────────────┘
                            │  plaintext streams, never files on shared storage
┌───────────────────────────▼─────────────────────────────────────┐
│ :core:crypto — the only module that touches key material        │
│   Keystore master key (non-exportable) · per-file keys · KDF    │
└───────────────────────────┬─────────────────────────────────────┘
                            │  ciphertext + wrapped keys only
┌───────────────────────────▼─────────────────────────────────────┐
│ app-private internal storage + Room                             │
│   filesDir/vault/items · thumbnails · temp                      │
│   noBackupFilesDir for anything that must not be backed up      │
└───────────────────────────┬─────────────────────────────────────┘
                            │  IDs, counts, statuses — no secrets
┌───────────────────────────▼─────────────────────────────────────┐
│ ViewModels and Compose UI                                       │
│   FLAG_SECURE on the window · in-memory session with expiry     │
└─────────────────────────────────────────────────────────────────┘
```

Crossing rules:

- Nothing above the crypto layer ever sees an unwrapped key.
- Nothing below the UI layer ever sees an Android `Context` it did not need.
- The authentication session is in-memory with an expiry, not a boolean in DataStore — a persisted
  boolean survives a reboot, and an unlocked-forever flag is not a lock.
