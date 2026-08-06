# 09 — Recovery and data-integrity review

Covers the failure paths that decide whether a user ever gets their files back: interrupted imports,
backup export and restore, database migration, and the three independent ways a vault can be opened.

Each section states what was **executed** and what was **read but not executed**. Nothing below is
recorded as verified on the strength of reading the code alone.

## 1. Key hierarchy — how many ways in, and what each one costs

| Path | Sealed by | Survives factory reset? | Verified how |
|---|---|---|---|
| Password / PIN | Argon2id(password, per-vault salt) → wraps master key; the wrapped copy is sealed again by an Android Keystore device key | No — the Keystore key is device-bound | Unit: `VaultKeyManagerTest` |
| Biometric | A second copy of the master key sealed by a separate, user-auth-bound Keystore key | No | Instrumented only — see gap below |
| Recovery key | A third copy sealed by a key derived from the printed recovery phrase, **deliberately not device-bound** | **Yes** — this is the only path off the original device | Unit: `RecoveryKeyTest`, `VaultKeyManagerTest` |

The double sealing is the point: the wrapped master key is useless without the password *and*
useless off the device. The recovery key is the deliberate exception, and it is the only thing
standing between a broken phone and permanent loss — which is why the create-lock screen states
plainly that nobody can reset it.

## 2. Interrupted imports

`SecureImportEngine.recoverInterruptedImports()` runs once at startup. Its rules, in the order they
matter:

1. **No original file is ever deleted during recovery.** Recovery touches only TrueVault's own
   temporary artefacts.
2. A `.vault.part` whose transaction row is non-terminal → row becomes `FAILED` + retryable, and the
   partial file is removed.
3. A `.vault.part` with no transaction row → unambiguously abandoned, removed.
4. Temporary plaintext left behind by a killed viewer is cleared.

The ordering that makes this safe lives in the import path itself: encrypt → fsync → **verify** →
atomic rename → commit row → *only then* ask about the original. A crash at any point leaves the
original exactly where it was.

**Status: reviewed, not executed as a crash test.** Simulating a process kill between the rename and
the row insert needs an instrumented test that terminates the app mid-import; that test does not
exist yet. Recorded as a gap in [FINAL_TEST_REPORT.md](../FINAL_TEST_REPORT.md).

## 3. Backup format v2 — the defect that made v1 backups unrestorable

**Found and fixed during this verification pass.** Format v1 wrapped each file key with the *source
vault's* master key and stored containers verbatim. Restoring into any new vault — a new phone, a
reinstall, the exact scenario a backup exists for — produced items that could never be opened again.
The data was intact and permanently unreachable, which is the worst possible failure mode for this
product.

Format v2 re-keys on the way out and again on the way in:

| Stage | What happens to the file key |
|---|---|
| Export | unwrapped from the vault master key, re-wrapped with a per-archive key |
| Restore | unwrapped with the archive key, re-wrapped with the **destination** vault's master key |

Containers themselves are copied verbatim — they are already ciphertext, and re-encrypting them
would double the work for no security gain. Both directions stream in 64 KiB chunks; the earlier
`readBytes()` on whole containers was an out-of-memory crash waiting for a large video, and is gone.

Schema v2 adds `wrapped_file_key` to `vault_items` so the row, not just the container header, holds
the authoritative wrapped key — a header copy cannot be re-wrapped for a different vault.

**Executed:** `BackupRekeyingTest` proves an item exported from vault A opens in a *different* vault
B after restore, and that without re-keying it does not. Run with the rest of the unit suite; see
[05-unit-test-report.md](05-unit-test-report.md).

## 4. Database migration 1 → 2

`MIGRATION_1_2` adds the `wrapped_file_key BLOB` column. Both schema JSONs (`1.json`, `2.json`) are
exported to `core/database/src/androidTest/assets/`.

**Executed on an emulator (API 34):** `TrueVaultDatabaseMigrationTest` — 3 tests, all passed. This
also uncovered a real packaging defect: the schemas had been exported to `assets/schemas/`, one
directory deeper than `MigrationTestHelper` resolves from, so the migration test could not have run
at all before it was fixed.

## 5. Container integrity

The `TVLT` v1 container makes the header the AAD for every chunk, and includes the chunk index and
an `isLast` flag in each chunk's AAD. That is what makes reordering, truncation and splicing
detectable rather than merely unlikely.

**Executed** (`SyntheticCorpusRoundTripTest`, `VaultFileCipherTest`, `AesGcmTest`):

- every file in the synthetic corpus round-trips byte for byte, including zero-byte and one-byte files
- identical plaintext under two names produces different ciphertext
- a corrupted container is refused
- decryption with the wrong key throws **and writes zero bytes** to the destination

## 6. Known gaps

| Gap | Why it matters | Status |
|---|---|---|
| Crash-during-import test | Recovery rules are reviewed, not proven under a real process kill | **NOT RUN — TEST NOT WRITTEN** |
| Restore onto a different physical device | The recovery-key path is the only route off a dead phone, and it has never been walked end to end on hardware | **NOT RUN — ENVIRONMENT UNAVAILABLE** (no second device) |
| Biometric enrolment/removal behaviour | Removing a fingerprint must invalidate the biometric key copy without touching the password path | **NOT RUN — ENVIRONMENT UNAVAILABLE** (emulator biometrics do not model enrolment invalidation faithfully) |
| Storage-full during import | The pre-flight space check is unit-tested; the mid-write ENOSPC path is not | **NOT RUN — TEST NOT WRITTEN** |
