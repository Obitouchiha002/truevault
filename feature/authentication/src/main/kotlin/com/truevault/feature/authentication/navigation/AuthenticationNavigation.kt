package com.truevault.feature.authentication.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.truevault.feature.authentication.presentation.CreateVaultLockScreen
import com.truevault.feature.authentication.presentation.SecuritySettingsScreen
import com.truevault.feature.authentication.presentation.UnlockScreen
import kotlinx.serialization.Serializable

/**
 * Authentication routes.
 *
 * These carry no arguments at all. A password, PIN or recovery key must never travel through a
 * navigation argument: arguments are held in the back stack and in saved state.
 */
@Serializable
data object CreateVaultLockRoute

@Serializable
data object UnlockRoute

/**
 * Security settings live with authentication rather than with the general settings feature: every
 * control on that screen changes how the lock behaves.
 */
@Serializable
data object SecuritySettingsRoute

fun NavController.navigateToUnlock(navOptions: NavOptions? = null) = navigate(UnlockRoute, navOptions)

fun NavController.navigateToCreateVaultLock(navOptions: NavOptions? = null) =
    navigate(CreateVaultLockRoute, navOptions)

fun NavController.navigateToSecuritySettings(navOptions: NavOptions? = null) =
    navigate(SecuritySettingsRoute, navOptions)

fun NavGraphBuilder.authenticationScreens(
    onVaultCreated: () -> Unit,
    onUnlocked: () -> Unit,
    onNavigateBack: () -> Unit,
    onLocked: () -> Unit,
) {
    composable<CreateVaultLockRoute> {
        CreateVaultLockScreen(onVaultCreated = onVaultCreated)
    }
    composable<UnlockRoute> {
        UnlockScreen(onUnlocked = onUnlocked)
    }
    composable<SecuritySettingsRoute> {
        SecuritySettingsScreen(onNavigateBack = onNavigateBack, onLocked = onLocked)
    }
}
