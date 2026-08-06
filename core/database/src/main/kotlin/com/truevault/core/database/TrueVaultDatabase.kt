package com.truevault.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.truevault.core.database.dao.ActivityEventDao
import com.truevault.core.database.dao.ImportTransactionDao
import com.truevault.core.database.dao.ScanResultDao
import com.truevault.core.database.dao.VaultItemDao
import com.truevault.core.database.entity.ActivityEventEntity
import com.truevault.core.database.entity.ImportTransactionEntity
import com.truevault.core.database.entity.ScanResultEntity
import com.truevault.core.database.entity.VaultItemEntity

/**
 * The vault database.
 *
 * The database itself is not encrypted at rest as a whole: it lives in app-private storage, and
 * every column that could identify a user's content is individually sealed before it gets here. A
 * whole-file encryption layer on top would add a key that has to be available for every query,
 * including background ones, which weakens rather than strengthens the picture.
 *
 * **Schemas are exported to `core/database/schemas` and checked in.** The migration tests read them,
 * so they are a build output the project depends on, not a convenience.
 *
 * **Destructive migration is never enabled.** A migration that drops the table would silently
 * destroy the user's vault index while leaving the encrypted files orphaned on disk — the worst
 * possible failure for this app.
 */
@Database(
    entities = [
        VaultItemEntity::class,
        ImportTransactionEntity::class,
        ScanResultEntity::class,
        ActivityEventEntity::class,
    ],
    version = TrueVaultDatabase.VERSION,
    exportSchema = true,
)
abstract class TrueVaultDatabase : RoomDatabase() {

    abstract fun vaultItemDao(): VaultItemDao

    abstract fun importTransactionDao(): ImportTransactionDao

    abstract fun scanResultDao(): ScanResultDao

    abstract fun activityEventDao(): ActivityEventDao

    companion object {
        const val VERSION: Int = 3
        const val NAME: String = "truevault.db"
    }
}

/**
 * v1 → v2: the vault item row gains its own copy of the wrapped file key.
 *
 * Before this, the only wrapped file key lived in the container header, sealed by whichever master
 * key was current when the file was written. That made a backup restored into a *different* vault
 * undecryptable: the new vault's master key cannot unwrap the old vault's wrapping. Keeping the
 * wrapped key in the row lets a restore re-wrap it under the destination vault's master key without
 * touching the container — whose header is the associated data for every chunk and therefore cannot
 * be rewritten.
 *
 * The column is nullable and is not backfilled. Existing rows keep working because the read path
 * falls back to the header, which is still correct for items that never left this vault.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_items ADD COLUMN wrapped_file_key BLOB")
    }
}

/**
 * v2 → v3: vault deletion becomes reversible.
 *
 * Deleting an item used to remove the row and its encrypted container in one step. A mis-tap
 * therefore destroyed a file permanently, in an app whose entire job is not losing the user's data.
 * `trashed_at` turns delete into a move: the container stays on disk, the row stays in the table,
 * and the item leaves every list until it is restored or the trash is emptied.
 *
 * Nullable with no backfill, so every existing row is — correctly — not in the trash.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_items ADD COLUMN trashed_at INTEGER")
    }
}

/**
 * Every migration this build knows about.
 *
 * Destructive migration is never enabled: dropping the table would destroy the vault index while
 * leaving the encrypted files orphaned on disk.
 */
val TRUEVAULT_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
