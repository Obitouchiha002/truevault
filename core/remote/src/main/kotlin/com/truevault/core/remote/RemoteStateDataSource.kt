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
        prefs[Keys.EVER_CHECKED_IN] = true
        prefs[Keys.BLOCKED] = status.blocked
        prefs[Keys.PREMIUM] = status.premium
        status.reason?.let { prefs[Keys.REASON] = it } ?: prefs.remove(Keys.REASON)
        status.code?.let { prefs[Keys.CODE] = it } ?: prefs.remove(Keys.CODE)
        status.until?.let { prefs[Keys.UNTIL] = it } ?: prefs.remove(Keys.UNTIL)
    }

    /**
     * A stable id for the few devices whose `ANDROID_ID` is null or the known-duplicate value.
     *
     * Generated once and kept. It does not survive an uninstall — nothing an app can store without
     * a permission does — so on those devices a ban can be shed by reinstalling. That is a real
     * gap and it is stated rather than papered over; what it replaces was worse, because every such
     * device shared a single row.
     */
    suspend fun fallbackInstallId(): String {
        read(Keys.FALLBACK_ID)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = "fb-" + java.util.UUID.randomUUID().toString()
        write { it[Keys.FALLBACK_ID] = generated }
        return generated
    }

    /**
     * Whether this install has ever had an answer from the backend.
     *
     * A fresh install has no cached decision, so "no decision" and "not blocked" look identical.
     * They are not: the first is unknown, the second is known. This tells them apart.
     */
    suspend fun hasEverCheckedIn(): Boolean =
        runCatching { store.data.first()[Keys.EVER_CHECKED_IN] }.getOrNull() == true

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
        val FALLBACK_ID = stringPreferencesKey("fallback_install_id")
        val EVER_CHECKED_IN = booleanPreferencesKey("ever_checked_in")
        val BLOCKED = booleanPreferencesKey("blocked")
        val PREMIUM = booleanPreferencesKey("premium")
        val REASON = stringPreferencesKey("block_reason")
        val CODE = stringPreferencesKey("block_code")
        val UNTIL = stringPreferencesKey("blocked_until")
    }
}
