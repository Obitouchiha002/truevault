package com.truevault.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.remote.InstallStatus
import com.truevault.core.remote.RemoteGateRepository
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
