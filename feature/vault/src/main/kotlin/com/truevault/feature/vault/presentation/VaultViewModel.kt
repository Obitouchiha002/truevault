package com.truevault.feature.vault.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.truevault.core.data.VaultRepository
import com.truevault.core.data.model.VaultItem
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.core.model.VaultLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val KEY_QUERY = "vault_query"
private const val SEARCH_DEBOUNCE_MILLIS = 200L

/**
 * The vault list.
 *
 * Search is debounced and resolved against an in-memory index of decrypted names, because the names
 * in the database are encrypted and cannot be matched in SQL. Nothing about the query is persisted
 * beyond `SavedStateHandle` — it is a UI filter, and it is never logged.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val preferences: UserPreferencesDataSource,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VaultUiState(query = savedStateHandle.get<String>(KEY_QUERY).orEmpty()),
    )
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<VaultEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<VaultEffect> = _effects.asSharedFlow()

    /** Ids matching the current query, or null when there is no query. */
    private val matchingIds = MutableStateFlow<List<String>?>(null)

    val items: Flow<PagingData<VaultItem>> = combine(
        _uiState.map { it.sortOrder }.distinctUntilChanged(),
        _uiState.map { it.selectedCategory }.distinctUntilChanged(),
    ) { sortOrder, category -> sortOrder to category }
        .flatMapLatest { (sortOrder, category) ->
            vaultRepository.pagedItems(sortOrder, category)
        }
        .combine(matchingIds) { paging, ids ->
            if (ids == null) paging else paging.filter { item -> item.id in ids }
        }
        .cachedIn(viewModelScope)

    init {
        preferences.userPreferences
            .onEach { prefs ->
                _uiState.update {
                    it.copy(layout = prefs.vaultLayout, sortOrder = prefs.vaultSortOrder)
                }
            }
            .launchIn(viewModelScope)

        vaultRepository.observeItemCount()
            .onEach { count -> _uiState.update { it.copy(isLoading = false, totalItems = count) } }
            .launchIn(viewModelScope)

        _uiState.map { it.query }
            .distinctUntilChanged()
            .debounce(SEARCH_DEBOUNCE_MILLIS)
            .onEach { query -> matchingIds.value = vaultRepository.searchIds(query) }
            .launchIn(viewModelScope)
    }

    fun onAction(action: VaultAction) {
        when (action) {
            is VaultAction.QueryChanged -> {
                savedStateHandle[KEY_QUERY] = action.query
                _uiState.update { it.copy(query = action.query) }
            }

            is VaultAction.CategorySelected ->
                _uiState.update { it.copy(selectedCategory = action.category) }

            is VaultAction.SortOrderSelected -> {
                _uiState.update { it.copy(sortOrder = action.order, showSortSheet = false) }
                viewModelScope.launch { preferences.setVaultSortOrder(action.order) }
            }

            VaultAction.ToggleLayout -> {
                val next = if (uiState.value.layout == VaultLayout.GRID) {
                    VaultLayout.LIST
                } else {
                    VaultLayout.GRID
                }
                _uiState.update { it.copy(layout = next) }
                viewModelScope.launch { preferences.setVaultLayout(next) }
            }

            VaultAction.SortSheetRequested -> _uiState.update { it.copy(showSortSheet = true) }
            VaultAction.SortSheetDismissed -> _uiState.update { it.copy(showSortSheet = false) }

            is VaultAction.ItemClicked -> onItemClicked(action.id)
            is VaultAction.ItemLongPressed -> toggleSelection(action.id, enterSelection = true)

            VaultAction.SelectAll -> selectAll()
            VaultAction.ClearSelection ->
                _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }

            VaultAction.DeleteSelectedRequested ->
                _uiState.update { it.copy(pendingDeleteConfirmation = true) }

            VaultAction.DeleteSelectedDismissed ->
                _uiState.update { it.copy(pendingDeleteConfirmation = false) }

            VaultAction.DeleteSelectedConfirmed -> deleteSelected()

            VaultAction.AddFilesClicked ->
                viewModelScope.launch { _effects.emit(VaultEffect.NavigateToImport) }
        }
    }

    /** Decrypted thumbnail bytes for one item; null when there is none or it cannot be read. */
    suspend fun thumbnailBytes(id: String): ByteArray? = vaultRepository.thumbnailBytes(id)

    private fun onItemClicked(id: String) {
        if (uiState.value.selectionMode) {
            toggleSelection(id, enterSelection = false)
        } else {
            viewModelScope.launch { _effects.emit(VaultEffect.OpenItem(id)) }
        }
    }

    private fun toggleSelection(id: String, enterSelection: Boolean) {
        _uiState.update { state ->
            val next = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id
            state.copy(selectionMode = enterSelection || next.isNotEmpty(), selectedIds = next)
        }
    }

    private fun selectAll() {
        viewModelScope.launch {
            val ids = matchingIds.value ?: vaultRepository.idsSortedByName(descending = false)
            _uiState.update { it.copy(selectionMode = true, selectedIds = ids.toSet()) }
        }
    }

    /**
     * Deletes the selected vault items.
     *
     * This removes TrueVault's encrypted copies only; it never touches anything outside the vault.
     * The confirmation in front of it exists because there is no undo — the plaintext is gone with
     * the container.
     */
    private fun deleteSelected() {
        val ids = uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            val deleted = vaultRepository.deleteItems(ids)
            _uiState.update {
                it.copy(
                    selectionMode = false,
                    selectedIds = emptySet(),
                    pendingDeleteConfirmation = false,
                )
            }
            _effects.emit(VaultEffect.ItemsDeleted(deleted))
        }
    }
}
