package com.truevault.feature.launcher.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.capabilities.DeviceCapabilityDetector
import com.truevault.core.capabilities.model.CapabilityActionResult
import com.truevault.core.capabilities.model.DeviceCapabilities
import com.truevault.core.capabilities.privateapps.PrivateAppsController
import com.truevault.feature.launcher.domain.LauncherVisibilityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class AdvancedPrivacyUiState(
    val capabilities: DeviceCapabilities = DeviceCapabilities.Unknown,
    val visibilityEnabled: Boolean = false,
    val hiddenCount: Int = 0,
    val showingRoleExplanation: Boolean = false,
    val showingVisibilityWarning: Boolean = false,
    val lastResult: CapabilityActionResult? = null,
)

sealed interface AdvancedPrivacyAction {
    data object SecureLauncherRequested : AdvancedPrivacyAction
    data object RoleExplanationDismissed : AdvancedPrivacyAction
    data object RoleRequestConfirmed : AdvancedPrivacyAction
    data object VisibilityRequested : AdvancedPrivacyAction
    data object VisibilityWarningDismissed : AdvancedPrivacyAction
    data object VisibilityConfirmed : AdvancedPrivacyAction
    data object VisibilityDisabled : AdvancedPrivacyAction
    data object RestoreAllIcons : AdvancedPrivacyAction
    data object ResultDismissed : AdvancedPrivacyAction
}

/**
 * Advanced Privacy.
 *
 * Both features here are opt-in, off by default, and explained before anything is requested.
 * Secure Launcher Mode is never offered during onboarding, and the Home role is only requested
 * after the user has read what changing their launcher means.
 */
@HiltViewModel
class AdvancedPrivacyViewModel @Inject constructor(
    private val capabilityDetector: DeviceCapabilityDetector,
    private val privateAppsController: PrivateAppsController,
    private val visibilityStore: LauncherVisibilityStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdvancedPrivacyUiState())
    val uiState: StateFlow<AdvancedPrivacyUiState> = _uiState.asStateFlow()

    init {
        capabilityDetector.observeCapabilities()
            .onEach { capabilities -> _uiState.update { it.copy(capabilities = capabilities) } }
            .launchIn(viewModelScope)

        visibilityStore.isEnabled
            .onEach { enabled -> _uiState.update { it.copy(visibilityEnabled = enabled) } }
            .launchIn(viewModelScope)

        visibilityStore.hiddenPackages
            .onEach { hidden -> _uiState.update { it.copy(hiddenCount = hidden.size) } }
            .launchIn(viewModelScope)
    }

    fun refresh() = capabilityDetector.refresh()

    fun onAction(action: AdvancedPrivacyAction) {
        when (action) {
            AdvancedPrivacyAction.SecureLauncherRequested ->
                _uiState.update { it.copy(showingRoleExplanation = true) }

            AdvancedPrivacyAction.RoleExplanationDismissed ->
                _uiState.update { it.copy(showingRoleExplanation = false) }

            AdvancedPrivacyAction.RoleRequestConfirmed -> viewModelScope.launch {
                _uiState.update { it.copy(showingRoleExplanation = false) }
                val result = privateAppsController.requestLauncherRole()
                _uiState.update { it.copy(lastResult = result) }
            }

            AdvancedPrivacyAction.VisibilityRequested ->
                _uiState.update { it.copy(showingVisibilityWarning = true) }

            AdvancedPrivacyAction.VisibilityWarningDismissed ->
                _uiState.update { it.copy(showingVisibilityWarning = false) }

            AdvancedPrivacyAction.VisibilityConfirmed -> viewModelScope.launch {
                _uiState.update { it.copy(showingVisibilityWarning = false) }
                visibilityStore.setEnabled(true)
            }

            AdvancedPrivacyAction.VisibilityDisabled -> viewModelScope.launch {
                // Disabling restores every icon, so there is no state where the feature is off and
                // apps stay hidden.
                visibilityStore.setEnabled(false)
            }

            AdvancedPrivacyAction.RestoreAllIcons -> viewModelScope.launch {
                visibilityStore.restoreAll()
            }

            AdvancedPrivacyAction.ResultDismissed -> _uiState.update { it.copy(lastResult = null) }
        }
    }
}
