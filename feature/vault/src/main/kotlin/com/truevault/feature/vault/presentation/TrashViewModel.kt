package com.truevault.feature.vault.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.data.VaultRepository
import com.truevault.core.data.model.VaultItem
import com.truevault.core.model.MimeCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One item in the trash, with only what the row needs. */
data class TrashItem(
    val id: String,
    val name: String,
    val category: MimeCategory,
    val sizeBytes: Long,
    val daysLeft: Int,
)

data class TrashUiState(
    val items: List<TrashItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val confirmingEmpty: Boolean = false,
    val isWorking: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
    val hasSelection: Boolean get() = selected.isNotEmpty()
    val allSelected: Boolean get() = items.isNotEmpty() && selected.size == items.size
}

sealed interface TrashAction {
    data class Toggled(val id: String) : TrashAction
    data object SelectAllToggled : TrashAction
    data object SelectionCleared : TrashAction
    data object RestoreSelected : TrashAction
    data object DeleteSelectedForever : TrashAction
    data object EmptyRequested : TrashAction
    data object EmptyDismissed : TrashAction
    data object EmptyConfirmed : TrashAction
}

/**
 * The trash.
 *
 * Everything here still exists, encrypted, exactly as it was. Restoring is a row update, not a
 * recovery — which is the whole reason deletion was made reversible in the first place.
 *
 * The only two operations that erase anything are "Delete forever" and "Empty trash", and both are
 * confirmed. Everything else on this screen is reversible.
 */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: VaultRepository,
) : ViewModel() {

    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val confirming = MutableStateFlow(false)
    private val working = MutableStateFlow(false)

    val uiState: StateFlow<TrashUiState> = combine(
        repository.observeTrash().map { entities -> entities.map(::toItem) },
        selection,
        confirming,
        working,
    ) { items, selected, confirmEmpty, isWorking ->
        TrashUiState(
            items = items,
            // A selection can outlive the item it pointed at — a restore on another screen, or the
            // retention sweep. Intersecting keeps the count honest.
            selected = selected intersect items.map { it.id }.toSet(),
            isLoading = false,
            confirmingEmpty = confirmEmpty,
            isWorking = isWorking,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TrashUiState(),
    )

    init {
        // Retention runs once per session, and can only reach rows already in the trash.
        viewModelScope.launch { repository.purgeExpiredTrash() }
    }

    fun onAction(action: TrashAction) {
        when (action) {
            is TrashAction.Toggled -> selection.update { current ->
                if (action.id in current) current - action.id else current + action.id
            }

            TrashAction.SelectAllToggled -> {
                val all = uiState.value.items.map { it.id }.toSet()
                selection.value = if (selection.value == all) emptySet() else all
            }

            TrashAction.SelectionCleared -> selection.value = emptySet()

            TrashAction.RestoreSelected -> run(repository::restoreItems)

            TrashAction.DeleteSelectedForever -> run(repository::permanentlyDelete)

            TrashAction.EmptyRequested -> confirming.value = true
            TrashAction.EmptyDismissed -> confirming.value = false

            TrashAction.EmptyConfirmed -> {
                confirming.value = false
                working.value = true
                viewModelScope.launch {
                    repository.emptyTrash()
                    selection.value = emptySet()
                    working.value = false
                }
            }
        }
    }

    private fun run(operation: suspend (List<String>) -> Int) {
        val ids = selection.value.toList()
        if (ids.isEmpty()) return

        working.value = true
        viewModelScope.launch {
            operation(ids)
            selection.value = emptySet()
            working.value = false
        }
    }

    private fun toItem(item: VaultItem): TrashItem {
        val elapsedDays = ((System.currentTimeMillis() - (item.trashedAtMillis ?: 0L)) / DAY_MILLIS).toInt()

        return TrashItem(
            id = item.id,
            name = item.displayName,
            category = item.category,
            sizeBytes = item.originalSizeBytes,
            daysLeft = (RETENTION_DAYS - elapsedDays).coerceAtLeast(0),
        )
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val RETENTION_DAYS = 30
    }
}

private fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
    value = block(value)
}
