package com.truevault.feature.vault.presentation

import androidx.compose.runtime.Immutable
import com.truevault.core.model.MimeCategory
import com.truevault.core.model.VaultLayout
import com.truevault.core.model.VaultSortOrder

@Immutable
data class VaultUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val selectedCategory: MimeCategory? = null,
    val layout: VaultLayout = VaultLayout.GRID,
    val sortOrder: VaultSortOrder = VaultSortOrder.DATE_ADDED_DESC,
    val isEmpty: Boolean = true,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
)

sealed interface VaultAction {
    data class QueryChanged(val query: String) : VaultAction
    data class CategorySelected(val category: MimeCategory?) : VaultAction
    data class SortOrderSelected(val order: VaultSortOrder) : VaultAction
    data object ToggleLayout : VaultAction
    data object ClearSelection : VaultAction
    data object AddFilesClicked : VaultAction
}

sealed interface VaultEffect {
    data object NavigateToImport : VaultEffect
}
