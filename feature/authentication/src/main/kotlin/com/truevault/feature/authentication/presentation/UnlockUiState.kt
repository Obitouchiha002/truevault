package com.truevault.feature.authentication.presentation

import androidx.compose.runtime.Immutable
import com.truevault.core.model.VaultError
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
)

sealed interface UnlockAction {
    data class Submit(val password: CharArray) : UnlockAction {
        override fun equals(other: Any?): Boolean =
            other is Submit && password.contentEquals(other.password)

        override fun hashCode(): Int = password.contentHashCode()
    }

    data object BiometricRequested : UnlockAction

    data object RecoveryRequested : UnlockAction

    data object RecoveryDismissed : UnlockAction

    data class SubmitRecoveryKey(val key: String) : UnlockAction

    data class BiometricAuthenticated(val cipher: Cipher) : UnlockAction

    data object BiometricDismissed : UnlockAction

    data object ErrorDismissed : UnlockAction
}

sealed interface UnlockEffect {
    data class LaunchBiometricPrompt(val cipher: Cipher) : UnlockEffect

    data object Unlocked : UnlockEffect
}
