package com.truevault.feature.settings.presentation

import androidx.compose.runtime.Immutable
import com.truevault.core.model.ThemePreference

@Immutable
data class SettingsUiState(
    val isLoading: Boolean = true,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val useDynamicColor: Boolean = false,
    val blockScreenshots: Boolean = true,
    val appVersion: String = "",
)

sealed interface SettingsAction {
    data class ThemeSelected(val theme: ThemePreference) : SettingsAction
    data class DynamicColorToggled(val enabled: Boolean) : SettingsAction
    data object SecuritySettingsClicked : SettingsAction
    data object AboutSecurityClicked : SettingsAction
}

sealed interface SettingsEffect {
    data object NavigateToSecuritySettings : SettingsEffect
    data object NavigateToAboutSecurity : SettingsEffect
}
