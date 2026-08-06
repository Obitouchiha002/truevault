package com.truevault.feature.legal.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.truevault.core.model.LegalDocumentKind
import com.truevault.feature.legal.presentation.DataAndPermissionsScreen
import com.truevault.feature.legal.presentation.DeleteVaultDataScreen
import com.truevault.feature.legal.presentation.LegalDocumentScreen
import com.truevault.feature.legal.presentation.LegalGateScreen
import com.truevault.feature.legal.presentation.LegalSettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object LegalGateRoute

@Serializable
data class LegalDocumentRoute(val kind: String)

@Serializable
data object LegalSettingsRoute

@Serializable
data object DataAndPermissionsRoute

@Serializable
data object DeleteVaultDataRoute

fun NavController.navigateToLegalGate(navOptions: NavOptions? = null) =
    navigate(LegalGateRoute, navOptions)

fun NavController.navigateToLegalDocument(
    kind: LegalDocumentKind,
    navOptions: NavOptions? = null,
) = navigate(LegalDocumentRoute(kind.name), navOptions)

fun NavController.navigateToLegalSettings(navOptions: NavOptions? = null) =
    navigate(LegalSettingsRoute, navOptions)

fun NavController.navigateToDataAndPermissions(navOptions: NavOptions? = null) =
    navigate(DataAndPermissionsRoute, navOptions)

fun NavController.navigateToDeleteVaultData(navOptions: NavOptions? = null) =
    navigate(DeleteVaultDataRoute, navOptions)

/**
 * The gate's own destination.
 *
 * Kept separate from [legalScreens] so the app can place it as the start destination without
 * pulling in the settings destinations, and so it is obvious at the call site that this one stands
 * in front of everything.
 */
fun NavGraphBuilder.legalGateScreen(
    onAccepted: () -> Unit,
    onExit: () -> Unit,
    onOpenDocument: (LegalDocumentKind) -> Unit,
) {
    composable<LegalGateRoute> {
        LegalGateScreen(
            onAccepted = onAccepted,
            onExit = onExit,
            onOpenDocument = onOpenDocument,
        )
    }
}

fun NavGraphBuilder.legalScreens(
    onNavigateBack: () -> Unit,
    onOpenDocument: (LegalDocumentKind) -> Unit,
    onOpenDataAndPermissions: () -> Unit,
    onOpenDeleteVaultData: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenLicences: () -> Unit,
    onOpenOnline: (String) -> Unit,
    onContact: () -> Unit,
    onResetComplete: () -> Unit,
) {
    composable<LegalDocumentRoute> { entry ->
        val route = entry.toRoute<LegalDocumentRoute>()
        val kind = runCatching { LegalDocumentKind.valueOf(route.kind) }
            .getOrDefault(LegalDocumentKind.PRIVACY_POLICY)

        LegalDocumentScreen(
            kind = kind,
            onNavigateBack = onNavigateBack,
            onOpenOnline = onOpenOnline,
            onContact = onContact,
        )
    }

    composable<LegalSettingsRoute> {
        LegalSettingsScreen(
            onNavigateBack = onNavigateBack,
            onOpenDocument = onOpenDocument,
            onOpenDataAndPermissions = onOpenDataAndPermissions,
            onOpenDeleteVaultData = onOpenDeleteVaultData,
            onOpenBackup = onOpenBackup,
            onOpenLicences = onOpenLicences,
            onContact = onContact,
        )
    }

    composable<DataAndPermissionsRoute> {
        DataAndPermissionsScreen(
            onNavigateBack = onNavigateBack,
            onOpenDetailedPractices = { onOpenDocument(LegalDocumentKind.PRIVACY_POLICY) },
        )
    }

    composable<DeleteVaultDataRoute> {
        DeleteVaultDataScreen(
            onNavigateBack = onNavigateBack,
            onExportBackup = onOpenBackup,
            onResetComplete = onResetComplete,
        )
    }
}
