package com.truevault.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.remote.InstallRecord
import com.truevault.core.remote.InstallStatus
import com.truevault.core.remote.RemoteGateRepository
import com.truevault.core.remote.RemoteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Name on first launch
// ---------------------------------------------------------------------------------------------

data class NamePromptUiState(
    val name: String = "",
    val isSaving: Boolean = false,
    val done: Boolean = false,
) {
    /** One character is enough. This is a label, not a credential, and nothing verifies it. */
    val canContinue: Boolean get() = name.trim().isNotEmpty() && !isSaving
}

@HiltViewModel
class NamePromptViewModel @Inject constructor(
    private val gate: RemoteGateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NamePromptUiState())
    val uiState: StateFlow<NamePromptUiState> = _uiState.asStateFlow()

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value.take(40)) }

    fun onContinue() {
        if (!_uiState.value.canContinue) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            gate.setDisplayName(_uiState.value.name)
            _uiState.update { it.copy(isSaving = false, done = true) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Blocked
// ---------------------------------------------------------------------------------------------

@HiltViewModel
class BlockedViewModel @Inject constructor(
    private val gate: RemoteGateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstallStatus.Unknown)
    val uiState: StateFlow<InstallStatus> = _uiState.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    init {
        viewModelScope.launch {
            gate.restore()
            gate.status.collect { _uiState.value = it }
        }
    }

    /** Lets a user whose block has just been lifted get back in without reinstalling. */
    fun retry(version: String) {
        if (_isChecking.value) return
        _isChecking.value = true
        viewModelScope.launch {
            gate.checkIn(version)
            _isChecking.value = false
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Admin panel
// ---------------------------------------------------------------------------------------------

data class AdminUiState(
    val pin: String = "",
    val authorised: Boolean = false,
    val installs: List<InstallRecord> = emptyList(),
    val isBusy: Boolean = false,
    val message: String? = null,
    val killSwitch: Boolean = false,
    /** False on a backend without the `admin_premium` function — StreamGarden's, for instance. */
    val premiumSupported: Boolean = true,
) {
    val canSubmitPin: Boolean get() = pin.length >= 4 && !isBusy
}

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val gate: RemoteGateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    fun onPinChanged(value: String) = _uiState.update { it.copy(pin = value.take(64)) }

    /**
     * A wrong PIN produces no message at all — the screen simply stays where it is. Anything that
     * distinguishes "wrong PIN" from "no panel here" turns a hidden door into a discovered one.
     */
    fun unlock() {
        if (!_uiState.value.canSubmitPin) return
        refresh(firstUnlock = true)
    }

    fun refresh(firstUnlock: Boolean = false) {
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            when (val result = gate.adminInstalls(_uiState.value.pin)) {
                is RemoteResult.Ok -> _uiState.update {
                    it.copy(isBusy = false, authorised = true, installs = result.value)
                }
                is RemoteResult.Refused -> _uiState.update {
                    it.copy(isBusy = false, message = if (firstUnlock) null else "Refused")
                }
                RemoteResult.Unreachable -> _uiState.update {
                    it.copy(isBusy = false, message = "No connection")
                }
            }
        }
    }

    fun setBlocked(id: String, blocked: Boolean, reason: String?, minutes: Int?, code: String?) =
        act { gate.adminBlock(_uiState.value.pin, id, blocked, reason, minutes, code) }

    fun setPremium(id: String, premium: Boolean) {
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            when (gate.adminPremium(_uiState.value.pin, id, premium)) {
                is RemoteResult.Ok -> refresh()
                // The function does not exist on this backend. Hide the control rather than
                // leaving a switch that silently does nothing every time it is touched.
                is RemoteResult.Refused -> _uiState.update {
                    it.copy(isBusy = false, premiumSupported = false)
                }
                RemoteResult.Unreachable -> _uiState.update {
                    it.copy(isBusy = false, message = "No connection")
                }
            }
        }
    }

    fun setKillSwitch(enabled: Boolean) {
        _uiState.update { it.copy(killSwitch = enabled) }
        act { gate.adminConfig(_uiState.value.pin, enabled, null, null, null) }
    }

    private fun act(block: suspend () -> RemoteResult<Unit>) {
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = block()
            if (result is RemoteResult.Ok) {
                refresh()
            } else {
                _uiState.update { it.copy(isBusy = false, message = "That did not go through") }
            }
        }
    }
}
