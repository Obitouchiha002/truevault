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

    /**
     * The Android ID: stable across reinstalls, cleared by a factory reset, and scoped per app and
     * per user since Android 8. Used only as an opaque handle for blocking. Nothing else about the
     * device is read — no model, no manufacturer, no serial.
     */
    @Suppress("HardwareIds")
    private val installId: String
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"

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
                put("p_id", JsonPrimitive(installId))
                put("p_name", JsonPrimitive(name))
                put("p_version", JsonPrimitive(version))
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

    // ---- admin ------------------------------------------------------------------------------

    suspend fun adminInstalls(pin: String): RemoteResult<List<InstallRecord>> = withContext(Dispatchers.IO) {
        when (val r = rpc.call("admin_installs", buildJsonObject { put("p_pin", JsonPrimitive(pin)) })) {
            is RemoteResult.Ok -> RemoteResult.Ok(
                (r.value as? JsonArray)?.map { rpc.json.decodeFromJsonElement(InstallRecord.serializer(), it) }
                    ?: emptyList(),
            )
            is RemoteResult.Refused -> r
            RemoteResult.Unreachable -> RemoteResult.Unreachable
        }
    }

    suspend fun adminBlock(
        pin: String,
        id: String,
        blocked: Boolean,
        reason: String?,
        minutes: Int?,
        code: String?,
    ): RemoteResult<Unit> = withContext(Dispatchers.IO) {
        rpc.call(
            "admin_block",
            buildJsonObject {
                put("p_pin", JsonPrimitive(pin))
                put("p_id", JsonPrimitive(id))
                put("p_blocked", JsonPrimitive(blocked))
                put("p_reason", JsonPrimitive(reason))
                put("p_minutes", JsonPrimitive(minutes))
                put("p_code", JsonPrimitive(code))
            },
        ).unit()
    }

    suspend fun adminPremium(pin: String, id: String, premium: Boolean): RemoteResult<Unit> =
        withContext(Dispatchers.IO) {
            rpc.call(
                "admin_premium",
                buildJsonObject {
                    put("p_pin", JsonPrimitive(pin))
                    put("p_id", JsonPrimitive(id))
                    put("p_premium", JsonPrimitive(premium))
                },
            ).unit()
        }

    suspend fun adminConfig(
        pin: String,
        kill: Boolean,
        latest: String?,
        url: String?,
        note: String?,
    ): RemoteResult<Unit> = withContext(Dispatchers.IO) {
        rpc.call(
            "admin_config",
            buildJsonObject {
                put("p_pin", JsonPrimitive(pin))
                put("p_kill", JsonPrimitive(kill))
                put("p_latest", JsonPrimitive(latest))
                put("p_url", JsonPrimitive(url))
                put("p_note", JsonPrimitive(note))
            },
        ).unit()
    }

    private fun RemoteResult<*>.unit(): RemoteResult<Unit> = when (this) {
        is RemoteResult.Ok -> RemoteResult.Ok(Unit)
        is RemoteResult.Refused -> this
        RemoteResult.Unreachable -> RemoteResult.Unreachable
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
