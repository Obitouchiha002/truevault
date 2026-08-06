package com.truevault.feature.authentication.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.common.result.Outcome
import com.truevault.core.crypto.kdf.wipe
import com.truevault.core.crypto.vault.VaultKeyManager
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.core.model.VaultError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Unlocks an existing vault.
 *
 * There is no attempt limit that wipes data, and no lockout timer. Argon2id already makes guessing
 * expensive, the outer Keystore layer makes offline guessing impossible, and a "wipe after N tries"
 * rule mostly destroys the data of users who mistype — it is a feature that punishes the owner more
 * reliably than it punishes an attacker. The attempt count is shown, not enforced.
 */
@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val keyManager: VaultKeyManager,
    private val preferences: UserPreferencesDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<UnlockEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<UnlockEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            val cipher = keyManager.biometricUnlockCipher()
            _uiState.update {
                it.copy(
                    biometricAvailable = cipher != null,
                    recoveryKeyAvailable = keyManager.hasRecoveryKey(),
                    lockType = keyManager.lockType(),
                    throttleRemainingMillis = keyManager.throttleRemainingMillis(),
                )
            }
        }

        // The remaining wait is counted down rather than shown once and left stale, so the user can
        // see it clearing instead of wondering whether the app has frozen.
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val remaining = keyManager.throttleRemainingMillis()
                if (remaining != _uiState.value.throttleRemainingMillis) {
                    _uiState.update { it.copy(throttleRemainingMillis = remaining) }
                }
            }
        }
    }

    fun onAction(action: UnlockAction) {
        when (action) {
            is UnlockAction.Submit -> unlock(action.password)
            UnlockAction.BiometricRequested -> requestBiometric()
            is UnlockAction.BiometricAuthenticated -> unlockWithBiometric(action.cipher)
            UnlockAction.BiometricDismissed -> Unit

            is UnlockAction.PinDigitEntered -> onPinDigit(action.digit)

            UnlockAction.PinBackspace -> {
                if (pinBuffer.isNotEmpty()) {
                    pinBuffer = pinBuffer.copyOf(pinBuffer.size - 1)
                    _uiState.update { it.copy(pinEnteredCount = pinBuffer.size, error = null) }
                }
            }
            UnlockAction.RecoveryRequested ->
                _uiState.update { it.copy(showingRecoveryEntry = true, error = null) }
            UnlockAction.RecoveryDismissed ->
                _uiState.update { it.copy(showingRecoveryEntry = false) }
            is UnlockAction.SubmitRecoveryKey -> unlockWithRecoveryKey(action.key)
            UnlockAction.ErrorDismissed -> _uiState.update { it.copy(error = null) }
        }
    }

    /** Entered digits. Never part of UI state — only the count is observable. */
    private var pinBuffer = CharArray(0)

    private fun onPinDigit(digit: Char) {
        val length = _uiState.value.lockType?.pinLength ?: return
        if (_uiState.value.isThrottled || pinBuffer.size >= length) return

        pinBuffer = pinBuffer.copyOf(pinBuffer.size + 1).also { it[it.size - 1] = digit }
        _uiState.update { it.copy(pinEnteredCount = pinBuffer.size, error = null) }

        if (pinBuffer.size == length) {
            val secret = pinBuffer.copyOf()
            pinBuffer.wipe()
            pinBuffer = CharArray(0)
            _uiState.update { it.copy(pinEnteredCount = 0) }
            unlock(secret)
        }
    }

    private fun unlock(password: CharArray) {
        if (_uiState.value.isCheckingPassword) return
        _uiState.update { it.copy(isCheckingPassword = true, error = null) }

        viewModelScope.launch {
            try {
                when (val result = keyManager.unlockWithPassword(password)) {
                    is Outcome.Success -> {
                        _uiState.update { it.copy(isCheckingPassword = false, failedAttempts = 0) }
                        _effects.emit(UnlockEffect.Unlocked)
                    }

                    is Outcome.Failure -> _uiState.update {
                        it.copy(
                            isCheckingPassword = false,
                            failedAttempts = it.failedAttempts + 1,
                            error = result.error,
                            throttleRemainingMillis = (result.error as? VaultError.TooManyAttempts)
                                ?.waitMillis
                                ?: keyManager.throttleRemainingMillis(),
                        )
                    }
                }
            } finally {
                password.wipe()
            }
        }
    }

    private fun unlockWithRecoveryKey(key: String) {
        _uiState.update { it.copy(isCheckingPassword = true, error = null) }
        viewModelScope.launch {
            when (val result = keyManager.unlockWithRecoveryKey(key)) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(isCheckingPassword = false) }
                    _effects.emit(UnlockEffect.Unlocked)
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isCheckingPassword = false, error = result.error)
                }
            }
        }
    }

    private fun requestBiometric() {
        viewModelScope.launch {
            val cipher = keyManager.biometricUnlockCipher()
            if (cipher == null) {
                // The key was invalidated by a new enrolment. Say so, and fall back to the password.
                preferences.setBiometricUnlockEnabled(false)
                _uiState.update { it.copy(biometricAvailable = false, biometricWasReset = true) }
            } else {
                _effects.emit(UnlockEffect.LaunchBiometricPrompt(cipher))
            }
        }
    }

    private fun unlockWithBiometric(cipher: Cipher) {
        viewModelScope.launch {
            when (val result = keyManager.unlockWithBiometric(cipher)) {
                is Outcome.Success -> _effects.emit(UnlockEffect.Unlocked)
                is Outcome.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }
}
