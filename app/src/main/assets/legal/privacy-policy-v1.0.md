# Privacy Policy

**Version:** 1.0
**Effective date:** [EFFECTIVE DATE REQUIRED]
**Last updated:** [LAST UPDATED DATE REQUIRED]
**Developer:** [LEGAL BUSINESS NAME REQUIRED]
**Privacy contact:** [PRIVACY EMAIL REQUIRED]

> Placeholders in square brackets are unresolved and must be filled from `legal/legal-config.json`
> before release. The build's legal-readiness check fails while any remain.

---

## 1. Introduction

TrueVault is an app that encrypts files you choose and keeps them on your device. This policy
explains what the app reads, what it stores, where that data lives, and what it does not do.

It is written to match the app's actual behaviour. The behaviour was audited section by section and
recorded in `legal/data-practices-inventory.md`; this policy is generated from that audit. Where the
audit could not prove something, this policy says so instead of guessing.

## 2. Who provides TrueVault

TrueVault is provided by [LEGAL BUSINESS NAME REQUIRED], a [BUSINESS TYPE REQUIRED] operating from
[COUNTRY OF OPERATION REQUIRED]. Contact details are in section 30.

## 3. Scope

This policy covers the TrueVault mobile application for Android. It does not cover:

- Android itself, or your device manufacturer's software
- Other apps you share a file with
- Cloud storage services where you choose to save an exported backup
- Any website other than [WEBSITE DOMAIN REQUIRED]

## 4. Information the app accesses

TrueVault accesses only what you hand it:

| What | When |
|---|---|
| Files, photos and videos you select | Only the specific items you pick through the Android photo picker or the document picker |
| Basic file details — name, size, type, dates | For the files you selected, so the app can display and organise them |
| Folders you explicitly grant | Only when you run the Privacy Leak Scanner and choose a folder |
| Biometric verification **result** | Only if you turn on biometric unlock |
| Launchable apps in a private profile | Only on Android versions that support Private Space, and only while TrueVault is your home app |

The app declares two Android permissions: `USE_BIOMETRIC` and `INTERNET`. Both are normal
permissions, so neither shows a runtime dialog. It requests no storage permission, no "all files
access", no camera, no microphone, no location and no contacts.

`INTERNET` is used for exactly one thing, described in the next section. It is not used to send,
back up, sync or examine anything in your vault, and there is no code in the app that could.

## 5. Information the developer collects

The app contacts the developer's server when it starts and when you return to it, and sends **four
things**:

| Sent | What it is |
|------|------------|
| An install identifier | Android's own per-app identifier for your device. It is not your name, your account, your phone number or your advertising ID. It survives reinstalling the app and is cleared by a factory reset. |
| The name you typed on first launch | Whatever you chose to type. Nothing checks it and nothing verifies it. It can be a single letter. |
| The app version | For example `0.1.2`. |
| The time of the request | Recorded as first-seen and last-seen. |

**What is never sent, and what no code path here could send:** anything about your vault. Not its
contents, not the files in it, not their names, not their number, not their size, not whether a
vault exists at all. Not your password, not your recovery key, not your notes, not your settings,
not your location, not your device model.

**Why it exists.** It lets the developer see how many people are using the app, respond to abuse by
suspending a specific install, and enable optional features on an install. If your install is
suspended the app will not open and will tell you so. **Your files are not deleted by a suspension**
— they stay encrypted on your device and open again if it is lifted.

**The app still works offline.** The response is remembered on your device, so starting the app never
waits for a network and never fails because of one. If there is no connection the app carries on
exactly as it did the last time it had one.

## 6. Files you select

The files you bring into TrueVault are the reason the app exists. They are:

- read only after you select them
- encrypted before they become a vault item
- stored in the app's private storage on your device
- never uploaded anywhere by TrueVault

Filenames are not used for the names of the encrypted files on disk. Each container is named with a
random identifier, so the storage layout does not reveal what you have stored.

## 7. Encryption and local storage

Each file gets its own encryption key. That key is wrapped by your vault key, which is derived from
your password or PIN using a deliberately slow key-derivation function, and then sealed again by a
key held in your device's hardware-backed keystore.

The practical effect: the stored data is useless without your password, and useless on a different
device — with one deliberate exception, the recovery key described in section 12.

Encrypted vault files live in the app's private storage. They are not on your SD card, not in your
gallery, and not in any shared folder.

## 8. Secure Copy

Secure Copy creates an encrypted copy in the vault. **The original file stays exactly where it
was.** The app shows the item's status as "Original remains" and does not describe it as fully
secured, because it is not.

## 9. Secure Move and deletion of originals

