package com.truevault.feature.settings.presentation

import androidx.compose.runtime.Immutable
import com.truevault.core.capabilities.model.DeviceCapabilities
import com.truevault.core.model.StorageBudget
import com.truevault.core.model.StorageBudgetPolicy
import com.truevault.core.model.ThemePreference

@Immutable
data class SettingsUiState(
    val isLoading: Boolean = true,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val useDynamicColor: Boolean = false,
    val blockScreenshots: Boolean = true,
    val appVersion: String = "",
    val capabilities: DeviceCapabilities = DeviceCapabilities.Unknown,
    val storageBudget: StorageBudget = StorageBudget.DEFAULT,
    /** Bytes the vault currently occupies, measured from disk. */
    val vaultUsedBytes: Long = 0L,
    val deviceFreeBytes: Long = 0L,
) {
    /** 0..1 for the meter, or null when there is no ceiling to fill. */
    val budgetFraction: Float? get() = StorageBudgetPolicy.usedFraction(storageBudget, vaultUsedBytes)

    /**
     * Ceilings that are not below what is already stored.
     *
     * Offering a smaller one would let the user put the vault permanently over budget with no way
     * back except deleting files — and the budget is deliberately unable to delete anything.
     */
    val selectableBudgets: List<StorageBudget>
        get() = StorageBudget.entries.filter {
            it.isUnlimited || (it.limitBytes ?: 0) >= vaultUsedBytes
        }

    val isOverBudget: Boolean
        get() = storageBudget.limitBytes?.let { vaultUsedBytes > it } == true
}

sealed interface SettingsAction {
    data class ThemeSelected(val theme: ThemePreference) : SettingsAction
    data class DynamicColorToggled(val enabled: Boolean) : SettingsAction
    data object SecuritySettingsClicked : SettingsAction
    data object DeviceCapabilitiesClicked : SettingsAction
    data object AdvancedPrivacyClicked : SettingsAction
    data object AppearanceClicked : SettingsAction
    data object PrivateAppsClicked : SettingsAction
    data class StorageBudgetSelected(val budget: StorageBudget) : SettingsAction
}

sealed interface SettingsEffect {
    data object NavigateToSecuritySettings : SettingsEffect
    data object NavigateToDeviceCapabilities : SettingsEffect
    data object NavigateToAdvancedPrivacy : SettingsEffect
    data object NavigateToAppearance : SettingsEffect
    data object NavigateToPrivateApps : SettingsEffect
}
