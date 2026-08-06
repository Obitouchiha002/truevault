# Play Console Data Safety mapping

Every answer TrueVault gives on the Play Console Data Safety form, with the source-code evidence
behind it. The evidence column is the point: a Data Safety form is a set of claims to Google and to
users, and each one has to be traceable to something in the repository.

Derived from [data-practices-inventory.md](data-practices-inventory.md). If the two disagree, the
form is wrong.

**Audit date:** 2026-08-06 · **App version:** 1.0 · **Branch:** `qa/autonomous-release-verification`

---

## Section 1 — Data collection and sharing

> Google's definition: **collected** means transmitted off the device. **Shared** means transferred
> to a third party. Data that is only processed on the device is *neither*.

### Overall answers

| Question | Answer | Evidence |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | No `INTERNET` permission; no network library in the dependency graph; no HTTP/socket call in the sources. Inventory §16. |
| Is all user data encrypted in transit? | **Not applicable** — no data is in transit | Same as above |
| Do you provide a way for users to request data deletion? | **Yes** | Settings → Delete Vault Data; full reset; uninstall. Inventory §19. |

### Per-category

| Category | Data type | Collected | Shared | Evidence |
|---|---|---|---|---|
| Location | Approximate, Precise | No | No | No location permission, no Play Services location |
| Personal info | Name, Email, User IDs, Address, Phone | No | No | No account system, no server, no sign-in |
| Financial info | Payment, Purchase history | No | No | No payment provider, no billing library |
| Health and fitness | — | No | No | Not accessed |
| Messages | — | No | No | Not accessed |
| **Photos and videos** | Photos, Videos | **No** | **No** | Selected via Photo Picker / SAF, encrypted locally into app-private storage. Never transmitted. Inventory §2, §6. |
| Audio | Voice, Music, Audio files | No | No | Files may be audio; treated identically to any other file. Not transmitted. |
| **Files and docs** | Files and docs | **No** | **No** | Same as photos and videos |
| Calendar | — | No | No | Not accessed |
| Contacts | — | No | No | Not accessed |
| **App activity** | Interactions, In-app search, Installed apps, Other actions | **No** | **No** | No analytics SDK. Launchable-app names are read via `LauncherApps` for the launcher grid only, never stored, never transmitted. Inventory §10, §13. |
| Web browsing | — | No | No | No browser, no WebView carrying user content |
| **App info and performance** | Crash logs, Diagnostics, Other | **No** | **No** | No crash-reporting SDK. `SecureLog` writes to logcat in debuggable builds only. Inventory §14. |
| Device or other IDs | Device or other IDs | **No** | **No** | No advertising ID, no device identifier accessed |

## Section 2 — Security practices

| Question | Answer | Evidence |
|---|---|---|
| Is data encrypted in transit? | Not applicable — nothing is transmitted | Inventory §16 |
| Can users request data deletion? | Yes | Settings → Delete Vault Data; uninstall removes app-private storage |
| Has your app been independently reviewed against a global security standard? | **No** | No third-party security review has been performed. Stated plainly in [../qa-reports/10-security-review.md](../qa-reports/10-security-review.md) §7. |
| Do you follow the Families policy? | Not applicable | App is not directed at children |

## Section 3 — Data-type detail

For every type above the answers are the same, so they are recorded once:

| Field | Value |
|---|---|
| Collected | No |
| Shared | No |
| Processing | On device only |
| Ephemeral | Temporary decrypted copies exist in the app's private cache while a file is viewed or shared, and are cleared after use and at next start (Inventory §7) |
| Required or optional | Every file in the vault is there because the user chose to put it there |
| Purpose | Encrypting and managing the user's own files |
| Encryption in transit | Not applicable |
| Deletion mechanism | Per-item delete, Delete Vault Data, full reset, uninstall |

## Section 4 — Permissions declared, and how they read to a user

| Permission | Source | Why the Play listing must explain it |
|---|---|---|
| `USE_BIOMETRIC` | Declared by the app | Required by `BiometricPrompt`. Normal permission. |
| `USE_FINGERPRINT` | `androidx.biometric:1.1.0` | Legacy fallback below API 28; minSdk is 26 so it is genuinely needed. Users see "fingerprint" in the permission list and deserve the reason. |
| ~~`ACCESS_NETWORK_STATE`~~ | media3-exoplayer | **Removed** with `tools:node="remove"` — see below |
| ~~`WAKE_LOCK`~~ | media3-exoplayer | **Removed** with `tools:node="remove"` |

The last two were merged in by media3 and were found by dumping the built APK, not by reading
source. A "no data collected" Data Safety form beside a manifest requesting network state is exactly
the kind of contradiction that gets an app rejected — and rightly so, because the user sees the
permission and not the explanation. Both are now removed.

## Section 5 — Third-party SDK evidence

| SDK category | Present? | Evidence |
|---|---|---|
| Analytics (GA, Firebase Analytics, Amplitude, Mixpanel…) | **No** | Not in `gradle/libs.versions.toml` or any `build.gradle.kts` |
| Crash reporting (Crashlytics, Sentry, Bugsnag…) | **No** | Same |
| Advertising (AdMob, AppLovin, Unity Ads…) | **No** | Same |
| Attribution (AppsFlyer, Adjust, Branch…) | **No** | Same |
| Cloud / backend (Firebase, AWS, Supabase…) | **No** | Same |
| Payments (Play Billing, Stripe…) | **No** | Same |
| Push (FCM) | **No** | Same |

Every runtime dependency is an on-device library: AndroidX, Hilt, kotlinx, Coil, media3,
BouncyCastle. See [../qa-reports/03-dependency-inventory.md](../qa-reports/03-dependency-inventory.md).

## Section 6 — Consistency check

Any contradiction here blocks release (§102).

| Pair | Consistent? | Note |
|---|---|---|
| App behaviour ↔ Privacy Policy | **Yes** | Policy generated from the inventory, which was generated from the source |
| App behaviour ↔ Terms | **Yes** | |
| Privacy Policy ↔ this map | **Yes** | Both say: nothing collected, nothing shared, no third-party SDKs |
| Manifest permissions ↔ policy | **Yes, after the fix.** | Before removing the media3 permissions, the manifest implied network capability the policy denied. |
| In-app disclosures ↔ policy | **PENDING** | The in-app Data and Permissions dashboard (§100) is not implemented yet. Blocks release. |
| Play Data Safety form ↔ this map | **NOT VERIFIED** | The form has not been filled in; no Play Console access in this environment. |
| Store description ↔ app | **NOT VERIFIED** | No store listing exists yet. It must disclose the vault, Secure Copy, Secure Move and the scanner. |

## Section 7 — What is not verified

| Item | Status |
|---|---|
| Runtime network capture on a device | **NOT RUN.** The "nothing is transmitted" answer rests on static evidence only. A proxied device run would make it observational, and should be done before submitting the form. |
| Release-artefact decompilation | **PARTIAL.** Permissions were dumped from the built APK; the R8-processed code was not reviewed. |
| Play Console form submitted | **NOT DONE — NO ACCESS** |
| Human legal review | **NOT DONE.** `legalReviewRequired` remains `true` in `legal-config.json`. |

## Rule

Adding any SDK, permission, network call or notification requires updating — in the same commit —
the inventory, the Privacy Policy, the Terms, this map, and the Play Console form. The legal release
gate in `qa-reports/13-legal-and-privacy-report.md` exists to catch the commit where that does not
happen.
