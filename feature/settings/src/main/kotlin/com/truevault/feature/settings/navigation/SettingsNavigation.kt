package com.truevault.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.truevault.feature.settings.presentation.DeviceCapabilitiesScreen
import com.truevault.feature.settings.presentation.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data object DeviceCapabilitiesRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) =
    navigate(SettingsRoute, navOptions)

fun NavController.navigateToDeviceCapabilities(navOptions: NavOptions? = null) =
    navigate(DeviceCapabilitiesRoute, navOptions)

fun NavGraphBuilder.settingsScreen(
    onOpenSecuritySettings: () -> Unit,
    onOpenDeviceCapabilities: () -> Unit,
    onOpenAdvancedPrivacy: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenPrivateApps: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onOpenSecuritySettings = onOpenSecuritySettings,
            onOpenDeviceCapabilities = onOpenDeviceCapabilities,
            onOpenAdvancedPrivacy = onOpenAdvancedPrivacy,
            onOpenAppearance = onOpenAppearance,
            onOpenVault = onOpenVault,
            onOpenPrivateApps = onOpenPrivateApps,
        )
    }
    composable<DeviceCapabilitiesRoute> {
        DeviceCapabilitiesScreen(onNavigateBack = onNavigateBack)
    }
}
