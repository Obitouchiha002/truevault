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
    private val biometricCapabilityChecker: BiometricCapabilityChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CreateVaultLockUiState(biometricCapability = biometricCapabilityChecker.capability()),
    )
    val uiState: StateFlow<CreateVaultLockUiState> = _uiState.asStateFlow()

    /**
     * Re-reads the biometric capability, and must be called every time the screen resumes.
     *
     * `canAuthenticate(BIOMETRIC_STRONG)` returns HW_UNAVAILABLE transiently — a sensor still waking
     * after boot, momentarily held by the keyguard, or busy for another app. Checking it only once
     * at construction froze that transient answer into "hardware is not responding" and never asked
     * again, so a device with a perfectly working fingerprint showed the error for the whole screen.
     * Re-querying on resume — including right after the user returns from enrolling a fingerprint in
     * system settings — is what lets the real answer replace the stale one.
     */
    fun refreshBiometricCapability() {
        _uiState.update { it.copy(biometricCapability = biometricCapabilityChecker.capability()) }
    }

    private val _effects = MutableSharedFlow<CreateVaultLockEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<CreateVaultLockEffect> = _effects.asSharedFlow()

    /** Pure domain call, exposed so the screen does not perform the assessment itself. */
    fun assess(password: CharArray): PasswordAssessment = assessPassword(password)

    /**
     * The digits entered so far.
     *
     * A [CharArray] rather than a String, and never part of the UI state: only the *count* of
     * entered digits is exposed, so the PIN itself is not held in an observable that survives
     * recomposition or lands in a state restore.
     */
    private var pinBuffer = CharArray(0)
    private var firstPin: CharArray? = null

    fun onAction(action: CreateVaultLockAction) {
        when (action) {
            is CreateVaultLockAction.LockTypeSelected ->
                _uiState.update { it.copy(lockType = action.lockType, error = null) }

            CreateVaultLockAction.LockTypeConfirmed -> {
                resetPinEntry()
                _uiState.update { it.copy(stage = CreateLockStage.ENTER, pinMismatch = false) }
            }

            is CreateVaultLockAction.PinDigitEntered -> onPinDigit(action.digit)

            CreateVaultLockAction.PinBackspace -> {
                if (pinBuffer.isNotEmpty()) {
                    pinBuffer = pinBuffer.copyOf(pinBuffer.size - 1)
                    _uiState.update { it.copy(pinEnteredCount = pinBuffer.size, pinMismatch = false) }
                }
            }

            CreateVaultLockAction.StartOver -> {
                resetPinEntry()
                firstPin?.wipe()
                firstPin = null
                _uiState.update {
                    it.copy(
                        stage = CreateLockStage.CHOOSE_TYPE,
                        pinEnteredCount = 0,
                        pinMismatch = false,
                        error = null,
                    )
                }
            }

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

    private fun onPinDigit(digit: Char) {
        val length = _uiState.value.lockType.pinLength ?: return
        if (pinBuffer.size >= length) return

        pinBuffer = pinBuffer.copyOf(pinBuffer.size + 1).also { it[it.size - 1] = digit }
        _uiState.update { it.copy(pinEnteredCount = pinBuffer.size, pinMismatch = false) }

        if (pinBuffer.size < length) return

        when (_uiState.value.stage) {
            CreateLockStage.ENTER -> {
                firstPin = pinBuffer.copyOf()
                resetPinEntry()
                _uiState.update { it.copy(stage = CreateLockStage.CONFIRM, pinEnteredCount = 0) }
            }

            CreateLockStage.CONFIRM -> {
                val first = firstPin
                if (first != null && first.contentEquals(pinBuffer)) {
                    val secret = pinBuffer.copyOf()
                    first.wipe()
                    firstPin = null
                    resetPinEntry()
                    createVault(secret)
                } else {
                    // Start the whole entry again rather than only the confirmation: a user who
                    // mistyped does not know which of the two entries was wrong.
                    first?.wipe()
                    firstPin = null
                    resetPinEntry()
                    _uiState.update {
                        it.copy(
                            stage = CreateLockStage.ENTER,
                            pinEnteredCount = 0,
                            pinMismatch = true,
                        )
                    }
                }
            }

            CreateLockStage.CHOOSE_TYPE -> Unit
        }
    }

    private fun resetPinEntry() {
        pinBuffer.wipe()
        pinBuffer = CharArray(0)
    }

    override fun onCleared() {
        resetPinEntry()
        firstPin?.wipe()
        firstPin = null
    }

    private fun createVault(password: CharArray) {
        if (_uiState.value.isCreating) return
        _uiState.update { it.copy(isCreating = true, error = null) }

        viewModelScope.launch {
            try {
                when (val result = keyManager.createLock(password, _uiState.value.lockType)) {
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
