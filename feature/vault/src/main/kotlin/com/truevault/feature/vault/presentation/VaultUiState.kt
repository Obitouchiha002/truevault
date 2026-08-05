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
    val totalItems: Int = 0,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val showSortSheet: Boolean = false,
    val pendingDeleteConfirmation: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && totalItems == 0
    val selectionCount: Int get() = selectedIds.size
}

sealed interface VaultAction {
    data class QueryChanged(val query: String) : VaultAction
    data class CategorySelected(val category: MimeCategory?) : VaultAction
    data class SortOrderSelected(val order: VaultSortOrder) : VaultAction
    data object ToggleLayout : VaultAction
    data object SortSheetRequested : VaultAction
    data object SortSheetDismissed : VaultAction
    data class ItemClicked(val id: String) : VaultAction
    data class ItemLongPressed(val id: String) : VaultAction
    data object SelectAll : VaultAction
    data object ClearSelection : VaultAction
    data object DeleteSelectedRequested : VaultAction
    data object DeleteSelectedConfirmed : VaultAction
    data object DeleteSelectedDismissed : VaultAction
    data object AddFilesClicked : VaultAction
}

sealed interface VaultEffect {
    data object NavigateToImport : VaultEffect
    data class OpenItem(val id: String) : VaultEffect
    data class ItemsDeleted(val count: Int) : VaultEffect
}
