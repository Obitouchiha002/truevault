# Release checklist

Work top to bottom. Anything that fails stops the release.

## Build

- [ ] `./gradlew clean`
- [ ] `./gradlew :app:assembleDebug` — no errors, no warnings
- [ ] `./gradlew testDebugUnitTest` — all pass
- [ ] `./gradlew connectedDebugAndroidTest` on a real device — migrations, Keystore, first-run flow
- [ ] `./gradlew :app:lintDebug` — clean; every suppression still justified in `lint.xml`
- [ ] `./gradlew bundleRelease` — succeeds with minification and resource shrinking on
- [ ] Install the **release** build and exercise: create vault → import → view → move → delete
      original → scan → backup → restore. Minified builds break reflection-based code, and Room,
      Hilt and kotlinx.serialization all use it

## Manifest and permissions

- [ ] Inspect the merged manifest (`app/build/outputs/logs/manifest-merger-release-report.txt`)
- [ ] Only `USE_BIOMETRIC` is present, and no dependency has added anything
- [ ] `android:allowBackup="false"` and `dataExtractionRules` still exclude every domain
- [ ] `FileProvider` is `exported="false"` and `file_paths.xml` still covers only the plaintext cache
- [ ] No other component is exported except the launcher activity

## Security

- [ ] `SecureLog` is configured from `BuildConfig.DEBUG` only
- [ ] Nothing logs a file name, path, URI, password, key, recovery key or search query
- [ ] No hardcoded secrets, test credentials, development endpoints, sample files or fake data
- [ ] The Keystore key is still non-exportable, GCM, `setRandomizedEncryptionRequired(true)`
- [ ] The biometric key still sets `setInvalidatedByBiometricEnrollment(true)`
- [ ] No WebView, no remote code loading, no dynamic class loading anywhere
- [ ] Backup entry names are still validated against path traversal
- [ ] Container header lengths are still bounds-checked before allocation
- [ ] `FLAG_SECURE` is applied and screenshot blocking defaults to on

## Data safety

- [ ] `TrueVaultDatabase.VERSION` matches the newest exported schema
- [ ] Every version bump has a migration and a migration test
- [ ] Destructive migration appears nowhere
- [ ] Restore an archive produced by the previous release into this build
- [ ] Verify a wrong backup passphrase is refused with the right message
- [ ] Verify a truncated archive is refused

## Honesty review

The claims below are product requirements. Re-read the strings before shipping:

- [ ] No text claims "unhackable", "military-grade", or protection against a rooted device
- [ ] No screen shows "Original deleted" before the platform confirmed it
- [ ] The scanner's limitation banner is still permanent and non-dismissible
- [ ] Private Apps still says "not supported" where it is, and never shows a fake success
- [ ] The unrecoverable-vault warning appears before a password is ever chosen
- [ ] The share warning appears before the share sheet

## Accessibility

- [ ] TalkBack pass over onboarding, unlock, vault, import and scan
- [ ] Every interactive element has a content description or a visible label
- [ ] Nothing conveys meaning through colour alone — the status pills carry icon plus text
- [ ] 200% font scale does not clip or overlap
- [ ] All touch targets are at least 48dp
- [ ] Animations respect the system's reduced-motion setting

## Performance

- [ ] Import a 2 GB video: memory stays flat, progress moves, cancel works
- [ ] 1,000+ item vault: scrolling stays smooth, search stays responsive
- [ ] Cold start to unlock screen is under two seconds on a mid-range device
- [ ] No ANR when a slow cloud provider is picked as an import source

## Store listing

- [ ] Data safety form matches reality: no data collected, no data shared
- [ ] Screenshots contain no real personal files
- [ ] The description repeats the recovery warning
