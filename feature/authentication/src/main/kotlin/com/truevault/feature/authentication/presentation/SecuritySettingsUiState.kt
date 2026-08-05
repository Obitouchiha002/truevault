package com.truevault.feature.authentication.presentation

import androidx.compose.runtime.Immutable
import com.truevault.core.model.AutoLockDuration
import com.truevault.feature.authentication.domain.BiometricCapability
import javax.crypto.Cipher

@Immutable
data class SecuritySettingsUiState(
    val isLoading: Boolean = true,
    val autoLockDuration: AutoLockDuration = AutoLockDuration.IMMEDIATE,
    val lockOnScreenOff: Boolean = true,
    val blockScreenshots: Boolean = true,
    val biometricUnlockEnabled: Boolean = false,
    val biometricCapability: BiometricCapability = BiometricCapability.UNSUPPORTED,
    val hardwareBackedKeystore: Boolean = false,
)

sealed interface SecuritySettingsAction {
    data class AutoLockSelected(val duration: AutoLockDuration) : SecuritySettingsAction
    data class LockOnScreenOffToggled(val enabled: Boolean) : SecuritySettingsAction
    data class BlockScreenshotsToggled(val enabled: Boolean) : SecuritySettingsAction
    data class BiometricToggled(val enabled: Boolean) : SecuritySettingsAction
    data class BiometricEnrolled(val cipher: Cipher) : SecuritySettingsAction
    data object BiometricEnrolmentCancelled : SecuritySettingsAction
    data object LockNow : SecuritySettingsAction
}

sealed interface SecuritySettingsEffect {
    data class RequestBiometricEnrolment(val cipher: Cipher) : SecuritySettingsEffect
    data object Locked : SecuritySettingsEffect
}
