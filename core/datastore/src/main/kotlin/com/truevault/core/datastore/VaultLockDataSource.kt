package com.truevault.core.datastore

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.truevault.core.common.log.SecureLog
import com.truevault.core.crypto.vault.VaultLockRecord
import com.truevault.core.crypto.vault.VaultLockStore
import com.truevault.core.datastore.di.VaultLockPreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val TAG = "VaultLockStore"

/**
 * Persists the vault lock record.
 *
 * Everything stored here is already sealed by [com.truevault.core.crypto.vault.VaultKeyManager] —
 * this class handles bytes it cannot read. It lives in its own DataStore file, separate from user
 * preferences, so clearing settings can never clear the lock and vice versa.
 */
@Singleton
class VaultLockDataSource @Inject constructor(
    @param:VaultLockPreferences private val dataStore: DataStore<Preferences>,
) : VaultLockStore {

    override val record: Flow<VaultLockRecord?> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                // Refuse to guess. An unreadable lock file is reported as "no record", which sends
                // the user to the unlock screen rather than silently creating a second vault.
                SecureLog.e(TAG, "Lock record unreadable")
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { it.toRecord() }

    override suspend fun read(): VaultLockRecord? = record.first()

    override suspend fun write(record: VaultLockRecord) {
        dataStore.edit { prefs ->
            prefs[Keys.KDF_VERSION] = record.kdfVersion
            prefs[Keys.SALT] = record.salt.encode()
            prefs[Keys.SEALED_MASTER_KEY] = record.sealedMasterKey.encode()
            record.biometricSealedMasterKey
                ?.let { prefs[Keys.BIOMETRIC_SEALED_MASTER_KEY] = it.encode() }
                ?: prefs.remove(Keys.BIOMETRIC_SEALED_MASTER_KEY)
            prefs[Keys.CREATED_AT] = record.createdAtMillis
            prefs[Keys.UPDATED_AT] = record.updatedAtMillis
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toRecord(): VaultLockRecord? {
        val salt = this[Keys.SALT]?.decode() ?: return null
        val sealed = this[Keys.SEALED_MASTER_KEY]?.decode() ?: return null
        val version = this[Keys.KDF_VERSION] ?: return null

        return VaultLockRecord(
            kdfVersion = version,
            salt = salt,
            sealedMasterKey = sealed,
            biometricSealedMasterKey = this[Keys.BIOMETRIC_SEALED_MASTER_KEY]?.decode(),
            createdAtMillis = this[Keys.CREATED_AT] ?: 0L,
            updatedAtMillis = this[Keys.UPDATED_AT] ?: 0L,
        )
    }

    private object Keys {
        val KDF_VERSION = intPreferencesKey("kdf_version")
        val SALT = stringPreferencesKey("kdf_salt")
        val SEALED_MASTER_KEY = stringPreferencesKey("sealed_master_key")
        val BIOMETRIC_SEALED_MASTER_KEY = stringPreferencesKey("biometric_sealed_master_key")
        val CREATED_AT = longPreferencesKey("created_at")
        val UPDATED_AT = longPreferencesKey("updated_at")
    }
}

private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)

private fun String.decode(): ByteArray? =
    runCatching { Base64.decode(this, Base64.NO_WRAP) }.getOrNull()
