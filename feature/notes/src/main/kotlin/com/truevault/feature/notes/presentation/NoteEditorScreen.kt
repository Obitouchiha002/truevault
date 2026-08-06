package com.truevault.feature.notes.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Notes
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.notes.R

/**
 * The note editor.
 *
 * There is no Save button. Typing pauses, the note is written; the screen closes, it is written
 * again. A save button in a notes app is a way to lose work — someone gets interrupted, the app is
 * killed, and the thing they typed was never theirs to begin with.
 */
@Composable
fun NoteEditorScreen(
    noteId: String?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(noteId) { viewModel.load(noteId) }

    // Leaving the screen for any reason — back, a share arriving, the process being backgrounded —
    // writes the last characters. Relying on the debounce alone would drop whatever was typed in
    // the final six-hundred milliseconds.
    DisposableEffect(Unit) {
        onDispose { viewModel.saveNow() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TvTopAppBar(
                title = "",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { viewModel.onAction(NoteEditorAction.PinToggled) }) {
                        Icon(
                            imageVector = if (uiState.isPinned) {
                                Icons.Filled.PushPin
                            } else {
                                Icons.Outlined.PushPin
                            },
                            contentDescription = stringResource(
                                if (uiState.isPinned) R.string.notes_unpin else R.string.notes_pin,
                            ),
                        )
                    }
                    IconButton(onClick = { viewModel.onAction(NoteEditorAction.ChecklistToggled) }) {
                        Icon(
                            imageVector = if (uiState.isChecklist) {
                                Icons.Filled.Notes
                            } else {
                                Icons.Filled.CheckBox
                            },
                            contentDescription = stringResource(R.string.notes_checklist),
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.onAction(NoteEditorAction.DeleteRequested)
                            onNavigateBack()
                        },
                        enabled = uiState.noteId != null,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.notes_delete),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = TvSpacing.screenHorizontal),
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.onAction(NoteEditorAction.TitleChanged(it)) },
                placeholder = { Text(stringResource(R.string.notes_editor_title_hint)) },
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true,
                colors = transparentFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            // Five tints, chosen inline. A colour picker behind a menu is a colour picker nobody
            // uses.
            val colourLabel = stringResource(R.string.notes_colour)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TvSpacing.small)
                    .selectableGroup()
                    .semantics { contentDescription = colourLabel },
                horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
            ) {
                (0..4).forEach { index ->
                    val scheme = MaterialTheme.colorScheme
                    val swatch = when (index) {
                        1 -> scheme.primaryContainer
                        2 -> scheme.secondaryContainer
                        3 -> scheme.tertiaryContainer
                        4 -> scheme.errorContainer
                        else -> scheme.surfaceContainer
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (uiState.colour == index) 2.dp else 1.dp,
                                color = if (uiState.colour == index) {
                                    scheme.primary
                                } else {
                                    scheme.outlineVariant
                                },
                                shape = CircleShape,
                            )
                            .selectable(
                                selected = uiState.colour == index,
                                onClick = { viewModel.onAction(NoteEditorAction.ColourChosen(index)) },
                                role = Role.RadioButton,
                            ),
                    )
                }
            }

            OutlinedTextField(
                value = uiState.body,
                onValueChange = { viewModel.onAction(NoteEditorAction.BodyChanged(it)) },
                placeholder = { Text(stringResource(R.string.notes_editor_body_hint)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = transparentFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/**
 * A borderless field.
 *
 * An editor that looks like a form makes writing feel like filling something in. The chrome is
 * removed so the page is the note.
 */
@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)
