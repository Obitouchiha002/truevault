package com.truevault.feature.home.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.truevault.feature.home.presentation.HomeScreen
import kotlinx.serialization.Serializable

/**
 * Type-safe route for the dashboard.
 *
 * Routes in TrueVault carry identifiers only. A file path, URI, password, recovery key or file name
 * must never appear in a route: routes are logged by the navigation library, survive in the back
 * stack, and end up in saved state.
 */
@Serializable
data object HomeRoute

fun NavController.navigateToHome(navOptions: NavOptions? = null) = navigate(HomeRoute, navOptions)

fun NavGraphBuilder.homeScreen(
    onAddFiles: () -> Unit,
    onRunScan: () -> Unit,
    onOpenPrivateApps: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenVault: () -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(
            onAddFiles = onAddFiles,
            onRunScan = onRunScan,
            onOpenPrivateApps = onOpenPrivateApps,
            onOpenBackup = onOpenBackup,
            onOpenVault = onOpenVault,
        )
    }
}
