package com.truevault.feature.importfiles.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.truevault.feature.importfiles.presentation.ImportSourceScreen
import kotlinx.serialization.Serializable

/**
 * Import routes.
 *
 * Selected files are held by the import session in the repository layer and referenced by
 * [ImportProgressRoute.sessionId]. Source URIs never appear in a route.
 */
@Serializable
data object ImportSourceRoute

@Serializable
data class ImportReviewRoute(val sessionId: String)

@Serializable
data class ImportModeRoute(val sessionId: String)

@Serializable
data class ImportProgressRoute(val sessionId: String)

@Serializable
data class ImportResultRoute(val sessionId: String)

fun NavController.navigateToImport(navOptions: NavOptions? = null) =
    navigate(ImportSourceRoute, navOptions)

fun NavGraphBuilder.importScreens(onClose: () -> Unit) {
    composable<ImportSourceRoute> {
        ImportSourceScreen(onClose = onClose)
    }
}
