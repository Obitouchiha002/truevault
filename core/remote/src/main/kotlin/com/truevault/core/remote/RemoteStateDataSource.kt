package com.truevault.core.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.truevault.core.common.log.SecureLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private const val TAG = "RemoteState"

/**
 * The name the user typed and the last status the backend gave.
 *
 * Its own preferences file, separate from the vault's. Nothing here is a secret — a name and a
 * boolean — but keeping it out of the vault's store means the gate can be read before any unlock,
 * which is exactly when it is needed, without opening anything that holds key material.
 */
private val Context.remoteStateStore: DataStore<Preferences> by preferencesDataStore(name = "remote_state")

@Singleton
class RemoteStateDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.remoteStateStore

    suspend fun displayName(): String = read(Keys.NAME).orEmpty()

    suspend fun setDisplayName(name: String) = write { it[Keys.NAME] = name }

    /**
     * The last answer the backend actually gave. Restored before the first network call so a
     * blocked install stays blocked offline — fail-open covers what we do not know, not what we do.
     */
    suspend fun cachedStatus(): InstallStatus {
        val prefs = runCatching { store.data.catch { emptyPrefs() }.first() }.getOrNull()
            ?: return InstallStatus.Unknown
        return InstallStatus(
            blocked = prefs[Keys.BLOCKED] ?: false,
            reason = prefs[Keys.REASON],
            code = prefs[Keys.CODE],
            until = prefs[Keys.UNTIL],
            premium = prefs[Keys.PREMIUM] ?: false,
        )
    }

    suspend fun cacheStatus(status: InstallStatus) = write { prefs ->
        prefs[Keys.BLOCKED] = status.blocked
        prefs[Keys.PREMIUM] = status.premium
        status.reason?.let { prefs[Keys.REASON] = it } ?: prefs.remove(Keys.REASON)
        status.code?.let { prefs[Keys.CODE] = it } ?: prefs.remove(Keys.CODE)
        status.until?.let { prefs[Keys.UNTIL] = it } ?: prefs.remove(Keys.UNTIL)
    }

    /** Called by the delete-everything flow. */
    suspend fun clear() = write { it.clear() }

    private suspend fun read(key: Preferences.Key<String>): String? =
        runCatching { store.data.first()[key] }.getOrNull()

    private suspend fun write(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            store.edit(block)
        } catch (e: IOException) {
            SecureLog.w(TAG, "Could not persist remote state (${e.javaClass.simpleName})")
        }
    }

    private fun emptyPrefs() = androidx.datastore.preferences.core.emptyPreferences()

    private object Keys {
        val NAME = stringPreferencesKey("display_name")
        val BLOCKED = booleanPreferencesKey("blocked")
        val PREMIUM = booleanPreferencesKey("premium")
        val REASON = stringPreferencesKey("block_reason")
        val CODE = stringPreferencesKey("block_code")
        val UNTIL = stringPreferencesKey("blocked_until")
    }
}
