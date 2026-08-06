package com.truevault.feature.notes.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.notes.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long typing has to pause before a draft is written. */
private const val AUTOSAVE_DELAY_MS = 600L

data class NoteEditorUiState(
    val noteId: String? = null,
    val title: String = "",
    val body: String = "",
    val isPinned: Boolean = false,
    val colour: Int = 0,
    val isChecklist: Boolean = false,
    val updatedAt: Long? = null,
    val isLoading: Boolean = true,
    val savedAtLeastOnce: Boolean = false,
)

sealed interface NoteEditorAction {
    data class TitleChanged(val title: String) : NoteEditorAction
    data class BodyChanged(val body: String) : NoteEditorAction
    data object PinToggled : NoteEditorAction
    data object ChecklistToggled : NoteEditorAction
    data class ColourChosen(val colour: Int) : NoteEditorAction
    data object DeleteRequested : NoteEditorAction
}

/**
 * The note editor.
 *
 * Typing is not written straight to the database. Each keystroke would be a disk write, and on a
 * long note that is a stutter the user feels; instead the draft is saved once typing pauses, and
 * again when the screen leaves. Structural actions — pin, delete — are written immediately, because
 * those are decisions rather than keystrokes.
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val repository: NotesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private var autosaveJob: Job? = null
    private var loaded = false

    fun load(noteId: String?) {
        if (loaded) return
        loaded = true

        if (noteId == null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            val note = repository.note(noteId)
            _uiState.update {
                it.copy(
                    noteId = note?.id,
                    title = note?.title.orEmpty(),
                    body = note?.body.orEmpty(),
                    isPinned = note?.isPinned == true,
                    colour = note?.colour ?: 0,
                    isChecklist = note?.isChecklist == true,
                    updatedAt = note?.updatedAt,
                    isLoading = false,
                )
            }
        }
    }

    fun onAction(action: NoteEditorAction) {
        when (action) {
            is NoteEditorAction.TitleChanged -> {
                _uiState.update { it.copy(title = action.title) }
                scheduleSave()
            }

            is NoteEditorAction.BodyChanged -> {
                _uiState.update { it.copy(body = action.body) }
                scheduleSave()
            }

            NoteEditorAction.PinToggled -> {
                val next = !_uiState.value.isPinned
                _uiState.update { it.copy(isPinned = next) }
                viewModelScope.launch {
                    // Save first so a brand-new note exists before it is pinned.
                    val id = persist() ?: return@launch
                    repository.setPinned(id, next)
                }
            }

            NoteEditorAction.ChecklistToggled -> {
                val next = !_uiState.value.isChecklist
                _uiState.update { it.copy(isChecklist = next) }
                viewModelScope.launch {
                    // Save first: a brand-new note has no row to convert yet.
                    val id = persist() ?: return@launch
                    repository.setChecklist(id, next)
                    reload(id)
                }
            }

            is NoteEditorAction.ColourChosen -> {
                _uiState.update { it.copy(colour = action.colour) }
                viewModelScope.launch {
                    val id = persist() ?: return@launch
                    repository.setColour(id, action.colour)
                }
            }

            NoteEditorAction.DeleteRequested -> viewModelScope.launch {
                _uiState.value.noteId?.let { repository.moveToTrash(it) }
            }
        }
    }

    /** Called when the screen goes away, so the last few characters are never lost. */
    fun saveNow() {
        autosaveJob?.cancel()
        viewModelScope.launch { persist() }
    }

    private fun scheduleSave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            persist()
        }
    }

    /** Re-reads after a conversion, because the body text on disk is now different from the field. */
    private suspend fun reload(id: String) {
        val note = repository.note(id) ?: return
        _uiState.update { it.copy(body = note.body, isChecklist = note.isChecklist) }
    }

    private suspend fun persist(): String? {
        val state = _uiState.value
        val id = repository.save(state.noteId, state.title, state.body)

        _uiState.update {
            it.copy(
                noteId = id,
                // A note emptied to nothing is discarded by the repository, so the editor stops
                // claiming to have saved something that no longer exists.
                savedAtLeastOnce = id != null,
            )
        }
        return id
    }

    override fun onCleared() {
        autosaveJob?.cancel()
    }
}
