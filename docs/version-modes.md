# Product modes and the capability matrix

TrueVault is **one** app: one Git repository, one application id, one security engine, one vault
engine, one design system, one Room database, one encryption format, one backup format. There is no
second Android Studio project and no second Play listing.

What differs between devices is which *capabilities* are offered, and that is decided at runtime by
`:core:capabilities` — never by the SDK level alone.

| Mode | Android | What it means |
|------|---------|---------------|
| **TrueVault Modern** | 15+ (API 35+) | Everything in Core, plus Private Space guidance and optional Secure Launcher Mode |
| **TrueVault Core** | 8–14 (API 26–34) | The complete file-security product |

## Capability matrix

| Feature | Modern | Core |
|---------|--------|------|
| Encrypted file vault | Full | Full |
| Secure Copy | Full | Full |
| Secure Move | Full | Full |
| Original deletion verification | Full | Full where the provider supports it |
| Photo and video import | Full | Full, with the version-appropriate picker |
| Documents and other files | Full | Full |
| Leak Scanner | Full | Full within granted storage access |
| Encrypted backup | Full | Full |
| Biometric vault unlock | Full | Full where hardware supports it |
| Private Space guidance | Full | Not available |
| Private Space installation guidance | Full | Not available |
| Private-profile app listing | Optional launcher mode | Not available |
| Private-profile app launching | Optional launcher mode | Not available |
| OEM secure-folder guidance | Optional | Optional |
| Work-profile awareness | Supported | Supported |
| App cloning or virtualisation | **Never** | **Never** |
| App usable after the main copy is uninstalled | Through real Private Space only | Not supported |
| Launcher icon hiding | Optional launcher feature | Optional launcher feature only |
| Hiding an app via an accessibility service | **Never** | **Never** |

## Detection, not assumption

`DeviceCapabilityDetectorImpl` probes all of the following, and reports unknown as **unavailable**:

- API level
- Private Space availability, configuration and lock state
- Whether TrueVault holds the Home role
- Device-policy and managed-device restrictions
- Work-profile presence, distinguished from private and clone profiles by `LauncherUserInfo`
- Biometric hardware and enrolment, strong biometrics only
- Whether Keystore keys are hardware-backed
- Which photo picker exists
- How far the platform allows original deletion
- Whether an OEM privacy settings screen actually resolves

Capabilities are re-probed on resume, on return from system settings, and on profile broadcasts
(`ACTION_PROFILE_ADDED/REMOVED/ACCESSIBLE/INACCESSIBLE` from API 34, plus
`ACTION_PROFILE_AVAILABLE/UNAVAILABLE` from API 35). Everything downstream observes a `Flow`, so a
Private Space locked from the notification shade updates the UI without a restart.

### Rules the code follows

- No API 35 call sits in common code without an SDK guard.
- API-specific work lives in its own class, marked `@RequiresApi`.
- `Android15PrivateAppsController` is constructed **only** when `SDK_INT >= 35`, by the Hilt module.
  On Android 8 the class is never loaded.
- `NoSuchMethodError` is never caught as version detection — discovering the platform by crashing
  into it is not detection.
- Feature detection and role checks are preferred over `Build.MANUFACTURER`.

## Source structure

```
:core:capabilities
  model/            TrueVaultProductMode, PrivateAppsSupport, DeviceCapabilities,
                    PrivateSpaceState, CapabilityActionResult, LauncherAppEntry
  provider/         PrivateSpaceCapabilityProvider, LauncherRoleProvider,
                    ManagedProfileProvider, OemSettingsCapabilityProvider,
                    BiometricCapabilityProvider, MediaPickerCapabilityProvider,
                    DocumentDeleteCapabilityProvider
  privateapps/      PrivateAppsController
                    ├── UnsupportedPrivateAppsController   (Core)
                    └── Android15PrivateAppsController     (@RequiresApi 35)
  DeviceCapabilityDetector / DeviceCapabilityDetectorImpl
  di/               runtime selection of the controller implementation
```

`:feature:launcher` holds Secure Launcher Mode and Launcher Visibility; both are optional, both are
off by default, and neither is offered during onboarding.

## Private Apps on Modern

The journey is entirely Android's:

```
Private Apps → detect state → not configured
  → read the separate-installation warnings and acknowledge them
  → open the system privacy settings
  → user sets up Private Space with its own lock
  → return to TrueVault → capability re-checked on resume
```

