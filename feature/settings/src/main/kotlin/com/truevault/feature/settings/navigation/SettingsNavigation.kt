package com.truevault.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.truevault.feature.settings.presentation.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data object SecuritySettingsRoute

@Serializable
data object AboutSecurityRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) =
    navigate(SettingsRoute, navOptions)

fun NavGraphBuilder.settingsScreen(
    onOpenSecuritySettings: () -> Unit,
    onOpenAboutSecurity: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onOpenSecuritySettings = onOpenSecuritySettings,
            onOpenAboutSecurity = onOpenAboutSecurity,
        )
    }
}
