package com.truevault.core.remote

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The app's one relationship with a server.
 *
 * Three rules hold this together, and each exists to stop a specific failure:
 *
 *  1. **It fails open.** A network error leaves the last *known* status in place and never invents a
 *     block. Someone on a plane, on a train, or with a dead backend still opens their own vault.
 *  2. **A known block survives offline.** The last successful answer is cached, so switching to
 *     aeroplane mode does not lift a block. Fail-open applies to the unknown, not to the decided.
 *  3. **It sends four things and nothing else**: the install identifier, the name the user typed,
 *     the app version, and implicitly the time. Nothing about the vault — not its size, not its
 *     contents, not whether one even exists — is ever transmitted, and there is no code path here
 *     that could.
 */
@Singleton
class RemoteGateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rpc: SupabaseRpc,
    private val config: RemoteConfig,
    private val store: RemoteStateDataSource,
) {
    private val _status = MutableStateFlow(InstallStatus.Unknown)
    val status: Flow<InstallStatus> = _status.asStateFlow()

    val isEnabled: Boolean get() = config.isEnabled

    /** The decision as it stands right now — cached if that is all we have. */
    fun currentStatus(): InstallStatus = _status.value

    /**
     * The handle a block is attached to, and the reason a ban survives a reinstall.
     *
     * `ANDROID_ID` is the right primitive here: since Android 8 it is scoped to the app's signing
     * key, the user and the device, and it is **not** regenerated when the app is uninstalled and
     * installed again. Only a factory reset changes it. So a blocked install that reinstalls checks
     * in under the same id and is still blocked — which is the whole requirement, and it needs no
     * permission and reveals nothing about the device.
     *
     * Two failure cases it does have, both handled:
     *
     *  - It can be null, and a handful of devices ship the notorious `9774d56d682e549c`, which is
     *    the same on every one of them. The previous fallback was the literal string "unknown", so
     *    every such device shared one row: two different people appeared as one install, and
     *    blocking either blocked all of them. That is worse than not blocking at all.
     *  - The replacement is a random id generated once and stored. Be clear about the cost: that
     *    one does not survive an uninstall, so on those few devices a ban can be shed by
     *    reinstalling. It is the best available without a permission, and it affects only the
     *    devices where the platform's own identifier is broken.
     */
    @Suppress("HardwareIds")
    private suspend fun installId(): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

        if (!androidId.isNullOrBlank() && androidId != KNOWN_BROKEN_ANDROID_ID) return androidId

        return store.fallbackInstallId()
    }

    /** Loads the last cached answer so the gate has something to say before any network call. */
    suspend fun restore() {
        if (!config.isEnabled) return
        _status.value = store.cachedStatus()
    }

    suspend fun checkIn(version: String): InstallStatus = withContext(Dispatchers.IO) {
        if (!config.isEnabled) return@withContext InstallStatus.Unknown

        val name = store.displayName()
        val result = rpc.call(
            function = "checkin",
            arguments = buildJsonObject {
                put("p_id", JsonPrimitive(installId()))
                put("p_name", JsonPrimitive(name))
                put("p_version", JsonPrimitive(version))
                // StreamGarden's `checkin` takes a platform, and its table is shared with this app.
                // This value is what separates the two in one list: StreamGarden sends "android",
                // so anything tagged here is unmistakably a TrueVault install.
                put("p_platform", JsonPrimitive(PLATFORM))
            },
        )

        val fresh = when (result) {
            // Unreachable and Refused both mean "we learned nothing new". Keeping the cached value
            // is the whole fail-open guarantee; overwriting it with a default here would silently
            // unblock every blocked install the moment the backend hiccuped.
            is RemoteResult.Unreachable, is RemoteResult.Refused -> return@withContext _status.value
            is RemoteResult.Ok -> result.value.firstRowAs<InstallStatus>() ?: return@withContext _status.value
        }

        store.cacheStatus(fresh)
        _status.value = fresh
        fresh
    }

    suspend fun setDisplayName(name: String) = store.setDisplayName(name.trim().take(40))

    suspend fun displayName(): String = store.displayName()

    suspend fun hasDisplayName(): Boolean = store.displayName().isNotBlank()

    /** False on a first launch and after a reinstall, because app-private storage is wiped. */
    suspend fun hasEverCheckedIn(): Boolean = store.hasEverCheckedIn()

    /**
     * Deliberately absent: there are no admin calls here any more.
     *
     * Admin moved to the website, where the credential is a service_role key that never leaves the
     * server. Leaving these methods in would keep the function names, the argument shapes and the
     * existence of a PIN inside an APK anyone can decompile — and would keep those functions
     * granted to the public anon key, which is what made the PIN brute-forceable in the first place.
     */
    private companion object {
        /**
         * Tags this app's rows in a table it may share with StreamGarden. Changing it orphans every
         * existing row from this app's point of view, so it is a constant rather than a setting.
         */
        const val PLATFORM = "truevault-android"

        /** Shipped identically on a number of devices, so it identifies a model, not an install. */
        const val KNOWN_BROKEN_ANDROID_ID = "9774d56d682e549c"
    }

    /** `checkin` returns a one-row set, so PostgREST sends an array of one object. */
    private inline fun <reified T> kotlinx.serialization.json.JsonElement.firstRowAs(): T? {
        val obj = when (this) {
            is JsonArray -> firstOrNull() as? JsonObject
            is JsonObject -> this
            else -> null
        } ?: return null
        return runCatching { rpc.json.decodeFromJsonElement<T>(obj) }.getOrNull()
    }
}
