package com.truevault.feature.launcher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.capabilities.DeviceCapabilityDetector
import com.truevault.core.capabilities.model.LauncherAppEntry
import com.truevault.core.capabilities.model.PrivateSpaceState
import com.truevault.core.capabilities.privateapps.PrivateAppsController
import com.truevault.core.crypto.session.VaultLockState
import com.truevault.core.crypto.session.VaultSession
import com.truevault.feature.launcher.domain.LauncherAppsRepository
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

/**
 * Secure Launcher Mode's home screen.
 *
 * Profile state drives everything. When Private Space locks, the private list is cleared from state
 * — not merely hidden by a flag — so no stale label can be reintroduced by a recomposition or a
 * search. When it unlocks, the list is re-read from the platform rather than restored from a cache.
 */
@HiltViewModel
class SecureLauncherViewModel @Inject constructor(
    private val appsRepository: LauncherAppsRepository,
    private val visibilityStore: LauncherVisibilityStore,
    private val privateAppsController: PrivateAppsController,
    private val capabilityDetector: DeviceCapabilityDetector,
    vaultSession: VaultSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecureLauncherUiState())
    val uiState: StateFlow<SecureLauncherUiState> = _uiState.asStateFlow()

    init {
        capabilityDetector.observeCapabilities()
            .onEach { capabilities ->
                _uiState.update { it.copy(isDefaultLauncher = capabilities.isDefaultLauncher) }
            }
            .launchIn(viewModelScope)

        privateAppsController.observeState()
            .onEach { state ->
                _uiState.update { current ->
                    current.copy(
                        privateSpaceState = state,
                        // Cleared, not filtered: a locked profile's labels leave memory here.
                        privateApps = if (state == PrivateSpaceState.ConfiguredUnlocked) {
                            current.privateApps
                        } else {
                            emptyList()
                        },
                    )
                }
                if (state == PrivateSpaceState.ConfiguredUnlocked) reload()
            }
            .launchIn(viewModelScope)

        visibilityStore.isEnabled
            .onEach { enabled -> _uiState.update { it.copy(visibilityEnabled = enabled) } }
            .launchIn(viewModelScope)

        visibilityStore.hiddenPackages
            .onEach { hidden -> _uiState.update { it.copy(hiddenPackages = hidden) } }
            .launchIn(viewModelScope)

        vaultSession.state
            .onEach { lockState ->
                val unlocked = lockState == VaultLockState.Unlocked
                _uiState.update {
                    // Locking the vault also ends any in-progress visibility editing.
                    it.copy(
                        vaultUnlocked = unlocked,
                        editingVisibility = it.editingVisibility && unlocked,
                    )
                }
            }
            .launchIn(viewModelScope)

        appsRepository.observeChanges()
            .onEach { reload() }
            .launchIn(viewModelScope)

        reload()
    }

    fun onAction(action: SecureLauncherAction) {
        when (action) {
            is SecureLauncherAction.QueryChanged ->
                _uiState.update { it.copy(query = action.query) }

            is SecureLauncherAction.AppClicked -> launch(action.entry)

            is SecureLauncherAction.VisibilityToggled -> viewModelScope.launch {
                if (!_uiState.value.canEditVisibility) return@launch
                val hidden = action.packageName in _uiState.value.hiddenPackages
                visibilityStore.setHidden(action.packageName, !hidden)
            }

            SecureLauncherAction.EditVisibilityToggled -> _uiState.update { state ->
                if (!state.canEditVisibility) state else state.copy(editingVisibility = !state.editingVisibility)
            }

            SecureLauncherAction.RestoreAllIcons -> viewModelScope.launch {
                visibilityStore.restoreAll()
            }

            SecureLauncherAction.Refresh -> {
                capabilityDetector.refresh()
                viewModelScope.launch { privateAppsController.refresh() }
                reload()
            }

            SecureLauncherAction.ResultDismissed -> _uiState.update { it.copy(lastResult = null) }
        }
    }

    private fun launch(entry: LauncherAppEntry) {
        viewModelScope.launch {
            val result = appsRepository.launch(
                id = entry.id,
                isMainProfile = !entry.isPrivateProfile && !entry.isWorkProfile,
            )
            _uiState.update { it.copy(lastResult = result) }
        }
    }

    private fun reload() {
        viewModelScope.launch {
            val main = appsRepository.mainProfileApps()
            val others = appsRepository.otherProfileApps()

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    mainApps = main,
                    privateApps = others.filter { it.isPrivateProfile },
                    workApps = others.filter { it.isWorkProfile },
                )
            }
        }
    }
}
