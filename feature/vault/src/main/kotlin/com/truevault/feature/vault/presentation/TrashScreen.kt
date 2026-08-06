package com.truevault.feature.vault.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.common.format.formatBytes
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvDestructiveButton
import com.truevault.core.designsystem.component.TvEmptyState
import com.truevault.core.designsystem.component.TvSecondaryButton
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.vault.R

/**
 * Vault → Trash.
 *
 * Everything on this screen still exists, encrypted and untouched. That is the point: deleting a
 * vault item used to erase the container immediately, so one mis-tap destroyed a file in an app
 * whose entire job is not losing the user's data.
 *
 * Only two controls here erase anything, and both say so plainly. Everything else is reversible.
 */
@Composable
fun TrashScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TvTopAppBar(
                title = stringResource(R.string.trash_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    if (uiState.items.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onAction(TrashAction.EmptyRequested) }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteForever,
                                contentDescription = stringResource(R.string.trash_empty),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isEmpty) {
            TvEmptyState(
                icon = Icons.Outlined.DeleteOutline,
                title = stringResource(R.string.trash_empty_title),
                description = stringResource(R.string.trash_empty_body),
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = TvSpacing.screenHorizontal,
                    end = TvSpacing.screenHorizontal,
                    bottom = TvSpacing.standard,
                ),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.small),
            ) {
                item(key = "note") {
                    TvBanner(
                        text = stringResource(R.string.trash_retention_note),
                        tone = TvBannerTone.Info,
                    )
                }

                items(uiState.items, key = { it.id }) { item ->
                    TrashRow(
                        item = item,
                        selected = item.id in uiState.selected,
                        onToggled = { viewModel.onAction(TrashAction.Toggled(item.id)) },
                    )
                }
            }

            if (uiState.hasSelection) {
                SelectionBar(
                    count = uiState.selected.size,
                    enabled = !uiState.isWorking,
                    onRestore = { viewModel.onAction(TrashAction.RestoreSelected) },
                    onDeleteForever = { viewModel.onAction(TrashAction.DeleteSelectedForever) },
                )
            }
        }
    }

    if (uiState.confirmingEmpty) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(TrashAction.EmptyDismissed) },
            title = { Text(stringResource(R.string.trash_empty_confirm_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.trash_empty_confirm_body,
                        uiState.items.size,
                        uiState.items.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(TrashAction.EmptyConfirmed) }) {
                    Text(stringResource(R.string.trash_delete_forever))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(TrashAction.EmptyDismissed) }) {
                    Text(stringResource(R.string.trash_keep))
                }
            },
        )
    }
}

@Composable
private fun TrashRow(item: TrashItem, selected: Boolean, onToggled: () -> Unit) {
    TvCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = selected, onValueChange = { onToggled() }, role = Role.Checkbox),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
        ) {
            Checkbox(checked = selected, onCheckedChange = null)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // The countdown is the useful fact here, not the deletion date: "8 days left"
                    // tells someone whether they need to act today.
                    text = pluralStringResource(
                        R.plurals.trash_row_summary,
                        item.daysLeft,
                        formatBytes(item.sizeBytes),
                        item.daysLeft,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.daysLeft <= 3) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    enabled: Boolean,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(TvSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.small),
    ) {
        Text(
            text = pluralStringResource(R.plurals.trash_selected, count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Restore is the primary action, because it is the one that cannot go wrong.
        TvSecondaryButton(
            text = stringResource(R.string.trash_restore),
            onClick = onRestore,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        TvDestructiveButton(
            text = stringResource(R.string.trash_delete_forever),
            onClick = onDeleteForever,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
