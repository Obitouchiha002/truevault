package com.truevault.core.database.di

import android.content.Context
import androidx.room.Room
import com.truevault.core.database.TRUEVAULT_MIGRATIONS
import com.truevault.core.database.TrueVaultDatabase
import com.truevault.core.database.dao.ActivityEventDao
import com.truevault.core.database.dao.ImportTransactionDao
import com.truevault.core.database.dao.ScanResultDao
import com.truevault.core.database.dao.VaultItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * The database file lives in `noBackupFilesDir`, next to the encrypted items it indexes.
     *
     * Keeping them together matters: a backup that captured the index without the files, or the
     * files without the index, would restore a vault that cannot be opened either way.
     */
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): TrueVaultDatabase =
        Room.databaseBuilder(
            context,
            TrueVaultDatabase::class.java,
            File(context.noBackupFilesDir, TrueVaultDatabase.NAME).absolutePath,
        )
            .addMigrations(*TRUEVAULT_MIGRATIONS)
            // Foreign keys are what cascade scan findings away when their item is deleted.
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides
    fun providesVaultItemDao(database: TrueVaultDatabase): VaultItemDao = database.vaultItemDao()

    @Provides
    fun providesImportTransactionDao(database: TrueVaultDatabase): ImportTransactionDao =
        database.importTransactionDao()

    @Provides
    fun providesScanResultDao(database: TrueVaultDatabase): ScanResultDao = database.scanResultDao()

    @Provides
    fun providesActivityEventDao(database: TrueVaultDatabase): ActivityEventDao =
        database.activityEventDao()
}
