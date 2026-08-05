# TrueVault threat model

Every threat below is written as: **Threat → Impact → Mitigation → Remaining limitation.**

The remaining limitation is the important column. A threat model that lists only mitigations is
marketing; the honest part is what is still true after the mitigation.

**TrueVault does not claim protection against a fully compromised or rooted operating system.** An
attacker with root can read this app's memory while it is unlocked, and no design in userspace
changes that.

---

## 1. Device theft, locked screen

**Impact:** Total vault exposure if the vault can be opened without the password.

**Mitigation:** The vault master key exists in memory only, and only while the session is open. It
is sealed twice on disk: once under an Argon2id key derived from the user's password, and once more
under a non-exportable Android Keystore key. Auto-lock defaults to locking the instant the app
leaves the foreground.

**Remaining limitation:** If the device is stolen while TrueVault is open on screen and unlocked,
the thief sees whatever is on screen. Auto-lock cannot help before it fires.

## 2. Device theft, thief pulls the files off the phone

**Impact:** Offline brute force of the vault password on hardware of the attacker's choosing.

**Mitigation:** The outer seal uses a Keystore key that cannot leave the device. Copied files are
undecryptable anywhere else, so the password cannot be attacked offline at all — only on the device,
one Argon2id evaluation at a time.

**Remaining limitation:** On a device where the Keystore is software-backed (reported honestly in
Security settings), an attacker who can extract the app's private storage *and* defeat the software
keystore is back to attacking Argon2id.

## 3. User leaves the app unlocked and hands the phone to someone

**Impact:** Full read access to the vault.

**Mitigation:** Configurable auto-lock, lock on screen-off (default on), and a manual "Lock now".

**Remaining limitation:** Within the grace period the user configured, the vault is open. A longer
auto-lock is a convenience the user chooses, and it costs exactly this.

## 4. A malicious app requests a shared file

**Impact:** Silent exfiltration of decrypted content.

**Mitigation:** The `FileProvider` is not exported, grants are per-share and read-only, and it
exposes exactly one cache subdirectory — never the vault, the database, or the Keystore material. A
share is always user-initiated.

**Remaining limitation:** Once the user shares a file with another app, that app has a copy.
TrueVault says so before the share sheet opens; it cannot enforce anything afterwards.

## 5. The original file is still visible after a Secure Move

**Impact:** The user believes a file is private when a copy sits in their gallery.

**Mitigation:** Deletion outcome is observed, never assumed. `USER_CANCELLED`, `FAILED`,
`PROVIDER_NOT_SUPPORTED` and `PERMISSION_LOST` each keep the item at `ORIGINAL_REMAINS`, the vault
list shows that status, the privacy score deducts for it, and the scanner finds it again.

**Remaining limitation:** TrueVault cannot delete a file the platform will not let it delete. On
API 26–28 and on read-only providers, "not supported" is the honest and final answer.

## 6. A duplicate exists somewhere the app was never pointed at

**Impact:** A copy survives a cleanup the user believes was complete.

**Mitigation:** The scanner compares by size, MIME type and SHA-256 within folders the user granted,
and every result is shown with its confidence before anything is removed.

**Remaining limitation:** A scan sees only the folder it was given. Other apps' private storage,
encrypted chats and cloud accounts are outside what any normal Android app can inspect, and the
scan screen says so permanently rather than in a dismissible tip.

## 7. The process crashes during an import

**Impact:** A half-written container, an orphaned temporary file, or — at worst — an original deleted
for a copy that never completed.

**Mitigation:** Encryption writes to `<uuid>.vault.part`, the container is decrypted and
authenticated end-to-end *before* the atomic rename, and the original is only discussed after the
rename. Startup recovery marks interrupted transactions retryable and deletes only TrueVault's own
temporary files.

**Remaining limitation:** A crash leaves the import to be redone. Recovery never resumes a partial
encryption — it restarts it, because a partially encrypted file cannot be verified.

## 8. Storage fills up mid-import

**Impact:** A partial container, or a device driven to zero free space.

**Mitigation:** Required space is estimated before encryption begins — source size plus per-chunk
overhead plus a thumbnail allowance plus a safety buffer — and the import is refused up front with
the shortfall stated. A write failure mid-stream is caught, the `.part` file removed, the original
untouched.

**Remaining limitation:** Another app can consume the space between the check and the write. That
case degrades to the mid-stream failure path, which is safe but wastes the user's time.

## 9. The database is corrupted

**Impact:** Encrypted files exist on disk with no index describing them.

**Mitigation:** The vault directory and the database live together in `noBackupFilesDir`, so they
are backed up and restored as a unit or not at all. Room migrations are explicit and destructive
migration is never enabled. A row whose metadata will not decrypt is surfaced as `CORRUPTED`
rather than hidden.

