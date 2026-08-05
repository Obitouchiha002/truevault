package com.truevault.feature.privateapps.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.capabilities.DeviceCapabilityDetector
import com.truevault.core.capabilities.model.CapabilityActionResult
import com.truevault.core.capabilities.privateapps.PrivateAppsController
import com.truevault.core.capabilities.provider.OemSettingsCapabilityProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Private Apps.
 *
 * Capabilities and profile state are both observed, not read once: Private Space can be created,
 * locked or unlocked while this screen is open, and the UI has to follow without a restart.
 */
@HiltViewModel
class PrivateAppsViewModel @Inject constructor(
    private val controller: PrivateAppsController,
    private val capabilityDetector: DeviceCapabilityDetector,
    private val oemSettings: OemSettingsCapabilityProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivateAppsUiState())
    val uiState: StateFlow<PrivateAppsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PrivateAppsEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<PrivateAppsEffect> = _effects.asSharedFlow()

    init {
        capabilityDetector.observeCapabilities()
            .onEach { capabilities -> _uiState.update { it.copy(capabilities = capabilities) } }
            .launchIn(viewModelScope)

        controller.observeState()
            .onEach { state ->
                _uiState.update { current ->
                    // A locked profile clears anything derived from it. Nothing about a private app
                    // may stay on screen once the profile it belongs to is locked.
                    current.copy(
                        privateSpaceState = state,
                        removalCandidate = if (state is com.truevault.core.capabilities.model.PrivateSpaceState.ConfiguredUnlocked) {
                            current.removalCandidate
                        } else {
                            null
                        },
                    )
                }
            }
            .launchIn(viewModelScope)

        refresh()
    }

    fun onAction(action: PrivateAppsAction) {
        when (action) {
            PrivateAppsAction.Refresh -> refresh()
            PrivateAppsAction.SetupRequested -> runAction { controller.openSetup() }
            PrivateAppsAction.WarningsRequested -> _uiState.update { it.copy(showingWarnings = true) }
            PrivateAppsAction.WarningsDismissed -> _uiState.update { it.copy(showingWarnings = false) }

            PrivateAppsAction.WarningsAcknowledged -> _uiState.update {
                it.copy(showingWarnings = false, warningsAcknowledged = true)
            }

            PrivateAppsAction.OpenOemSettings -> {
                val opened = oemSettings.openPrivacySettings()
                _uiState.update {
                    it.copy(
                        lastActionResult = if (opened) {
                            CapabilityActionResult.Success
                        } else {
                            CapabilityActionResult.SettingsUnavailable
                        },
                    )
                }
            }

            is PrivateAppsAction.RemovalConsidered -> considerRemoval(action.packageName, action.label)

            PrivateAppsAction.ManualVerificationConfirmed -> _uiState.update { state ->
                state.copy(removalCandidate = state.removalCandidate?.copy(manuallyConfirmed = true))
            }

            PrivateAppsAction.RemovalDismissed -> _uiState.update { it.copy(removalCandidate = null) }

            PrivateAppsAction.RemoveMainCopy -> {
                val candidate = _uiState.value.removalCandidate ?: return
                if (!candidate.canRemove) return
                viewModelScope.launch {
                    _effects.emit(PrivateAppsEffect.LaunchUninstall(candidate.packageName))
                }
            }

            PrivateAppsAction.OpenPrivateCopy -> openPrivateCopy()

            PrivateAppsAction.ResultDismissed -> _uiState.update { it.copy(lastActionResult = null) }
        }
    }

    fun refresh() {
        capabilityDetector.refresh()
        viewModelScope.launch { controller.refresh() }
    }

    private fun considerRemoval(packageName: String, label: String) {
        viewModelScope.launch {
            // Verified means observed through a supported API. When TrueVault cannot see the
            // profile, this is false and the UI asks the user to confirm manually instead.
            val verified = controller.isInstalledInPrivateProfile(packageName)
            _uiState.update {
                it.copy(
                    removalCandidate = RemovalCandidate(
                        packageName = packageName,
                        label = label,
                        privateCopyVerified = verified,
                        manuallyConfirmed = false,
                    ),
                )
            }
        }
    }

    private fun openPrivateCopy() {
        val candidate = _uiState.value.removalCandidate ?: return
        viewModelScope.launch {
            val apps = controller.listApps()
            val entry = apps.firstOrNull {
                it.isPrivateProfile && it.id.packageName == candidate.packageName
            }
            val result = entry?.let { controller.openPrivateApp(it.id) }
                ?: CapabilityActionResult.Unsupported
            _uiState.update { it.copy(lastActionResult = result) }
        }
    }

    private fun runAction(block: suspend () -> CapabilityActionResult) {
        viewModelScope.launch {
            val result = block()
            _uiState.update { it.copy(lastActionResult = result) }
        }
    }
}
