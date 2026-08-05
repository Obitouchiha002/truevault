# Permissions

TrueVault declares **one** permission, and it is a normal one.

| Permission | Type | Why | If it is unavailable |
|------------|------|-----|----------------------|
| `USE_BIOMETRIC` | Normal — no runtime dialog | `BiometricPrompt` requires it. It grants no access to biometric data | Biometric unlock is hidden; the password still works |

There is also one `uses-feature` entry, `android.hardware.fingerprint` with `required="false"`, so
devices without a fingerprint sensor can still install the app.

## What TrueVault deliberately does not request

| Not requested | Why it is not needed |
|---------------|----------------------|
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | The Android Photo Picker returns URIs the user explicitly chose |
| `READ_EXTERNAL_STORAGE` | The Storage Access Framework does the same for documents |
| `MANAGE_EXTERNAL_STORAGE` | Never. All-files access is not needed to store files the user hands over, and asking for it would be the clearest possible sign an app wants more than it needs |
| `POST_NOTIFICATIONS` | Nothing runs in the background that needs to notify |
| Contacts, location, microphone, camera, call log, SMS | None of them relate to securing a file |
| Accessibility service | An accessibility service can read every screen on the device. No vault app needs one |
| Usage access, device administrator | Not needed, and both are commonly abused by apps in this category |

## How access is actually obtained

| Task | Mechanism | Scope granted |
|------|-----------|---------------|
| Pick photos and videos | `PickMultipleVisualMedia` | Exactly the items picked |
| Pick documents | `ACTION_OPEN_DOCUMENT` | Exactly the documents picked |
| Choose a folder to scan | `ACTION_OPEN_DOCUMENT_TREE` | That folder subtree, and only while scanning |
| Delete a media original | `MediaStore.createDeleteRequest` | One system dialog the user can decline |
| Delete a document original | `DocumentsContract.deleteDocument` | Only when the provider advertises `FLAG_SUPPORTS_DELETE` |
| Share a file out | `FileProvider`, not exported, one cache subdirectory | One read grant, revoked after |

## If the user declines

Every picker can be dismissed and nothing happens — no import starts, no state changes. Declining a
delete confirmation is a first-class outcome: the vault copy stays, the item is marked
`ORIGINAL_REMAINS`, and the app says so instead of retrying or hiding it.

The app remains fully usable with biometrics unavailable, with every optional permission declined,
and with Private Space unsupported.
