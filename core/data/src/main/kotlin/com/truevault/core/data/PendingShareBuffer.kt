package com.truevault.core.data

import com.truevault.core.common.log.SecureLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "Share"

/**
 * Files handed to TrueVault from another app, waiting for the vault to be unlocked.
 *
 * Someone sharing a photo from their gallery arrives at a locked app. The URIs cannot be dropped —
 * that would make the share silently do nothing — and they cannot be acted on either, because
 * importing requires the vault key. They wait here until the user unlocks, and then the import flow
 * picks them up.
 *
 * In memory only, and deliberately so. Persisting them would write a list of the user's file
 * locations to disk outside the encrypted vault, which is the opposite of what this app is for. If
 * the process dies before the user unlocks, the share is lost — and losing a share is a far smaller
 * harm than leaving a plaintext record of what someone was about to hide.
 *
 * The grants behind these URIs are process-scoped too, so a persisted list would often be
 * unreadable by the time it was used.
 */
@Singleton
class PendingShareBuffer @Inject constructor() {

    private val _pending = MutableStateFlow<List<String>>(emptyList())

    /** Non-empty when a share is waiting. The navigation layer observes this. */
    val pending: StateFlow<List<String>> = _pending.asStateFlow()

    /**
     * Called from the Activity when a share arrives.
     *
     * Replaces rather than appends: a second share before the first was handled means the user
     * changed their mind, and silently importing both would take in files they no longer chose.
     */
    fun offer(uriTokens: List<String>) {
        val cleaned = uriTokens.filter { it.isNotBlank() }.distinct()
        if (cleaned.isEmpty()) {
            SecureLog.w(TAG, "Share arrived with no readable items")
            return
        }

        SecureLog.i(TAG, "Share received (${cleaned.size} item(s))")
        _pending.value = cleaned
    }

    /** Called once the import flow has taken them. Clearing is the caller's responsibility. */
    fun consume(): List<String> {
        val current = _pending.value
        _pending.value = emptyList()
        return current
    }

    /** The user backed out of the share without importing. */
    fun clear() {
        _pending.value = emptyList()
    }
}
