package com.truevault.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.capabilities.DeviceCapabilityDetector
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.core.model.ThemePreference
import com.truevault.core.storage.VaultFileSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings view model.
 *
 * Preferences are read as a cold flow from DataStore and turned into UI state with
 * [SharingStarted.WhileSubscribed], so no work happens while the screen is off-screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesDataSource,
    private val capabilityDetector: DeviceCapabilityDetector,
    private val fileSystem: VaultFileSystem,
    @param:Dispatcher(TrueVaultDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /**
     * Vault size and free space, measured rather than remembered.
     *
     * Re-read whenever preferences change, which covers the case that matters: the user imports
     * files, comes to Settings, and sees the meter they were told about rather than the number it
     * held when the screen was first opened.
     */
    private val storageUsage = preferences.userPreferences.map {
        withContext(ioDispatcher) { fileSystem.totalVaultBytes() to fileSystem.freeSpaceBytes() }
    }

    val uiState: StateFlow<SettingsUiState> = kotlinx.coroutines.flow.combine(
        preferences.userPreferences,
        capabilityDetector.observeCapabilities(),
        storageUsage,
    ) { prefs, capabilities, usage ->
        SettingsUiState(
            isLoading = false,
            theme = prefs.theme,
            useDynamicColor = prefs.useDynamicColor,
            blockScreenshots = prefs.blockScreenshots,
            capabilities = capabilities,
            storageBudget = prefs.storageBudget,
            vaultUsedBytes = usage.first,
            deviceFreeBytes = usage.second,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    private val _effects = MutableSharedFlow<SettingsEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.ThemeSelected -> setTheme(action.theme)
            is SettingsAction.DynamicColorToggled -> setDynamicColor(action.enabled)
            SettingsAction.SecuritySettingsClicked ->
                viewModelScope.launch { _effects.emit(SettingsEffect.NavigateToSecuritySettings) }
            SettingsAction.DeviceCapabilitiesClicked ->
                viewModelScope.launch { _effects.emit(SettingsEffect.NavigateToDeviceCapabilities) }
            SettingsAction.AdvancedPrivacyClicked ->
                viewModelScope.launch { _effects.emit(SettingsEffect.NavigateToAdvancedPrivacy) }
            SettingsAction.AppearanceClicked ->
                viewModelScope.launch { _effects.emit(SettingsEffect.NavigateToAppearance) }
            SettingsAction.PrivateAppsClicked ->
                viewModelScope.launch { _effects.emit(SettingsEffect.NavigateToPrivateApps) }
            is SettingsAction.StorageBudgetSelected ->
                viewModelScope.launch { preferences.setStorageBudget(action.budget) }
        }
    }

    private fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { preferences.setTheme(theme) }
    }

    private fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setUseDynamicColor(enabled) }
    }
}
