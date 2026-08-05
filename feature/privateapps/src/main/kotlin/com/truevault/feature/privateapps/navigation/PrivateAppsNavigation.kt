package com.truevault.feature.privateapps.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.truevault.feature.privateapps.presentation.PrivateAppsScreen
import kotlinx.serialization.Serializable

@Serializable
data object PrivateAppsRoute

fun NavController.navigateToPrivateApps(navOptions: NavOptions? = null) =
    navigate(PrivateAppsRoute, navOptions)

fun NavGraphBuilder.privateAppsScreen(onNavigateBack: () -> Unit) {
    composable<PrivateAppsRoute> {
        PrivateAppsScreen(onNavigateBack = onNavigateBack)
    }
}