Once configured, TrueVault explains that installing an app privately means installing it *again*
inside the profile, with its own data.

**Removing the main copy** is gated. The button stays disabled until either a private copy was
observed through supported APIs, or the user explicitly states they verified it themselves — and the
two are labelled differently, because describing a manual confirmation as an automatic verification
would be a lie. Uninstalling always goes through Android's own confirmation screen.

### What is never done

Copying APKs into the vault, executing an APK from internal storage, virtual containers, unofficial
cloning frameworks, silent install or uninstall, copying another app's private data, transferring a
login session, accessibility-service control, root, Shizuku, hidden APIs, or device-owner privileges
for a consumer app. None of these are supported ways to isolate an app.

## Private Space state handling

`PrivateSpaceState` has eight explicit values, and locking is handled properly:

- Private app entries are **cleared from state**, not merely filtered — a locked profile's labels
  leave memory rather than sitting behind a flag.
- Search returns nothing for them.
- No further queries are made against the hidden profile.
- The locked card shows no count and no names: a count alone would leak how many private apps exist.
- On unlock, the list is re-read from the platform rather than restored from a cache.

## Secure Launcher Mode

Optional, off by default, and reachable only from **Settings → Advanced Privacy**. It requires the
Home role, and the role is explained before the system dialog appears:

> To display and launch apps from supported private profiles, TrueVault must become your Home app.
> This changes your phone's main launcher. You can switch back at any time from Android settings.

Package visibility stays narrow: `LauncherApps.getActivityList` on profiles a Home app may already
see. `QUERY_ALL_PACKAGES` is not declared, non-launchable packages are never enumerated, and no
installed-app information is sent anywhere — the app contains no analytics at all.

## Launcher Visibility (Core and Modern)

Named **Launcher Visibility**, never "Private App Container", "Secure App Copy" or "App Vault",
because it is none of those. It removes icons from TrueVault's own launcher grid. The apps stay
installed and still appear in Settings, system search, notifications, share sheets and any other
launcher. It:

- is available only while TrueVault is the active Home app,
- is disabled by default,
- requires an unlocked vault to change,
- stores its list in the vault's own preferences file in `noBackupFilesDir`,
- offers "Restore all hidden icons" as an always-available escape,
- never blocks Settings, never blocks uninstall, never intercepts another app's UI, and never uses
  an accessibility service.

## Work profiles

A work profile is an enterprise-managed environment and is never presented as a consumer private
space. TrueVault detects one, runs correctly inside one, and honours administrator restrictions. It
does not provision profiles, does not ask for device-owner rights, and does not merge personal and
work vaults.

Separation is the platform's, not something TrueVault has to implement: each Android profile already
has its own app storage, its own Room database, its own Keystore keys, its own vault session and its
own backups. **Encryption keys are never shared across profiles** — the Keystore in one profile
cannot produce the keys of another.

## Shared foundation

Identical in both modes, with no branching anywhere:

encryption algorithm · encrypted-file format · database schema · Secure Copy engine · Secure Move
engine · backup format · recovery-key format · scanner fingerprint format · vault item identifiers ·
error model · design system · authentication model · import transaction model.

**A backup made in Core mode restores in Modern mode, and the reverse.** Backups contain vault data
only — never Private Space app installations or app data, which TrueVault has no access to and no
business copying.

## Build configuration

`minSdk = 26`, `compileSdk = 37`, `targetSdk = 37`. The target SDK is never lowered to escape modern
Android restrictions, and no legacy APK with an old target is published.

**No product flavors are defined.** The specification allows them "only when testing or distribution
requires them", and neither does here: the production build picks its capabilities at runtime, which
is exactly what needs testing. Adding `modernDebug`/`coreDebug` variants would mean testing
something users never run. Mode is forced for testing by running on the matching API level, which is
what the emulator matrix in [testing.md](testing.md) covers.

## Play Store presentation

One listing: **TrueVault: Private File Security**. Not two apps, not "Old" and "New".

> Core file-security features are available on supported Android devices. Advanced private-app
> features require Android 15 or later and may depend on device support, system settings and
> launcher configuration.

Never advertised: works on every Android phone · clones any app · runs uninstalled apps · hides apps
completely on all versions · transfers all app data · stops every notification leak.

Screenshots: home dashboard, Secure Copy or Move, file vault, leak scanner, privacy report, backup
and recovery — plus Private Space guided setup and Secure Launcher Mode, marked *"Available on
supported Android 15+ devices"*.
