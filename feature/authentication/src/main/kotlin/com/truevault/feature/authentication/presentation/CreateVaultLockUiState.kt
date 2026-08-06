package com.truevault.feature.authentication.presentation

import androidx.compose.runtime.Immutable
import com.truevault.core.model.PasswordAssessment
import com.truevault.core.model.PasswordStrength
import com.truevault.core.model.VaultError
import com.truevault.core.model.VaultLockType
import com.truevault.feature.authentication.domain.BiometricCapability
import javax.crypto.Cipher

/** Which step of choosing a secret the user is on. */
enum class CreateLockStage { CHOOSE_TYPE, ENTER, CONFIRM }

@Immutable
data class CreateVaultLockUiState(
    val lockType: VaultLockType = VaultLockType.PIN_6,
    val stage: CreateLockStage = CreateLockStage.CHOOSE_TYPE,
    /** Digits entered so far. Only the count reaches the UI — never the digits. */
    val pinEnteredCount: Int = 0,
    val pinMismatch: Boolean = false,
    val assessment: PasswordAssessment? = null,
    val passwordsMatch: Boolean = false,
    val confirmTouched: Boolean = false,
    val biometricCapability: BiometricCapability = BiometricCapability.UNSUPPORTED,
    val enableBiometrics: Boolean = false,
    val isCreating: Boolean = false,
    val error: VaultError? = null,
) {
    val strength: PasswordStrength get() = assessment?.strength ?: PasswordStrength.TOO_SHORT

    val canSubmit: Boolean
        get() = !isCreating && assessment?.isAcceptable == true && passwordsMatch
}

sealed interface CreateVaultLockAction {
    data class LockTypeSelected(val lockType: VaultLockType) : CreateVaultLockAction
    data object LockTypeConfirmed : CreateVaultLockAction
    data class PinDigitEntered(val digit: Char) : CreateVaultLockAction
    data object PinBackspace : CreateVaultLockAction
    data object StartOver : CreateVaultLockAction

    data class PasswordChanged(val password: CharArray, val confirmation: CharArray) :
        CreateVaultLockAction {
        // Generated equals on a CharArray field compares references, which would make two identical
        // passwords look different. Content comparison keeps recomposition correct.
        override fun equals(other: Any?): Boolean = other is PasswordChanged &&
            password.contentEquals(other.password) &&
            confirmation.contentEquals(other.confirmation)

        override fun hashCode(): Int =
            31 * password.contentHashCode() + confirmation.contentHashCode()
    }

    data class BiometricToggled(val enabled: Boolean) : CreateVaultLockAction

    data class Submit(val password: CharArray) : CreateVaultLockAction {
        override fun equals(other: Any?): Boolean =
            other is Submit && password.contentEquals(other.password)

        override fun hashCode(): Int = password.contentHashCode()
    }

    data class BiometricEnrolled(val cipher: Cipher) : CreateVaultLockAction

    data object BiometricEnrolmentDeclined : CreateVaultLockAction

    data object ErrorDismissed : CreateVaultLockAction
}

sealed interface CreateVaultLockEffect {
    /** Ask the UI to run BiometricPrompt with this cipher before the vault opens. */
    data class RequestBiometricEnrolment(val cipher: Cipher) : CreateVaultLockEffect

    data object VaultCreated : CreateVaultLockEffect
}
