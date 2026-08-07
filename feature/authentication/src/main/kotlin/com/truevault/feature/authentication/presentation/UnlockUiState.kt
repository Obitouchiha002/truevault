package com.truevault.feature.authentication.presentation

import androidx.compose.runtime.Immutable
import com.truevault.core.model.VaultError
import com.truevault.core.model.VaultLockType
import javax.crypto.Cipher

@Immutable
data class UnlockUiState(
    val isCheckingPassword: Boolean = false,
    val biometricAvailable: Boolean = false,
    val failedAttempts: Int = 0,
    val error: VaultError? = null,
    /** Set when a new biometric enrolment invalidated the stored biometric key. */
    val biometricWasReset: Boolean = false,
    val recoveryKeyAvailable: Boolean = false,
    val showingRecoveryEntry: Boolean = false,
    /** Null until the stored lock record has been read. */
    val lockType: VaultLockType? = null,
    /** Digits entered so far. Only the count, never the digits. */
    val pinEnteredCount: Int = 0,
    /** Milliseconds still to wait after too many failures; zero when attempts are accepted. */
    val throttleRemainingMillis: Long = 0,
) {
    val isThrottled: Boolean get() = throttleRemainingMillis > 0
}

sealed interface UnlockAction {
    data class Submit(val password: CharArray) : UnlockAction {
        override fun equals(other: Any?): Boolean =
            other is Submit && password.contentEquals(other.password)

        override fun hashCode(): Int = password.contentHashCode()
    }

    data object BiometricRequested : UnlockAction

    data class PinDigitEntered(val digit: Char) : UnlockAction

    data object PinBackspace : UnlockAction

    data object RecoveryRequested : UnlockAction

    data object RecoveryDismissed : UnlockAction

    data class SubmitRecoveryKey(val key: String) : UnlockAction {
        /**
         * The recovery key is the only credential that works on a different device, which makes it
         * the most damaging string in the app to leak. A data class prints every field, so the
         * generated `toString` would put it verbatim into any log line, crash trace or debugger
         * frame that ever stringifies this action. [Submit] holds a `CharArray` and is safe by
         * construction; this one is a `String` and has to say so.
         */
        override fun toString(): String = "SubmitRecoveryKey(key=<redacted>)"
    }

    data class BiometricAuthenticated(val cipher: Cipher) : UnlockAction

    data object BiometricDismissed : UnlockAction

    data object ErrorDismissed : UnlockAction
}

sealed interface UnlockEffect {
    data class LaunchBiometricPrompt(val cipher: Cipher) : UnlockEffect

    data object Unlocked : UnlockEffect
}
