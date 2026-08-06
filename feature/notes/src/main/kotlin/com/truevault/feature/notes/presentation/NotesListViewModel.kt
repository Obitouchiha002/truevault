package com.truevault.feature.notes.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.notes.Note
import com.truevault.core.notes.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesListUiState(
    val notes: List<Note> = emptyList(),
    val query: String = "",
    val isSearching: Boolean = false,
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && notes.isEmpty() && query.isBlank()
    val hasNoResults: Boolean get() = !isLoading && notes.isEmpty() && query.isNotBlank()
}

sealed interface NotesListAction {
    data class QueryChanged(val query: String) : NotesListAction
    data object SearchOpened : NotesListAction
    data object SearchClosed : NotesListAction
    data class PinToggled(val note: Note) : NotesListAction
    data class Deleted(val note: Note) : NotesListAction
    data class UndoDelete(val noteId: String) : NotesListAction
    data class ChecklistItemToggled(val noteId: String, val index: Int) : NotesListAction
}

/**
 * The notes list.
 *
 * Search filters the same query the list already uses, rather than maintaining a separate index.
 * That keeps one guarantee trivially true: notes search reads the notes table and nothing else, so
 * it cannot surface vault content even by accident.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val repository: NotesRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val searching = MutableStateFlow(false)

    val uiState: StateFlow<NotesListUiState> = combine(
        query,
        searching,
        query.flatMapLatest { q ->
            if (q.isBlank()) repository.observeNotes() else repository.search(q)
        },
    ) { currentQuery, isSearching, notes ->
        NotesListUiState(
            notes = notes,
            query = currentQuery,
            isSearching = isSearching,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotesListUiState(),
    )

    init {
        // Trash retention runs once per launch, and can only touch notes already in the trash.
        viewModelScope.launch { repository.purgeExpiredTrash() }
    }

    fun onAction(action: NotesListAction) {
        when (action) {
            is NotesListAction.QueryChanged -> query.value = action.query
            NotesListAction.SearchOpened -> searching.value = true
            NotesListAction.SearchClosed -> {
                searching.value = false
                query.value = ""
            }

            is NotesListAction.PinToggled -> viewModelScope.launch {
                repository.setPinned(action.note.id, !action.note.isPinned)
            }

            // Deleting moves to the trash, so "Undo" is a real restore rather than a re-creation
            // that would lose the note's history and its original date.
            is NotesListAction.Deleted -> viewModelScope.launch {
                repository.moveToTrash(action.note.id)
            }

            is NotesListAction.UndoDelete -> viewModelScope.launch {
                repository.restore(action.noteId)
            }

            // Ticking a line from the list is the whole point of a checklist card — making the
            // user open the note first would defeat it.
            is NotesListAction.ChecklistItemToggled -> viewModelScope.launch {
                repository.toggleChecklistItem(action.noteId, action.index)
            }
        }
    }
}
