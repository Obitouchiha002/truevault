package com.truevault.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
        const val VERSION: Int = 1
        const val NAME: String = "truevault.db"
    }
}

/**
 * Every migration this build knows about.
 *
 * The list is empty at version 1 and grows by one entry per schema change. It exists now, rather
 * than being introduced when the first migration is needed, so that adding one is a one-line change
 * and never a reason to reach for destructive migration under time pressure.
 */
val TRUEVAULT_MIGRATIONS: Array<Migration> = emptyArray()
