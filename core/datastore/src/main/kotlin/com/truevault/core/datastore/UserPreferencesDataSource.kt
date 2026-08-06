package com.truevault.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.truevault.core.common.log.SecureLog
import com.truevault.core.model.AutoLockDuration
import com.truevault.core.model.ImportModePreference
import com.truevault.core.model.StorageBudget
import com.truevault.core.model.ThemePreference
import com.truevault.core.model.VaultLayout
import com.truevault.core.model.VaultSortOrder
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val TAG = "UserPreferences"

@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { throwable ->
            // A corrupt or unreadable preferences file must not stop the app from starting; the
            // user falls back to defaults rather than to a crash loop.
            if (throwable is IOException) {
                SecureLog.w(TAG, "Preferences unreadable, falling back to defaults")
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs -> prefs.toUserPreferences() }

    suspend fun setTheme(theme: ThemePreference) = edit { it[Keys.THEME] = theme.name }

    suspend fun setUseDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setOnboardingCompleted(completed: Boolean) =
        edit { it[Keys.ONBOARDING_COMPLETED] = completed }

    suspend fun setAutoLockDuration(duration: AutoLockDuration) =
        edit { it[Keys.AUTO_LOCK] = duration.name }

    suspend fun setLockOnScreenOff(enabled: Boolean) = edit { it[Keys.LOCK_ON_SCREEN_OFF] = enabled }

    suspend fun setBlockScreenshots(enabled: Boolean) = edit { it[Keys.BLOCK_SCREENSHOTS] = enabled }

    suspend fun setBiometricUnlockEnabled(enabled: Boolean) =
        edit { it[Keys.BIOMETRIC_UNLOCK] = enabled }

    suspend fun setImportModePreference(preference: ImportModePreference) =
        edit { it[Keys.IMPORT_MODE] = preference.name }

    suspend fun setVaultLayout(layout: VaultLayout) = edit { it[Keys.VAULT_LAYOUT] = layout.name }

    suspend fun setStorageBudget(budget: StorageBudget) =
        edit { it[Keys.STORAGE_BUDGET] = budget.name }

    suspend fun setVaultSortOrder(order: VaultSortOrder) = edit { it[Keys.VAULT_SORT] = order.name }

    suspend fun setRecoveryKeyConfigured(configured: Boolean) =
        edit { it[Keys.RECOVERY_KEY_CONFIGURED] = configured }

    suspend fun setLastBackupAt(timestampMillis: Long) =
        edit { it[Keys.LAST_BACKUP_AT] = timestampMillis }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (e: IOException) {
            SecureLog.e(TAG, "Failed to persist a preference", e)
        }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val AUTO_LOCK = stringPreferencesKey("auto_lock_duration")
        val LOCK_ON_SCREEN_OFF = booleanPreferencesKey("lock_on_screen_off")
        val BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
        val BIOMETRIC_UNLOCK = booleanPreferencesKey("biometric_unlock")
        val IMPORT_MODE = stringPreferencesKey("import_mode_preference")
        val VAULT_LAYOUT = stringPreferencesKey("vault_layout")
        val STORAGE_BUDGET = stringPreferencesKey("storage_budget")
        val VAULT_SORT = stringPreferencesKey("vault_sort_order")
        val RECOVERY_KEY_CONFIGURED = booleanPreferencesKey("recovery_key_configured")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }

    private fun Preferences.toUserPreferences() = UserPreferences(
        theme = enumOrDefault(this[Keys.THEME], ThemePreference.SYSTEM),
        useDynamicColor = this[Keys.DYNAMIC_COLOR] ?: false,
        hasCompletedOnboarding = this[Keys.ONBOARDING_COMPLETED] ?: false,
        autoLockDuration = enumOrDefault(this[Keys.AUTO_LOCK], AutoLockDuration.IMMEDIATE),
        lockOnScreenOff = this[Keys.LOCK_ON_SCREEN_OFF] ?: true,
        blockScreenshots = this[Keys.BLOCK_SCREENSHOTS] ?: true,
        biometricUnlockEnabled = this[Keys.BIOMETRIC_UNLOCK] ?: false,
        importModePreference = enumOrDefault(this[Keys.IMPORT_MODE], ImportModePreference.ALWAYS_ASK),
        vaultLayout = enumOrDefault(this[Keys.VAULT_LAYOUT], VaultLayout.GRID),
        storageBudget = StorageBudget.fromName(this[Keys.STORAGE_BUDGET]),
        vaultSortOrder = enumOrDefault(this[Keys.VAULT_SORT], VaultSortOrder.DATE_ADDED_DESC),
        recoveryKeyConfigured = this[Keys.RECOVERY_KEY_CONFIGURED] ?: false,
        lastBackupAtMillis = this[Keys.LAST_BACKUP_AT],
    )
}

/** Unknown or removed enum names fall back to the default instead of crashing on a downgrade. */
private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    name?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default
