package com.truevault.feature.authentication.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.truevault.feature.authentication.presentation.CreateVaultLockScreen
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

fun NavController.navigateToUnlock(navOptions: NavOptions? = null) = navigate(UnlockRoute, navOptions)

fun NavController.navigateToCreateVaultLock(navOptions: NavOptions? = null) =
    navigate(CreateVaultLockRoute, navOptions)

fun NavGraphBuilder.authenticationScreens(
    onVaultCreated: () -> Unit,
    onUnlocked: () -> Unit,
) {
    composable<CreateVaultLockRoute> {
        CreateVaultLockScreen(onVaultCreated = onVaultCreated)
    }
    composable<UnlockRoute> {
        UnlockScreen(onUnlocked = onUnlocked)
    }
}
