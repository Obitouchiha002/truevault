package com.truevault.feature.notes.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvEmptyState
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TrueVaultTheme
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.notes.Note
import com.truevault.feature.notes.R

/**
 * The notes list.
 *
 * This is a real notes app, not a decoy with dead buttons. Notes can be written, edited, pinned,
 * searched and deleted, and someone who never turns on the vault still has something worth keeping
 * on their phone. A fake cover would be obvious in about ten seconds, which would make it worse than
 * no cover at all.
 *
 * The banner stating that notes are **not** encrypted is deliberate and permanent. An app that
 * offers both a notes list and a vault must never let a user assume the wrong one protects them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesListScreen(
    onOpenNote: (String?) -> Unit,
    onVaultEntryRequested: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TvTopAppBar(
                    // Long-pressing the title asks for the vault. It is a *trigger*, not
                    // authentication: it navigates to the unlock screen and the password is still
                    // required. A gesture that opened the vault by itself would mean anyone who
                    // discovered it — or found it by accident — was already inside.
                    //
                    // There is also a visible route in Settings, so forgetting this never locks
                    // anyone out of their own files.
                    titleModifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onVaultEntryRequested,
                        onLongClickLabel = stringResource(R.string.notes_open_vault),
                    ),
                    title = stringResource(R.string.notes_title),
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.onAction(
                                    if (uiState.isSearching) {
                                        NotesListAction.SearchClosed
                                    } else {
                                        NotesListAction.SearchOpened
                                    },
                                )
                            },
                        ) {
                            Icon(
                                imageVector = if (uiState.isSearching) {
                                    Icons.Filled.Close
                                } else {
                                    Icons.Filled.Search
                                },
                                contentDescription = stringResource(
                                    if (uiState.isSearching) {
                                        R.string.notes_close_search
                                    } else {
                                        R.string.notes_search
                                    },
                                ),
                            )
                        }
                    },
                )

                if (uiState.isSearching) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = { viewModel.onAction(NotesListAction.QueryChanged(it)) },
                        label = { Text(stringResource(R.string.notes_search_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TvSpacing.screenHorizontal),
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenNote(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.notes_new)) },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isEmpty -> TvEmptyState(
                icon = Icons.Outlined.EditNote,
                title = stringResource(R.string.notes_empty_title),
                description = stringResource(R.string.notes_empty_body),
                modifier = Modifier.padding(innerPadding),
            )

            uiState.hasNoResults -> Text(
                text = stringResource(R.string.notes_no_results, uiState.query),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(TvSpacing.screenHorizontal),
            )

            else -> LazyVerticalStaggeredGrid(
                // Two columns of uneven cards: a long note and a three-line list should not be
                // forced to the same height just to keep a grid tidy.
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = TvSpacing.standard,
                    end = TvSpacing.standard,
                    bottom = TvSpacing.contentBottom,
                ),
                verticalItemSpacing = TvSpacing.small,
                horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
            ) {
                // Pinned notes rise to the top under their own heading, the way Keep does it — but
                // only when some are pinned and the user is not searching, since a "PINNED" header
                // over search results would label a set the search, not the pin, chose.
                val pinned = uiState.notes.filter { it.isPinned }
                val others = uiState.notes.filterNot { it.isPinned }
                val sectioned = pinned.isNotEmpty() && others.isNotEmpty() && !uiState.isSearching

                if (sectioned) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "hdr_pinned") {
                        SectionHeader(stringResource(R.string.notes_section_pinned))
                    }
                }
                items(if (sectioned) pinned else uiState.notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onOpen = { onOpenNote(note.id) },
                        onPinToggled = { viewModel.onAction(NotesListAction.PinToggled(note)) },
                        onItemToggled = { index ->
                            viewModel.onAction(NotesListAction.ChecklistItemToggled(note.id, index))
                        },
                    )
                }
                if (sectioned) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "hdr_others") {
                        SectionHeader(stringResource(R.string.notes_section_others))
                    }
                    items(others, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onOpen = { onOpenNote(note.id) },
                            onPinToggled = { viewModel.onAction(NotesListAction.PinToggled(note)) },
                            onItemToggled = { index ->
                                viewModel.onAction(NotesListAction.ChecklistItemToggled(note.id, index))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    // Keep's small, quiet, let-spaced section label. It names the group without competing with the
    // notes for attention.
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = TvSpacing.xs,
            top = TvSpacing.small,
            bottom = TvSpacing.xs,
        ),
    )
}

@Composable
private fun NoteCard(
    note: Note,
    onOpen: () -> Unit,
    onPinToggled: () -> Unit,
    onItemToggled: (Int) -> Unit,
) {
    TvCard(
        onClick = onOpen,
        containerColor = TrueVaultTheme.noteTint(note.colour),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = note.displayTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (note.isPinned) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = stringResource(R.string.notes_pinned_label),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (note.isChecklist) {
            Spacer(Modifier.height(TvSpacing.xs))
            // Only the first few lines, and the rest is counted. A card that reprints a
            // forty-item shopping list is a wall, not a preview.
            note.checklistItems.take(CHECKLIST_PREVIEW).forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = item.isDone,
                            onValueChange = { onItemToggled(index) },
                            role = Role.Checkbox,
                        ),
                ) {
                    Checkbox(
                        checked = item.isDone,
                        onCheckedChange = null,
                        modifier = Modifier.size(30.dp),
                    )
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isDone) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val hidden = note.checklistItems.size - CHECKLIST_PREVIEW
            if (hidden > 0) {
                Text(
                    text = pluralStringResource(R.plurals.notes_more_items, hidden, hidden),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 30.dp, top = 2.dp),
                )
            }
        } else if (note.preview.isNotBlank()) {
            Spacer(Modifier.height(TvSpacing.xs))
            Text(
                text = note.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val CHECKLIST_PREVIEW = 4


