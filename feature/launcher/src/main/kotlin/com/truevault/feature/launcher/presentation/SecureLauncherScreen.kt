package com.truevault.feature.launcher.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.capabilities.model.LauncherAppEntry
import com.truevault.core.capabilities.model.PrivateSpaceState
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.theme.TvRadius
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.launcher.R

/**
 * Secure Launcher Mode's home screen.
 *
 * It shows the main apps grid, a private-profile section when one exists, a work section when one
 * exists, and search across whichever of those are currently visible.
 *
 * The locked state is not a disabled list — it is a neutral card with no app information in it at
 * all. While Private Space is locked, TrueVault holds no labels, shows no icons and returns no
 * search results for it.
 */
@Composable
fun SecureLauncherScreen(
    modifier: Modifier = Modifier,
    viewModel: SecureLauncherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Profile state can change while the launcher is in the background.
    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(SecureLauncherAction.Refresh)
        onPauseOrDispose { }
    }

    SecureLauncherContent(uiState = uiState, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
internal fun SecureLauncherContent(
    uiState: SecureLauncherUiState,
    onAction: (SecureLauncherAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TvSpacing.screenHorizontal, vertical = TvSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { onAction(SecureLauncherAction.QueryChanged(it)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.launcher_search_hint)) },
                modifier = Modifier.weight(1f),
            )

            if (uiState.canEditVisibility) {
                IconButton(onClick = { onAction(SecureLauncherAction.EditVisibilityToggled) }) {
                    Icon(
                        imageVector = if (uiState.editingVisibility) {
                            Icons.Filled.Visibility
                        } else {
                            Icons.Filled.VisibilityOff
                        },
                        contentDescription = stringResource(R.string.launcher_edit_visibility),
                    )
                }
            }
        }

        if (!uiState.isDefaultLauncher) {
            TvBanner(
                text = stringResource(R.string.launcher_not_home_app),
                tone = TvBannerTone.Info,
                modifier = Modifier.padding(horizontal = TvSpacing.screenHorizontal),
            )
        }

        if (uiState.editingVisibility) {
            TvBanner(
                title = stringResource(R.string.launcher_editing_title),
                text = stringResource(R.string.launcher_editing_body),
                tone = TvBannerTone.Warning,
                modifier = Modifier.padding(
                    horizontal = TvSpacing.screenHorizontal,
                    vertical = TvSpacing.small,
                ),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            contentPadding = PaddingValues(
                start = TvSpacing.screenHorizontal,
                end = TvSpacing.screenHorizontal,
                bottom = TvSpacing.contentBottom,
            ),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.standard),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                TvSectionHeader(
                    title = stringResource(R.string.launcher_all_apps),
                    subtitle = pluralStringResource(
                        R.plurals.launcher_app_count,
                        uiState.visibleMainApps.size,
                        uiState.visibleMainApps.size,
                    ),
                )
            }

            items(
                count = uiState.visibleMainApps.size,
                key = { index -> "main_${uiState.visibleMainApps[index].id.packageName}" },
            ) { index ->
                val entry = uiState.visibleMainApps[index]
                AppTile(
                    entry = entry,
                    hidden = entry.id.packageName in uiState.hiddenPackages,
                    editing = uiState.editingVisibility,
                    onClick = {
                        if (uiState.editingVisibility) {
                            onAction(SecureLauncherAction.VisibilityToggled(entry.id.packageName))
                        } else {
                            onAction(SecureLauncherAction.AppClicked(entry))
                        }
                    },
                )
            }

            if (uiState.showsPrivateSection) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(top = TvSpacing.standard)) {
                        TvSectionHeader(title = stringResource(R.string.launcher_private_apps))

                        when (uiState.privateSpaceState) {
                            PrivateSpaceState.ConfiguredLocked -> LockedCard()
                            PrivateSpaceState.NotConfigured -> TvBanner(
                                text = stringResource(R.string.launcher_private_not_configured),
                                tone = TvBannerTone.Info,
                            )

                            PrivateSpaceState.RestrictedByPolicy -> TvBanner(
                                text = stringResource(R.string.launcher_private_restricted),
                                tone = TvBannerTone.Warning,
                            )

                            PrivateSpaceState.HomeRoleRequired -> TvBanner(
                                text = stringResource(R.string.launcher_private_role_required),
                                tone = TvBannerTone.Info,
                            )

                            else -> Unit
                        }
                    }
                }

                items(
                    count = uiState.visiblePrivateApps.size,
                    key = { index -> "private_${uiState.visiblePrivateApps[index].id.packageName}" },
                ) { index ->
                    val entry = uiState.visiblePrivateApps[index]
                    AppTile(
                        entry = entry,
                        hidden = false,
                        editing = false,
                        onClick = { onAction(SecureLauncherAction.AppClicked(entry)) },
                    )
                }
            }

            if (uiState.visibleWorkApps.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(top = TvSpacing.standard)) {
                        TvSectionHeader(
                            title = stringResource(R.string.launcher_work_apps),
                            subtitle = stringResource(R.string.launcher_work_subtitle),
                        )
                    }
                }

                items(
                    count = uiState.visibleWorkApps.size,
                    key = { index -> "work_${uiState.visibleWorkApps[index].id.packageName}" },
                ) { index ->
                    val entry = uiState.visibleWorkApps[index]
                    AppTile(
                        entry = entry,
                        hidden = false,
                        editing = false,
                        onClick = { onAction(SecureLauncherAction.AppClicked(entry)) },
                    )
                }
            }
        }
    }
}

/**
 * The locked-state card.
 *
 * Deliberately contains no count, no icons and no names — only the fact that the profile is locked
 * and how to unlock it. A count alone would leak how many private apps exist.
 */
@Composable
private fun LockedCard() {
    TvCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.standard),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    text = stringResource(R.string.launcher_private_locked_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.launcher_private_locked_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppTile(
    entry: LauncherAppEntry,
    hidden: Boolean,
    editing: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(TvRadius.small))
            .clickable(onClick = onClick, onClickLabel = entry.label)
            .padding(vertical = TvSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TvSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    when {
                        entry.isPrivateProfile -> MaterialTheme.colorScheme.tertiaryContainer
                        entry.isWorkProfile -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Icons are loaded by the platform per profile; the placeholder keeps the grid stable
            // while a profile is unavailable, and carries the profile badge through its colour.
            Icon(
                imageVector = Icons.Filled.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (editing && hidden) {
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    contentDescription = stringResource(R.string.launcher_hidden),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp),
                )
            }
        }

        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (hidden && editing) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Secure launcher", showBackground = true, heightDp = 800)
@Composable
private fun SecureLauncherPreview() {
    TvPreviewSurface {
        SecureLauncherContent(
            uiState = SecureLauncherUiState(
                isLoading = false,
                isDefaultLauncher = true,
                privateSpaceState = PrivateSpaceState.ConfiguredLocked,
            ),
            onAction = {},
        )
    }
}
