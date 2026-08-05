# Database schema and migration strategy

Room, one database, `truevault.db`, in `noBackupFilesDir` alongside the encrypted vault files.

**Current version: 1.** Schemas are exported to
`core/database/src/androidTest/assets/schemas/` and checked in.

## What is plaintext and what is not

| Kind of column | Examples | Why |
|----------------|----------|-----|
| Plaintext | ids, sizes, timestamps, category, status enums, format versions | None identifies content, and all of them are needed to sort, filter and page in SQL instead of decrypting the whole vault |
| Sealed blob | display name, MIME type, original URI, matched URI | A file name alone can be the entire secret |
| Keyed fingerprint | `content_fingerprint` | HMAC-SHA256 of the file's SHA-256 under a vault-only key: equality still works for duplicate detection, but the value cannot be matched against a public corpus of known files |

The database file is not separately encrypted as a whole. It lives in app-private storage and every
identifying column is individually sealed; a whole-file layer would add a key that must be available
to every query, including background ones, which weakens rather than strengthens the picture.

## Tables

### `vault_items`

| Column | Type | Notes |
|--------|------|-------|
| `id` | TEXT PK | Random UUID. Not derived from the name or URI — it appears in on-disk file names |
| `file_relative_path` | TEXT | Relative to the vault items directory, never absolute |
| `thumbnail_relative_path` | TEXT? | Null when there is no thumbnail |
| `encrypted_metadata` | BLOB | Sealed `VaultItemMetadata` JSON |
| `mime_category` | TEXT | `PHOTO`/`VIDEO`/`DOCUMENT`/`AUDIO`/`ARCHIVE`/`OTHER` |
| `encrypted_size`, `original_size` | INTEGER | |
| `created_at`, `updated_at` | INTEGER | Epoch millis |
| `import_mode` | TEXT | `SECURE_COPY` / `SECURE_MOVE` |
| `privacy_status` | TEXT | The seven statuses |
| `verification_status` | TEXT | `NOT_VERIFIED` / `VERIFIED` / `FAILED` |
| `key_version`, `file_format_version` | INTEGER | Upgrade paths |
| `original_deletion_state` | TEXT | Five states, observed never assumed |
| `content_fingerprint` | BLOB? | Keyed HMAC |
| `last_integrity_check_at` | INTEGER? | |

Indices: `created_at`, `mime_category`, `privacy_status`, `content_fingerprint`, `original_size` —
one per column the list screen actually orders or filters by.

### `import_transactions`

Written **before** any bytes are encrypted and updated as the transaction advances, so a process
killed at any point leaves a record of how far it got. This is what makes Secure Move recoverable
rather than leaving orphaned `.part` files nobody can explain.

States: `PENDING → VALIDATED → ENCRYPTING → VERIFYING → COMMITTED`, or terminal `FAILED` /
`CANCELLED`. `failure_code` holds a stable code, never a raw exception message.

### `scan_results`

Foreign key to `vault_items` with `ON DELETE CASCADE`: a finding about an item that no longer exists
is noise, and leaving it would let the privacy score keep counting a problem the user already solved.

### `activity_events`

Deliberately has no column for a file name, a path or a URI. Activity is the screen a user is most
likely to have visible while someone else is looking. Trimmed to the most recent 100 entries.

## Migration strategy

1. **Destructive migration is never enabled.** Dropping the table would destroy the vault index
   while leaving the encrypted files orphaned on disk — the worst possible failure for this app.
2. Every schema change adds one entry to `TRUEVAULT_MIGRATIONS`, which exists (empty) from version 1
   so that adding one is a one-line change and never a reason to reach for destructive migration
   under pressure.
3. `TrueVaultDatabaseMigrationTest` runs the full path with `validateMigration = true`, so a
   forgotten migration is a failing test rather than a crash on a user's device.
4. Schemas are checked in with the migration that introduced them.

### Adding a migration

```kotlin
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_items ADD COLUMN new_column TEXT")
    }
}

val TRUEVAULT_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
```

Then bump `TrueVaultDatabase.VERSION`, build once to export `2.json`, commit both, and add a test
that seeds v1 data and asserts it survives.
