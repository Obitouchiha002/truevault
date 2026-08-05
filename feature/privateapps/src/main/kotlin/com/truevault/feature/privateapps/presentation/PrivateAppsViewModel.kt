package com.truevault.feature.privateapps.presentation

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.truevault.core.model.PrivateAppsCapability
import com.truevault.feature.privateapps.domain.PrivateAppsCapabilityDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class PrivateAppsUiState(
    val capability: PrivateAppsCapability = PrivateAppsCapability.UNKNOWN,
)

@HiltViewModel
class PrivateAppsViewModel @Inject constructor(
    private val detector: PrivateAppsCapabilityDetector,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivateAppsUiState(detector.detect()))
    val uiState: StateFlow<PrivateAppsUiState> = _uiState.asStateFlow()

    /** Re-detected on resume: the user may have set Private Space up while they were away. */
    fun refresh() {
        _uiState.value = PrivateAppsUiState(detector.detect())
    }

    fun systemSettingsIntent(): Intent? = detector.settingsIntentOrNull()
}
