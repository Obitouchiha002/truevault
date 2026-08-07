package com.truevault.core.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the backend says about this install.
 *
 * Every field has a safe default, and the defaults are what an install gets when the backend has
 * never been reached: not blocked, not premium, no update notice. That direction matters — the
 * failure mode of a network problem must be "the app works", never "the user is locked out of their
 * own encrypted files".
 */
@Serializable
data class InstallStatus(
    val blocked: Boolean = false,
    val reason: String? = null,
    val code: String? = null,
    val until: String? = null,
    val premium: Boolean = false,
    @SerialName("latest_version") val latestVersion: String? = null,
    @SerialName("update_url") val updateUrl: String? = null,
    @SerialName("update_note") val updateNote: String? = null,
) {
    companion object {
        /** The state of an install that has never successfully checked in. */
        val Unknown = InstallStatus()
    }
}

/** One row of the admin list. */
@Serializable
data class InstallRecord(
    val id: String,
    val name: String? = null,
    val version: String? = null,
    val blocked: Boolean = false,
    @SerialName("block_reason") val blockReason: String? = null,
    @SerialName("block_code") val blockCode: String? = null,
    @SerialName("blocked_until") val blockedUntil: String? = null,
    val premium: Boolean = false,
    @SerialName("first_seen") val firstSeen: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
)

/** Why a call did not produce a status. Kept separate from [InstallStatus] so the two never blur. */
sealed interface RemoteResult<out T> {
    data class Ok<T>(val value: T) : RemoteResult<T>

    /** Network down, DNS failure, timeout. The caller must carry on as if nothing happened. */
    data object Unreachable : RemoteResult<Nothing>

    /** The backend answered and refused — a wrong admin PIN, or a function that does not exist. */
    data class Refused(val statusCode: Int) : RemoteResult<Nothing>
}