Secure Move does the following, in this order, and never in another order:

1. encrypts a copy into the vault
2. verifies that the encrypted copy can be opened
3. commits the vault item
4. **only then** asks Android to delete the original

Things you should know about step 4:

- Android shows its own confirmation dialog. You can decline it.
- Deletion can fail — some storage providers do not support it at all.
- If you decline, or it fails, the app says the original remains. It will **not** tell you the file
  was deleted.
- Deleting a file may move it to a system trash that still holds it for a period.
- Copies you made earlier, edits, files already shared with someone, and copies synced to a cloud
  service are outside TrueVault's reach. It cannot delete what it cannot see.

## 10. Temporary files and cache

To show or share a file, the app must decrypt it. Decrypted copies are written to the app's private
cache and are removed after use and on the next start. During an import, a partial encrypted file
exists until the import is verified and committed; if the import is interrupted, that partial file
is removed and your original is untouched.

## 11. Biometric authentication

If you enable it, Android performs the biometric check and returns only a result. **TrueVault never
receives, sees or stores your fingerprint or face data.** It stores only a handle to a key that
Android will unlock after a successful check.

Your password still works, and still decides. Biometrics are a convenience layer on top of it.

## 12. Password and recovery key

- Your password or PIN is never stored in a form that can be reversed.
- It is held in memory only while it is being used, and the buffer is overwritten afterwards.
- The recovery key is shown to you once. It is the only way back into your vault if you forget your
  password, and it is the only thing that works on a different device.
- **If you lose both, the vault cannot be recovered.** Not by you, not by the developer, not by
  anyone. There is no account, no email reset and no support route back in. The app tells you this
  before you create your lock, not afterwards.

## 13. Backup and restore

TrueVault's backup is a **local encrypted export**. There is no cloud backup and no provider
integration.

- The exported file is encrypted.
- Where it goes afterwards is your decision. If you save it to a cloud drive, that service's privacy
  policy applies to it.
- Android's automatic backup is disabled for this app, so your vault is not swept into a system
  backup without your knowledge.
- **Uninstalling the app removes your vault.** Local data does not survive uninstall. Only an export
  you made yourself does.

## 14. File export and sharing

When you share a file out of the vault, the app decrypts a temporary copy and hands it to the app
you picked, through a private, permission-scoped file provider.

Once another app has that file, TrueVault has no control over it. What happens next is governed by
that app's policy, not this one.

## 15. Privacy Leak Scanner

The scanner compares files in a folder **you granted** against your vault, using size, type and a
content hash. It can tell you that another accessible copy exists.

What it **cannot** see, and does not claim to:

- other apps' private storage
- messages, chats or anything already sent to another person
- cloud accounts and unsupported cloud locations
- locked system or manufacturer containers
- another device
- anything outside the access you granted

A clean scan result means "nothing was found in what I was allowed to look at". It does not mean no
copy exists.

## 16. Private Apps and Android profiles

On Android versions that support it, Android provides a Private Space — a separate profile with its
own app installations and its own data. TrueVault can show and launch those apps when it is your
home app and Android permits it.

- Private Space is managed by **Android**, not by TrueVault.
- TrueVault cannot read another app's private data, copy it, or clone an app.
- On older Android versions this feature does not exist, and the app says so rather than pretending.
- Manufacturer "private space" features are separate products outside TrueVault's control.

To list launchable apps, the app uses Android's launcher API. It does **not** request permission to
see every installed package, does not enumerate non-launchable apps, and never sends any of this
information anywhere.

## 17. Device information

The app checks what the device can do — Android version, whether secure hardware is present, whether
biometrics are enrolled, whether a private profile exists — so it can offer only features that
actually work. These checks happen on the device, produce no stored record beyond the current
session, and are not transmitted.

The app does not access advertising identifiers, IMEI, phone number, SIM details or location.

## 18. Diagnostics and analytics

**There are none.** The app contains no analytics SDK, no crash-reporting SDK and no advertising SDK.
Nothing about how you use the app — which screens you open, which features you use, how long you
spend, what you search for — is recorded or sent. The check-in described in section 5 reports that
the app started; it reports nothing about what you then did with it.

The app writes technical log lines only in developer builds, and even then a deliberate rule keeps
file names, paths, search terms and content out of them.

Because no optional data collection exists, the app does not show a "help us improve" consent
screen. Showing one for something that does not exist would be theatre.

## 19. Third-party services

TrueVault uses open-source software libraries — Android's own AndroidX components, a dependency
injection library, Kotlin runtime libraries, an image loader, a media player, and a cryptography
library.

