package com.truevault.feature.vault.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.truevault.core.common.format.formatBytes
import com.truevault.core.data.model.VaultItem
import com.truevault.core.designsystem.component.TvEmptyState
import com.truevault.core.designsystem.component.TvPrimaryButton
import com.truevault.core.designsystem.component.TvStatusPill
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvRadius
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.VaultLayout
import com.truevault.feature.vault.R

@Composable
fun VaultScreen(
    onAddFiles: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                VaultEffect.NavigateToImport -> onAddFiles()
                is VaultEffect.OpenItem -> onOpenItem(effect.id)
                is VaultEffect.ItemsDeleted -> items.refresh()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (uiState.selectionMode) {
            SelectionBar(
                count = uiState.selectionCount,
                onClear = { viewModel.onAction(VaultAction.ClearSelection) },
                onSelectAll = { viewModel.onAction(VaultAction.SelectAll) },
                onDelete = { viewModel.onAction(VaultAction.DeleteSelectedRequested) },
            )
        } else {
            TvTopAppBar(
                title = stringResource(R.string.vault_title),
                actions = {
                    // Deleted items are recoverable, so there has to be somewhere to recover them
                    // from. A trash nobody can find is the same as no trash.
                    IconButton(onClick = onOpenTrash) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.trash_open),
                        )
                    }
                    IconButton(onClick = { viewModel.onAction(VaultAction.ToggleLayout) }) {
                        Icon(
                            imageVector = if (uiState.layout == VaultLayout.GRID) {
                                Icons.AutoMirrored.Filled.ViewList
                            } else {
                                Icons.Filled.GridView
                            },
                            contentDescription = stringResource(R.string.vault_toggle_layout),
                        )
                    }
                    IconButton(onClick = { viewModel.onAction(VaultAction.SortSheetRequested) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.vault_sort),
                        )
                    }
                },
            )
        }

        OutlinedTextField(
            value = uiState.query,
            onValueChange = { viewModel.onAction(VaultAction.QueryChanged(it)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.vault_search_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TvSpacing.screenHorizontal, vertical = TvSpacing.small),
        )

        when {
            uiState.isEmpty && uiState.query.isBlank() -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                TvEmptyState(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.vault_empty_title),
                    description = stringResource(R.string.vault_empty_body),
                    action = {
                        TvPrimaryButton(
                            text = stringResource(R.string.vault_empty_action),
                            onClick = { viewModel.onAction(VaultAction.AddFilesClicked) },
                        )
                    },
                )
            }

            items.itemCount == 0 && uiState.query.isNotBlank() -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                TvEmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.vault_no_results_title),
                    description = stringResource(R.string.vault_no_results_body),
                )
            }

            else -> LazyVerticalGrid(
                columns = if (uiState.layout == VaultLayout.GRID) {
                    GridCells.Adaptive(minSize = 116.dp)
                } else {
                    GridCells.Fixed(1)
                },
                contentPadding = PaddingValues(
                    start = TvSpacing.screenHorizontal,
                    end = TvSpacing.screenHorizontal,
                    bottom = TvSpacing.contentBottom,
                ),
                horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.small),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    count = items.itemCount,
                    key = items.itemKey { it.id },
                ) { index ->
                    val item = items[index] ?: return@items
                    if (uiState.layout == VaultLayout.GRID) {
                        VaultGridCell(
                            item = item,
                            selected = item.id in uiState.selectedIds,
                            loadThumbnail = viewModel::thumbnailBytes,
                            onClick = { viewModel.onAction(VaultAction.ItemClicked(item.id)) },
                            onLongClick = {
                                viewModel.onAction(VaultAction.ItemLongPressed(item.id))
                            },
                        )
                    } else {
                        VaultListRow(
                            item = item,
                            selected = item.id in uiState.selectedIds,
                            loadThumbnail = viewModel::thumbnailBytes,
                            onClick = { viewModel.onAction(VaultAction.ItemClicked(item.id)) },
                            onLongClick = {
                                viewModel.onAction(VaultAction.ItemLongPressed(item.id))
                            },
                        )
                    }
                }
            }
        }
    }

    if (uiState.showSortSheet) {
        SortDialog(
            current = uiState.sortOrder,
            onSelect = { viewModel.onAction(VaultAction.SortOrderSelected(it)) },
            onDismiss = { viewModel.onAction(VaultAction.SortSheetDismissed) },
        )
    }

    if (uiState.pendingDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(VaultAction.DeleteSelectedDismissed) },
            title = { Text(stringResource(R.string.vault_delete_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.vault_delete_body,
                        uiState.selectionCount,
                        uiState.selectionCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(VaultAction.DeleteSelectedConfirmed) }) {
                    Text(stringResource(R.string.vault_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(VaultAction.DeleteSelectedDismissed) }) {
                    Text(stringResource(R.string.vault_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = TvSpacing.small, vertical = TvSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClear) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.vault_clear_selection))
        }
        Text(
            text = pluralStringResource(R.plurals.vault_selected, count, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSelectAll) {
            Icon(Icons.Filled.DoneAll, contentDescription = stringResource(R.string.vault_select_all))
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.vault_delete_selected),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun VaultGridCell(
    item: VaultItem,
    selected: Boolean,
    loadThumbnail: suspend (String) -> ByteArray?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(TvRadius.small))
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(TvRadius.small),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick, onClickLabel = item.displayName),
    ) {
        Box {
            VaultThumbnail(
                itemId = item.id,
                category = item.category,
                hasThumbnail = item.hasThumbnail,
                loadThumbnail = loadThumbnail,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            TvStatusPill(
                status = item.privacyStatus,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
            )
        }
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun VaultListRow(
    item: VaultItem,
    selected: Boolean,
    loadThumbnail: suspend (String) -> ByteArray?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TvRadius.small))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(onClick = onClick, onClickLabel = item.displayName)
            .padding(TvSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
    ) {
        VaultThumbnail(
            itemId = item.id,
            category = item.category,
            hasThumbnail = item.hasThumbnail,
            loadThumbnail = loadThumbnail,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatBytes(item.originalSizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TvStatusPill(status = item.privacyStatus)
    }
}

@Composable
private fun SortDialog(
    current: com.truevault.core.model.VaultSortOrder,
    onSelect: (com.truevault.core.model.VaultSortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_sort)) },
        text = {
            Column {
                com.truevault.core.model.VaultSortOrder.entries.forEach { order ->
                    TextButton(onClick = { onSelect(order) }) {
                        Text(
                            text = stringResource(order.labelRes()),
                            color = if (order == current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.vault_delete_cancel)) }
        },
    )
}

private fun com.truevault.core.model.VaultSortOrder.labelRes(): Int = when (this) {
    com.truevault.core.model.VaultSortOrder.DATE_ADDED_DESC -> R.string.vault_sort_newest
    com.truevault.core.model.VaultSortOrder.DATE_ADDED_ASC -> R.string.vault_sort_oldest
    com.truevault.core.model.VaultSortOrder.NAME_ASC -> R.string.vault_sort_name_az
    com.truevault.core.model.VaultSortOrder.NAME_DESC -> R.string.vault_sort_name_za
    com.truevault.core.model.VaultSortOrder.SIZE_DESC -> R.string.vault_sort_largest
    com.truevault.core.model.VaultSortOrder.SIZE_ASC -> R.string.vault_sort_smallest
    com.truevault.core.model.VaultSortOrder.TYPE -> R.string.vault_sort_type
}