**Remaining limitation:** If the database is lost entirely and no backup exists, the encrypted files
remain but their names, types and keys' associations are gone. This is what the encrypted backup
exists to prevent.

## 10. Someone copies the user's backup archive

**Impact:** Offline attack on the whole vault at once.

**Mitigation:** The archive is encrypted before it leaves the app, with an Argon2id key derived from
a passphrase the user chooses for the backup. The manifest carries no file names, and a wrong
passphrase is detected by a check value rather than by a partial decryption.

**Remaining limitation:** A backup deliberately is *not* bound to the device — that is the point of a
backup. Its entire security is the passphrase. A weak backup passphrase is the single easiest way to
undo everything else in this document.

## 11. Rooted or otherwise compromised device

**Impact:** Everything.

**Mitigation:** None that is honest. Keys live in the Keystore and plaintext is minimised, which
raises the effort, not the outcome.

**Remaining limitation:** Stated plainly: TrueVault does not defend against an attacker who controls
the operating system. Any app claiming otherwise is wrong.

## 12. Screen recording and shoulder surfing

**Impact:** Vault content captured while it is on screen.

**Mitigation:** `FLAG_SECURE` on the activity window blocks screenshots and screen recording, and
blanks the app in the recent-apps switcher. On by default, and it applies to dialogs because it is a
window flag rather than a Compose-level trick.

**Remaining limitation:** A second camera pointed at the screen. Nothing in software addresses that.

## 13. Debug logs leak metadata

**Impact:** File names, paths and URIs in logcat, readable by anyone with adb access.

**Mitigation:** `SecureLog` is the only logging entry point, it is a no-op unless the application
enabled it, and the application only enables it for debuggable builds. Errors log the exception class
name, never its message, because provider exceptions routinely embed file paths. Release builds also
strip `android.util.Log` verbose/debug/info calls via ProGuard.

**Remaining limitation:** A dependency that logs on our behalf at warn or error level is outside this
choke point. The dependency list is deliberately small for this reason.

## 14. Clipboard exposure

**Impact:** A recovery key or password copied to the clipboard is readable by other apps and lands
in the system clipboard history.

**Mitigation:** TrueVault provides no "copy" action for the recovery key. The key is shown once, and
the screen recommends paper over a screenshot — because a screenshot goes straight into the gallery
the user is trying to keep things out of.

**Remaining limitation:** Nothing stops a user from selecting text manually. The app can only avoid
encouraging it.

## 15. App uninstall removes local data

**Impact:** Total, irreversible vault loss.

**Mitigation:** The encrypted backup export exists specifically for this. The Backup screen states
that TrueVault has no server and no copy of anything.

**Remaining limitation:** Android gives no reliable pre-uninstall hook. A user who uninstalls without
a backup loses the vault, and no design prevents that.

## 16. The user forgets the password

**Impact:** Permanent loss of the vault.

**Mitigation:** A recovery key, shown once and confirmed by typing one group back — a user who has
not actually recorded it fails that check while it still matters. The privacy score deducts until a
recovery key exists.

**Remaining limitation:** There is no reset. That is a deliberate consequence of having no server and
no escrow, and the app says so before a password is ever chosen.

## 17. The user loses the recovery key

**Impact:** Falls back to the password only.

**Mitigation:** A new recovery key can be generated at any time from an unlocked vault, which
invalidates nothing else.

**Remaining limitation:** Losing both the password and the recovery key is unrecoverable, by design.

## 18. A new biometric is enrolled on a stolen unlocked device

**Impact:** The attacker adds their own fingerprint and opens the vault with it.

**Mitigation:** The biometric-bound Keystore key sets `setInvalidatedByBiometricEnrollment(true)`, so
enrolling a new biometric destroys it. TrueVault detects this, turns biometric unlock off, and says
why.

**Remaining limitation:** The password still opens the vault, as it must.

## 19. Tampering with the encrypted files on disk

**Impact:** Silently altered or truncated user data.

**Mitigation:** Every chunk is AES-256-GCM with the container header, the chunk index and an
end-of-file marker as associated data. Reordering, duplicating, dropping or splicing chunks all fail
authentication, and truncation is caught by the authenticated plaintext length. Partial plaintext is
never returned.

**Remaining limitation:** An attacker with write access can still delete files. Integrity protects
against undetected modification, not against destruction.

## 20. Malicious backup archive or hostile file names

**Impact:** Path traversal writing outside the vault directory, or a name that breaks the UI.

**Mitigation:** Backup entry names are matched against the manifest and rejected if they contain
`..` or start with a separator. Every entry's SHA-256 is verified before it is unpacked. Provider
display names have path separators and control characters stripped, and are capped at 255
characters. Container header lengths are bounds-checked before any allocation.

**Remaining limitation:** A valid archive with genuinely hostile *content* is stored as-is — TrueVault
is a vault, not an antivirus.
