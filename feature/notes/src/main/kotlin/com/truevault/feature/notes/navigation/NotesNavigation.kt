package com.truevault.feature.notes.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.truevault.feature.notes.presentation.NoteEditorScreen
import com.truevault.feature.notes.presentation.NotesListScreen
import kotlinx.serialization.Serializable

@Serializable
data object NotesRoute

/** A blank id means a new note. The id is a random UUID and reveals nothing about the content. */
@Serializable
data class NoteEditorRoute(val noteId: String = "")

fun NavController.navigateToNotes(navOptions: NavOptions? = null) =
    navigate(NotesRoute, navOptions)

fun NavController.navigateToNoteEditor(noteId: String?, navOptions: NavOptions? = null) =
    navigate(NoteEditorRoute(noteId.orEmpty()), navOptions)

fun NavGraphBuilder.notesScreens(
    onOpenNote: (String?) -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable<NotesRoute> {
        NotesListScreen(onOpenNote = onOpenNote)
    }

    composable<NoteEditorRoute> { entry ->
        val route = entry.toRoute<NoteEditorRoute>()
        NoteEditorScreen(
            noteId = route.noteId.ifBlank { null },
            onNavigateBack = onNavigateBack,
        )
    }
}
