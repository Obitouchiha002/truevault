package com.truevault.feature.importfiles.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.truevault.feature.importfiles.presentation.ImportScreen
import kotlinx.serialization.Serializable

/**
 * Import routes.
 *
 * Selected files are held by the in-memory import session and referenced by a session id. Source
 * URIs never appear in a route.
 *
 * The flow's five stages live inside [ImportSourceRoute] rather than in five destinations: they
 * share one session, and a back stack that could return the user to "progress" after an import has
 * finished would describe a state that cannot exist. See `ImportScreen` for the full reasoning.
 */
@Serializable
data object ImportSourceRoute

fun NavController.navigateToImport(navOptions: NavOptions? = null) =
    navigate(ImportSourceRoute, navOptions)

fun NavGraphBuilder.importScreens(onClose: () -> Unit) {
    composable<ImportSourceRoute> {
        ImportScreen(onClose = onClose)
    }
}
