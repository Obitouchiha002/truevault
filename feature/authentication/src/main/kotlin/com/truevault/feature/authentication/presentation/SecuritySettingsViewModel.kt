package com.truevault.feature.authentication.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.common.result.Outcome
import com.truevault.core.crypto.keystore.HardwareKeyStore
import com.truevault.core.crypto.session.VaultSession
import com.truevault.core.crypto.vault.VaultKeyManager
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.feature.authentication.domain.BiometricCapability
import com.truevault.feature.authentication.domain.BiometricCapabilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesDataSource,
    private val keyManager: VaultKeyManager,
    private val session: VaultSession,
    private val biometricCapabilityChecker: BiometricCapabilityChecker,
    hardwareKeyStore: HardwareKeyStore,
) : ViewModel() {

    private val hardwareBacked = hardwareKeyStore.isHardwareBacked()

    val uiState: StateFlow<SecuritySettingsUiState> = preferences.userPreferences
        .map { prefs ->
            SecuritySettingsUiState(
                isLoading = false,
                autoLockDuration = prefs.autoLockDuration,
                lockOnScreenOff = prefs.lockOnScreenOff,
                blockScreenshots = prefs.blockScreenshots,
                biometricUnlockEnabled = prefs.biometricUnlockEnabled,
                biometricCapability = biometricCapabilityChecker.capability(),
                hardwareBackedKeystore = hardwareBacked,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SecuritySettingsUiState(),
        )

    private val _effects = MutableSharedFlow<SecuritySettingsEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<SecuritySettingsEffect> = _effects.asSharedFlow()

    fun onAction(action: SecuritySettingsAction) {
        when (action) {
            is SecuritySettingsAction.AutoLockSelected ->
                edit { setAutoLockDuration(action.duration) }

            is SecuritySettingsAction.LockOnScreenOffToggled ->
                edit { setLockOnScreenOff(action.enabled) }

            is SecuritySettingsAction.BlockScreenshotsToggled ->
                edit { setBlockScreenshots(action.enabled) }

            is SecuritySettingsAction.BiometricToggled -> toggleBiometrics(action.enabled)

            is SecuritySettingsAction.BiometricEnrolled -> completeEnrolment(action.cipher)

            SecuritySettingsAction.BiometricEnrolmentCancelled ->
                edit { setBiometricUnlockEnabled(false) }

            SecuritySettingsAction.LockNow -> lockNow()
        }
    }

    private fun toggleBiometrics(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                keyManager.disableBiometricUnlock()
                preferences.setBiometricUnlockEnabled(false)
                return@launch
            }

            if (biometricCapabilityChecker.capability() != BiometricCapability.AVAILABLE) return@launch

            // Requires an unlocked session by design: turning biometrics on must never become a way
            // to gain access without first having proved knowledge of the password.
            val cipher = keyManager.biometricEnrolCipher() ?: return@launch
            _effects.emit(SecuritySettingsEffect.RequestBiometricEnrolment(cipher))
        }
    }

    private fun completeEnrolment(cipher: Cipher) {
        viewModelScope.launch {
            when (keyManager.enableBiometricUnlock(cipher)) {
                is Outcome.Success -> preferences.setBiometricUnlockEnabled(true)
                is Outcome.Failure -> preferences.setBiometricUnlockEnabled(false)
            }
        }
    }

    private fun lockNow() {
        session.lock()
        viewModelScope.launch { _effects.emit(SecuritySettingsEffect.Locked) }
    }

    private fun edit(block: suspend UserPreferencesDataSource.() -> Unit) {
        viewModelScope.launch { preferences.block() }
    }
}
