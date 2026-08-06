package com.truevault.feature.legal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.legal.LegalRepository
import com.truevault.core.model.LegalAcceptanceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LegalSettingsUiState(
    val termsVersion: String = "",
    val privacyVersion: String = "",
    val hasAcceptanceRecord: Boolean = false,
    val reacceptanceRequired: Boolean = false,
)

@HiltViewModel
class LegalSettingsViewModel @Inject constructor(
    private val repository: LegalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LegalSettingsUiState())
    val uiState: StateFlow<LegalSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val versions = runCatching { repository.versions() }.getOrNull()

            _uiState.update {
                it.copy(
                    termsVersion = versions?.termsVersion.orEmpty(),
                    privacyVersion = versions?.privacyVersion.orEmpty(),
                )
            }
        }

        viewModelScope.launch {
            repository.status().collect { status ->
                _uiState.update {
                    it.copy(
                        // Corrupted counts as "no record". Showing "accepted" for something that
                        // could not be read back would be reporting a fact the app does not have.
                        hasAcceptanceRecord = status is LegalAcceptanceStatus.Accepted,
                        reacceptanceRequired = status is LegalAcceptanceStatus.ReacceptanceRequired,
                    )
                }
            }
        }
    }
}
