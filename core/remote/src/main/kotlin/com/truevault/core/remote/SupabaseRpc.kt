package com.truevault.core.remote

import com.truevault.core.common.log.SecureLog
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val TAG = "SupabaseRpc"
private const val CONNECT_TIMEOUT_MS = 8_000
private const val READ_TIMEOUT_MS = 8_000

/**
 * A minimal PostgREST RPC caller.
 *
 * It can only do one thing: POST a JSON object to `/rest/v1/rpc/<function>` and hand back the JSON
 * that comes out. That narrowness is the point — there is no way to express "read this table" with
 * this client, so a future change cannot quietly widen what the app talks to. The database side
 * enforces the same boundary independently: RLS is on with no policies, so the anon key can only
 * reach the five granted functions.
 *
 * `HttpURLConnection` rather than a library, because this is the app's only network call and an
 * HTTP stack would be a large dependency for it.
 */
@Singleton
class SupabaseRpc @Inject constructor(
    private val config: RemoteConfig,
) {
    /** Shared so callers decode responses with the same leniency this client encodes with. */
    val json = Json {
        ignoreUnknownKeys = true      // the backend may gain columns; old builds must not break
        encodeDefaults = true
    }

    /**
     * @return [RemoteResult.Unreachable] for anything that looks like a network problem, so callers
     * can tell "the server said no" from "we could not ask", and treat the two differently.
     */
    fun call(function: String, arguments: JsonObject): RemoteResult<JsonElement> {
        if (!config.isEnabled) return RemoteResult.Unreachable

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("${config.url}/rest/v1/rpc/$function").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", config.anonKey)
                setRequestProperty("Authorization", "Bearer ${config.anonKey}")
                // Ask PostgREST to unwrap a single-row result so the caller does not have to know
                // whether a function returns a row or a set.
                setRequestProperty("Accept", "application/json")
            }

            connection.outputStream.use { it.write(arguments.toString().toByteArray()) }

            val status = connection.responseCode
            if (status !in 200..299) {
                // The error body is discarded rather than logged: a PostgREST failure echoes the
                // request, which would put the install name and identifier into logcat.
                SecureLog.w(TAG, "RPC $function refused ($status)")
                return RemoteResult.Refused(status)
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            RemoteResult.Ok(json.parseToJsonElement(body))
        } catch (e: IOException) {
            SecureLog.w(TAG, "RPC $function unreachable (${e.javaClass.simpleName})")
            RemoteResult.Unreachable
        } catch (e: SecurityException) {
            // No INTERNET permission in the merged manifest — the offline build. Not an error.
            SecureLog.w(TAG, "RPC $function blocked by permission (${e.javaClass.simpleName})")
            RemoteResult.Unreachable
        } finally {
            connection?.disconnect()
        }
    }
}

/** Where the backend lives, and whether there is one at all. */
@Singleton
class RemoteConfig @Inject constructor() {
    val url: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY

    /**
     * False in every build made without `supabase.properties`, which is the default and the one
     * the offline privacy claims describe. Nothing in this module runs when this is false.
     */
    val isEnabled: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()
}
