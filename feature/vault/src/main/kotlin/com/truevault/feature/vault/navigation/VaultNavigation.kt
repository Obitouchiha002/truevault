package com.truevault.feature.vault.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.truevault.feature.vault.presentation.VaultScreen
import kotlinx.serialization.Serializable

/** Vault list. */
@Serializable
data object VaultRoute

/** A single category of the vault. Carries the category name only, never a path. */
@Serializable
data class VaultCategoryRoute(val category: String)

/** A single vault item, addressed by its opaque identifier. */
@Serializable
data class VaultItemRoute(val vaultItemId: String)

fun NavController.navigateToVault(navOptions: NavOptions? = null) = navigate(VaultRoute, navOptions)

fun NavGraphBuilder.vaultScreen(
    onAddFiles: () -> Unit,
) {
    composable<VaultRoute> {
        VaultScreen(onAddFiles = onAddFiles)
    }
}
