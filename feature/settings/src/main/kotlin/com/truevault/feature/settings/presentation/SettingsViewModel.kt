package com.truevault.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.core.model.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings view model.
 *
 * Preferences are read as a cold flow from DataStore and turned into UI state with
 * [SharingStarted.WhileSubscribed], so no work happens while the screen is off-screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesDataSource,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = preferences.userPreferences
        .map { prefs ->
            SettingsUiState(
                isLoading = false,
                theme = prefs.theme,
                useDynamicColor = prefs.useDynamicColor,
                blockScreenshots = prefs.blockScreenshots,
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
            SettingsAction.AboutSecurityClicked ->
                viewModelScope.launch { _effects.emit(SettingsEffect.NavigateToAboutSecurity) }
        }
    }

    private fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { preferences.setTheme(theme) }
    }

    private fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setUseDynamicColor(enabled) }
    }
}
