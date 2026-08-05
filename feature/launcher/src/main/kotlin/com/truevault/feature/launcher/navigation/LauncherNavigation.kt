package com.truevault.feature.launcher.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.truevault.feature.launcher.presentation.AdvancedPrivacyScreen
import com.truevault.feature.launcher.presentation.SecureLauncherScreen
import kotlinx.serialization.Serializable

/** Secure Launcher Mode's home surface. Reached only when TrueVault holds the Home role. */
@Serializable
data object SecureLauncherRoute

/** Settings → Advanced Privacy: Secure Launcher Mode and Launcher Visibility live here. */
@Serializable
data object AdvancedPrivacyRoute

fun NavController.navigateToAdvancedPrivacy(navOptions: NavOptions? = null) =
    navigate(AdvancedPrivacyRoute, navOptions)

fun NavController.navigateToSecureLauncher(navOptions: NavOptions? = null) =
    navigate(SecureLauncherRoute, navOptions)

fun NavGraphBuilder.launcherScreens(onNavigateBack: () -> Unit) {
    composable<AdvancedPrivacyRoute> {
        AdvancedPrivacyScreen(onNavigateBack = onNavigateBack)
    }
    composable<SecureLauncherRoute> {
        SecureLauncherScreen()
    }
}