These are code libraries that run inside the app on your device. None of them is a service, none
receives data, and none communicates over a network in this app.

The app does use one hosted service: **Supabase**, which stores the check-in records described in
section 5 and nothing else. It never receives anything about your vault.

There is no advertising SDK, no attribution SDK, no cloud SDK and no payment provider.

## 20. Data sharing

The developer shares nothing, because the developer receives nothing.

Data leaves your device only when **you** cause it to: by sharing a file with another app, or by
saving an exported backup somewhere.

## 21. Data retention

| Data | Kept until |
|---|---|
| Encrypted vault files | You delete them, or reset the app |
| File details for vault items | Same |
| Scanner findings | You resolve them, or reset the app |
| Temporary decrypted copies | Removed after use and at next start |
| Your legal-acceptance record | You reset the app |

There is no server-side retention, because there is no server.

## 22. Deleting your data

You can:

- delete individual vault items
- delete all local vault data from **Settings → Legal and Privacy → Delete Vault Data**
- reset the app completely, which removes vault files, thumbnails, the database, preferences and the
  locally wrapped keys
- uninstall, which removes all app-private storage

None of these touch:

- files you kept outside the vault
- files you already shared with other apps
- backups you exported and saved elsewhere

The app says this on screen after a reset, rather than implying a clean sweep it cannot perform.

## 23. Security protections

- Per-file encryption keys, wrapped by a vault key derived with a slow key-derivation function
- A second sealing layer using your device's hardware-backed keystore, so the data is tied to this
  device
- Authenticated encryption: a modified or truncated container is refused rather than partially read
- A delay between failed unlock attempts, which grows with each failure
- No permanent-wipe-after-N-attempts rule, because that punishes people who mistype far more
  reliably than it stops an attacker
- Screen-content protection while the vault is open
- App-private storage; nothing in shared or external locations

## 24. Security limitations — stated plainly

TrueVault is not unbreakable, and this policy will not claim it is.

- It cannot protect data on a device that is already compromised.
- It cannot protect against someone who knows your password.
- It cannot protect against malware with system-level access.
- A short PIN is weaker than a long passphrase. The app tells you which is which before you choose.
- It cannot reach copies of your files that exist somewhere it has no access to.
- It cannot undo a file that has already been shared.
- No independent cryptographic audit has been performed on this app. The design is documented and
  tested; it has not been reviewed by a third party.

## 25. Rooted or compromised devices

On a rooted or modified device, the protections Android provides — and therefore the protections
TrueVault depends on — can be bypassed. The app cannot reliably detect every such condition and does
not claim to defend against it.

## 26. Children and age requirements

TrueVault is not directed at children. The minimum age to use it is
[MINIMUM USER AGE REQUIRED], as required in [PRIMARY MARKETS REQUIRED]. The app does not knowingly
collect information from anyone — of any age — because it collects nothing.

## 27. International data processing

All processing happens on your device. No personal data is transferred to the developer or to any
other country by the app, so no international transfer mechanism is engaged.

If a future version introduces a cloud feature, this section will be rewritten before that feature
ships, and you will be asked separately.

## 28. Your rights

Data-protection law in your region may give you rights of access, correction, erasure, portability
and objection.

For TrueVault these are unusual in one respect: the developer holds none of your data, so there is
nothing to disclose to you or to erase on your behalf. You exercise all of them directly:

| Right | How |
|---|---|
| Access | Your data is on your device, in the app |
| Portability | Settings → export an encrypted backup |
| Erasure | Settings → Delete Vault Data, or uninstall |
| Correction | Edit or replace items in the app |
| Objection / restriction | Nothing is processed by the developer to object to |

If you believe otherwise, contact [PRIVACY EMAIL REQUIRED]. You also have the right to complain to
your local data-protection authority.

## 29. Changes to this policy

Substantive changes get a new version number, a new effective date, and a plain-language summary of
what changed. If the change materially affects how your data is handled, the app asks you to review
it — it does not update the stored acceptance quietly.

Typographical and formatting corrections do not trigger a re-acceptance prompt, because interrupting
people for a comma teaches them to dismiss the prompts that matter.

Previous versions remain available at [PUBLIC ARCHIVE URL OPTIONAL].

## 30. Contact

| | |
|---|---|
| Privacy enquiries | [PRIVACY EMAIL REQUIRED] |
| Support | [SUPPORT EMAIL REQUIRED] |
| Developer | [LEGAL BUSINESS NAME REQUIRED] |
| Address | [BUSINESS ADDRESS REQUIRED WHERE LEGALLY MANDATED] |
| Website | [WEBSITE DOMAIN REQUIRED] |
