package com.truevault.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.truevault.core.common.dispatcher.ApplicationScope
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus

private const val PREFERENCES_FILE = "truevault_preferences.preferences_pb"
private const val VAULT_LOCK_FILE = "truevault_lock.preferences_pb"

/** Distinguishes the lock-record store from the user-preferences store. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class VaultLockPreferences

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    /**
     * Preferences live in `noBackupFilesDir`.
     *
     * Android's automatic backup is disabled for this app, but placing the file here as well means
     * that if backup is ever enabled for an explicitly-designed encrypted export, these settings
     * still do not leave the device by accident.
     */
    @Provides
    @Singleton
    fun providesUserPreferencesDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(TrueVaultDispatcher.IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope.plus(ioDispatcher),
        produceFile = { File(context.noBackupFilesDir, PREFERENCES_FILE) },
    )

    /**
     * The lock record lives in its own file, in `noBackupFilesDir`.
     *
     * Keeping it out of the backup set is not optional: the sealed master key is bound to a Keystore
     * key that cannot be backed up, so a restored copy could never be opened. Shipping it anyway
     * would hand the user a backup that looks complete and is not.
     */
    @Provides
    @Singleton
    @VaultLockPreferences
    fun providesVaultLockDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(TrueVaultDispatcher.IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope.plus(ioDispatcher),
        produceFile = { File(context.noBackupFilesDir, VAULT_LOCK_FILE) },
    )
}
