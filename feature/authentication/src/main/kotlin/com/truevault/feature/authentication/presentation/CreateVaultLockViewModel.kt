package com.truevault.feature.authentication.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.common.result.Outcome
import com.truevault.core.crypto.kdf.wipe
import com.truevault.core.crypto.vault.VaultKeyManager
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.core.model.PasswordAssessment
import com.truevault.core.model.assessPassword
import com.truevault.feature.authentication.domain.BiometricCapability
import com.truevault.feature.authentication.domain.BiometricCapabilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Creates the vault lock.
 *
 * The password never enters [CreateVaultLockUiState]. It arrives as a [CharArray] with each action,
 * is used, and is wiped — so it is not held in a StateFlow, not written to `SavedStateHandle`, and
 * not recoverable from a state restore.
 */
@HiltViewModel
class CreateVaultLockViewModel @Inject constructor(
    private val keyManager: VaultKeyManager,
    private val preferences: UserPreferencesDataSource,
    biometricCapabilityChecker: BiometricCapabilityChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CreateVaultLockUiState(biometricCapability = biometricCapabilityChecker.capability()),
    )
    val uiState: StateFlow<CreateVaultLockUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CreateVaultLockEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<CreateVaultLockEffect> = _effects.asSharedFlow()

    /** Pure domain call, exposed so the screen does not perform the assessment itself. */
    fun assess(password: CharArray): PasswordAssessment = assessPassword(password)

    fun onAction(action: CreateVaultLockAction) {
        when (action) {
            is CreateVaultLockAction.PasswordChanged -> {
                val assessment = assessPassword(action.password)
                _uiState.update {
                    it.copy(
                        assessment = assessment,
                        passwordsMatch = action.password.isNotEmpty() &&
                            action.password.contentEquals(action.confirmation),
                        confirmTouched = action.confirmation.isNotEmpty(),
                        error = null,
                    )
                }
            }

            is CreateVaultLockAction.BiometricToggled ->
                _uiState.update { it.copy(enableBiometrics = action.enabled) }

            is CreateVaultLockAction.Submit -> createVault(action.password)

            is CreateVaultLockAction.BiometricEnrolled -> enableBiometrics(action.cipher)

            CreateVaultLockAction.BiometricEnrolmentDeclined -> finish()

            CreateVaultLockAction.ErrorDismissed -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun createVault(password: CharArray) {
        if (_uiState.value.isCreating) return
        _uiState.update { it.copy(isCreating = true, error = null) }

        viewModelScope.launch {
            try {
                when (val result = keyManager.createLock(password)) {
                    is Outcome.Failure -> _uiState.update {
                        it.copy(isCreating = false, error = result.error)
                    }

                    is Outcome.Success -> {
                        val wantsBiometrics = _uiState.value.enableBiometrics &&
                            _uiState.value.biometricCapability == BiometricCapability.AVAILABLE
                        val cipher = if (wantsBiometrics) keyManager.biometricEnrolCipher() else null

                        if (cipher != null) {
                            _effects.emit(CreateVaultLockEffect.RequestBiometricEnrolment(cipher))
                        } else {
                            finish()
                        }
                    }
                }
            } finally {
                password.wipe()
            }
        }
    }

    private fun enableBiometrics(cipher: javax.crypto.Cipher) {
        viewModelScope.launch {
            // A failure here is not fatal: the vault already exists and the password still opens it.
            when (keyManager.enableBiometricUnlock(cipher)) {
                is Outcome.Success -> preferences.setBiometricUnlockEnabled(true)
                is Outcome.Failure -> preferences.setBiometricUnlockEnabled(false)
            }
            finish()
        }
    }

    private fun finish() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = false) }
            _effects.emit(CreateVaultLockEffect.VaultCreated)
        }
    }
}
