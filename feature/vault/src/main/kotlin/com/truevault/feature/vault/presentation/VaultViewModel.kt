package com.truevault.feature.vault.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.model.VaultLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val KEY_QUERY = "vault_query"

/**
 * Vault list view model.
 *
 * Phase 0 scope: search, filter, sort and layout state, plus the empty state of a fresh vault.
 * The paged item list is connected to the Room-backed repository in Phase 2.
 *
 * The search query is kept in [SavedStateHandle] so it survives process death — it is a UI filter,
 * not user content, and it is never logged.
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VaultUiState(
            isLoading = false,
            query = savedStateHandle.get<String>(KEY_QUERY).orEmpty(),
        ),
    )
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<VaultEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<VaultEffect> = _effects.asSharedFlow()

    fun onAction(action: VaultAction) {
        when (action) {
            is VaultAction.QueryChanged -> {
                savedStateHandle[KEY_QUERY] = action.query
                _uiState.update { it.copy(query = action.query) }
            }

            is VaultAction.CategorySelected ->
                _uiState.update { it.copy(selectedCategory = action.category) }

            is VaultAction.SortOrderSelected ->
                _uiState.update { it.copy(sortOrder = action.order) }

            VaultAction.ToggleLayout -> _uiState.update {
                it.copy(
                    layout = if (it.layout == VaultLayout.GRID) VaultLayout.LIST else VaultLayout.GRID,
                )
            }

            VaultAction.ClearSelection ->
                _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }

            VaultAction.AddFilesClicked ->
                viewModelScope.launch { _effects.emit(VaultEffect.NavigateToImport) }
        }
    }
}
