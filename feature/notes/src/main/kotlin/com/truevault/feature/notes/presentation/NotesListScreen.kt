package com.truevault.feature.notes.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvEmptyState
import com.truevault.core.designsystem.component.TvTopAppBar
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
@Composable
fun NotesListScreen(
    onOpenNote: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TvTopAppBar(
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

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = TvSpacing.screenHorizontal,
                    end = TvSpacing.screenHorizontal,
                    bottom = TvSpacing.contentBottom,
                ),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.small),
            ) {
                item(key = "not-encrypted") {
                    TvBanner(
                        text = stringResource(R.string.notes_not_encrypted),
                        tone = TvBannerTone.Info,
                    )
                }

                items(uiState.notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onOpen = { onOpenNote(note.id) },
                        onPinToggled = { viewModel.onAction(NotesListAction.PinToggled(note)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, onOpen: () -> Unit, onPinToggled: () -> Unit) {
    TvCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.preview.isNotBlank()) {
                    Text(
                        text = note.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = onPinToggled) {
                Icon(
                    imageVector = if (note.isPinned) {
                        Icons.Filled.PushPin
                    } else {
                        Icons.Outlined.PushPin
                    },
                    contentDescription = stringResource(
                        if (note.isPinned) R.string.notes_unpin else R.string.notes_pin,
                    ),
                    tint = if (note.isPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
