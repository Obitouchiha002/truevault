package com.truevault.core.crypto.session

import com.truevault.core.common.dispatcher.ApplicationScope
import com.truevault.core.common.log.SecureLog
import com.truevault.core.common.time.TimeProvider
import com.truevault.core.model.AutoLockDuration
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "Session"

/** What the rest of the app is allowed to know about the lock. Never the key itself. */
sealed interface VaultLockState {
    /** No vault exists yet; the user has not created a lock. */
    data object NotConfigured : VaultLockState

    data object Locked : VaultLockState

    data object Unlocked : VaultLockState
}

/**
 * The authenticated session.
 *
 * The vault master key lives here, in memory, and nowhere else. It is not written to DataStore, not
 * cached in a file, and not held by any ViewModel — a screen asks this object for state, never for
 * the key.
 *
 * Authentication is deliberately *not* a boolean in DataStore. A persisted flag survives a reboot
 * and a process kill, which turns "unlocked" into a permanent property of the install rather than a
 * property of the last few minutes.
 *
 * All timeout arithmetic uses monotonic elapsed-realtime, so moving the device clock forward or
 * backward cannot extend a session.
 */
@Singleton
class VaultSession @Inject constructor(
    private val timeProvider: TimeProvider,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {

    private val _state = MutableStateFlow<VaultLockState>(VaultLockState.NotConfigured)
    val state: StateFlow<VaultLockState> = _state.asStateFlow()

    @Volatile
    private var masterKey: SecretKey? = null

    /** Monotonic timestamp after which the session is no longer valid; null while in foreground. */
    @Volatile
    private var expiresAtElapsedMillis: Long? = null

    private var autoLockJob: Job? = null

    /** Called once at startup, after checking whether a lock record exists on disk. */
    fun setConfigured(configured: Boolean) {
        _state.value = if (configured) VaultLockState.Locked else VaultLockState.NotConfigured
    }

    /** Opens the session. Only the key manager calls this, immediately after a successful unlock. */
    internal fun open(key: SecretKey) {
        masterKey = key
        expiresAtElapsedMillis = null
        autoLockJob?.cancel()
        autoLockJob = null
        _state.value = VaultLockState.Unlocked
        SecureLog.d(TAG, "Session opened")
    }

    /**
     * Returns the master key, or null when the session is locked or has expired.
     *
     * Every caller must handle null by asking the user to authenticate. There is no variant of this
     * that unlocks implicitly.
     */
    internal fun masterKeyOrNull(): SecretKey? {
        if (hasExpired()) {
            lock()
            return null
        }
        return masterKey
    }

    /**
     * Checking the session also enforces it: if the grace period has passed, the key is dropped and
     * [state] moves to [VaultLockState.Locked] here and now. A session that has expired but still
     * reports itself unlocked until some timer fires is exactly the bug this design exists to avoid.
     */
    val isUnlocked: Boolean
        get() {
            if (hasExpired()) {
                lock()
                return false
            }
            return masterKey != null
        }

    /** Drops the key and returns to the locked state. Safe to call repeatedly. */
    fun lock() {
        val had = masterKey != null
        masterKey = null
        expiresAtElapsedMillis = null
        autoLockJob?.cancel()
        autoLockJob = null
        if (_state.value != VaultLockState.NotConfigured) {
            _state.value = VaultLockState.Locked
        }
        if (had) SecureLog.d(TAG, "Session locked")
    }

    /**
     * The app went to the background.
     *
     * With the default [AutoLockDuration.IMMEDIATE] this locks straight away. With a grace period,
     * a timer is armed *and* an expiry timestamp is recorded — the timestamp is what actually
     * decides, so a killed or frozen process cannot leave the vault open by outliving its timer.
     */
    fun onAppBackgrounded(duration: AutoLockDuration) {
        if (masterKey == null) return

        if (duration == AutoLockDuration.IMMEDIATE) {
            lock()
            return
        }

        val expiry = timeProvider.elapsedRealtimeMillis() + duration.millis
        expiresAtElapsedMillis = expiry

        autoLockJob?.cancel()
        autoLockJob = applicationScope.launch {
            delay(duration.millis)
            if (hasExpired()) lock()
        }
    }

    /** The app came back to the foreground. Cancels a pending auto-lock if the session is still valid. */
    fun onAppForegrounded() {
        if (hasExpired()) {
            lock()
            return
        }
        expiresAtElapsedMillis = null
        autoLockJob?.cancel()
        autoLockJob = null
    }

    /** The screen turned off. Honoured only when the user asked for it. */
    fun onScreenOff(lockOnScreenOff: Boolean, duration: AutoLockDuration) {
        if (lockOnScreenOff) lock() else onAppBackgrounded(duration)
    }

    private fun hasExpired(): Boolean {
        val expiry = expiresAtElapsedMillis ?: return false
        return timeProvider.elapsedRealtimeMillis() >= expiry
    }
}
