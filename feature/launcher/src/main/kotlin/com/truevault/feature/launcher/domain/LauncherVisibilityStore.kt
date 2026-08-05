package com.truevault.feature.launcher.domain

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.truevault.core.datastore.di.VaultLockPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val HIDDEN_PACKAGES = stringSetPreferencesKey("launcher_hidden_packages")
private val VISIBILITY_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey(
    "launcher_visibility_enabled",
)

/**
 * Which apps are hidden from TrueVault's own launcher screen.
 *
 * This is **Launcher Visibility**, and the name matters. It removes icons from one launcher's grid.
 * It is not app isolation, not a container, and not a vault — the apps stay installed and still
 * appear in Settings, system search, notifications, share sheets and any other launcher.
 *
 * Stored in the vault's own preferences file in `noBackupFilesDir`, so the list of apps a user
 * chose to hide never leaves the device in a backup.
 */
@Singleton
class LauncherVisibilityStore @Inject constructor(
    @param:VaultLockPreferences private val dataStore: DataStore<Preferences>,
) {

    val isEnabled: Flow<Boolean> = dataStore.data.map { it[VISIBILITY_ENABLED] ?: false }

    val hiddenPackages: Flow<Set<String>> = dataStore.data.map { it[HIDDEN_PACKAGES].orEmpty() }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[VISIBILITY_ENABLED] = enabled
            // Turning the feature off restores every icon immediately. There is no state where the
            // feature is off and icons stay hidden with no way to find them.
            if (!enabled) prefs.remove(HIDDEN_PACKAGES)
        }
    }

    suspend fun setHidden(packageName: String, hidden: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[HIDDEN_PACKAGES].orEmpty()
            prefs[HIDDEN_PACKAGES] = if (hidden) current + packageName else current - packageName
        }
    }

    /** The emergency restore. Always reachable from Advanced Privacy, even with the grid empty. */
    suspend fun restoreAll() {
        dataStore.edit { it.remove(HIDDEN_PACKAGES) }
    }

    suspend fun hiddenCount(): Int = hiddenPackages.first().size
}
