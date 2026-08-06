package com.truevault.feature.vault.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.truevault.feature.vault.presentation.VaultItemViewerScreen
import com.truevault.feature.vault.presentation.TrashScreen
import com.truevault.feature.vault.presentation.VaultScreen
import kotlinx.serialization.Serializable

/** Vault list. */
@Serializable
data object VaultRoute

/** A single category of the vault. Carries the category name only, never a path. */
@Serializable
data class VaultCategoryRoute(val category: String)

/** Deleted items, still encrypted, waiting out their retention window. */
@Serializable
data object VaultTrashRoute

/** A single vault item, addressed by its opaque identifier. */
@Serializable
data class VaultItemRoute(val vaultItemId: String)

fun NavController.navigateToVault(navOptions: NavOptions? = null) = navigate(VaultRoute, navOptions)

fun NavController.navigateToVaultItem(vaultItemId: String, navOptions: NavOptions? = null) =
    navigate(VaultItemRoute(vaultItemId), navOptions)

fun NavController.navigateToTrash(navOptions: NavOptions? = null) =
    navigate(VaultTrashRoute, navOptions)

fun NavGraphBuilder.vaultScreen(
    onAddFiles: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenTrash: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable<VaultRoute> {
        VaultScreen(onAddFiles = onAddFiles, onOpenItem = onOpenItem, onOpenTrash = onOpenTrash)
    }
    composable<VaultTrashRoute> {
        TrashScreen(onNavigateBack = onNavigateBack)
    }
    composable<VaultItemRoute> { entry ->
        val route = entry.toRoute<VaultItemRoute>()
        VaultItemViewerScreen(
            vaultItemId = route.vaultItemId,
            onNavigateBack = onNavigateBack,
        )
    }
}
