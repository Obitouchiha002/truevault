package com.truevault.feature.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCategoryRow
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvQuickAction
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.MimeCategory
import com.truevault.feature.home.R

@Composable
fun HomeScreen(
    onAddFiles: () -> Unit,
    onRunScan: () -> Unit,
    onOpenPrivateApps: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenVault: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                HomeEffect.NavigateToImport -> onAddFiles()
                HomeEffect.NavigateToScanner -> onRunScan()
                HomeEffect.NavigateToPrivateApps -> onOpenPrivateApps()
                HomeEffect.NavigateToBackup -> onOpenBackup()
                HomeEffect.NavigateToVault -> onOpenVault()
                is HomeEffect.NavigateToCategory -> onOpenVault()
            }
        }
    }

    HomeContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
internal fun HomeContent(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TvSpacing.screenHorizontal,
            end = TvSpacing.screenHorizontal,
            top = TvSpacing.standard,
            bottom = TvSpacing.contentBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.section),
    ) {
        item(key = "greeting") {
            HomeGreeting(uiState.greeting)
        }

        item(key = "status") {
            // Phase 0 shows the honest state of a fresh install. The privacy score appears once
            // there is vault data to score (Phase 3), rather than displaying a flattering number.
            TvBanner(
                title = stringResource(R.string.home_setup_title),
                text = stringResource(R.string.home_setup_body),
                tone = TvBannerTone.Info,
            )
        }

        item(key = "quick_actions") {
            Column {
                TvSectionHeader(title = stringResource(R.string.home_quick_actions))
                QuickActionRow(onAction = onAction)
            }
        }

        item(key = "categories_header") {
            TvSectionHeader(
                title = stringResource(R.string.home_categories),
                subtitle = pluralStringResource(
                    R.plurals.home_items_secured,
                    uiState.totalItems,
                    uiState.totalItems,
                ),
            )
        }

        items(
            count = HomeCategories.size,
            key = { index -> "category_${HomeCategories[index].category.name}" },
        ) { index ->
            val entry = HomeCategories[index]
            TvCategoryRow(
                icon = entry.icon,
                title = stringResource(entry.titleRes),
                supporting = pluralStringResource(
                    R.plurals.home_items_count,
                    uiState.countFor(entry.category),
                    uiState.countFor(entry.category),
                ),
                onClick = { onAction(HomeAction.CategoryClicked(entry.category)) },
            )
        }

        item(key = "activity") {
            Column {
                TvSectionHeader(title = stringResource(R.string.home_recent_activity))
                if (uiState.recentActivity.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_no_activity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeGreeting(greeting: Greeting) {
    val greetingRes = when (greeting) {
        Greeting.MORNING -> R.string.home_greeting_morning
        Greeting.AFTERNOON -> R.string.home_greeting_afternoon
        Greeting.EVENING -> R.string.home_greeting_evening
    }

    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.xs)) {
        Text(
            text = stringResource(greetingRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun QuickActionRow(onAction: (HomeAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
    ) {
        TvQuickAction(
            icon = Icons.Filled.Add,
            label = stringResource(R.string.home_action_add_files),
            onClick = { onAction(HomeAction.AddFilesClicked) },
            modifier = Modifier.weight(1f),
        )
        TvQuickAction(
            icon = Icons.Filled.Radar,
            label = stringResource(R.string.home_action_run_scan),
            onClick = { onAction(HomeAction.RunScanClicked) },
            modifier = Modifier.weight(1f),
        )
        TvQuickAction(
            icon = Icons.Filled.Apps,
            label = stringResource(R.string.home_action_private_apps),
            onClick = { onAction(HomeAction.PrivateAppsClicked) },
            modifier = Modifier.weight(1f),
        )
        TvQuickAction(
            icon = Icons.Filled.CloudUpload,
            label = stringResource(R.string.home_action_backup),
            onClick = { onAction(HomeAction.BackupClicked) },
            modifier = Modifier.weight(1f),
        )
    }
}

private data class HomeCategoryEntry(
    val category: MimeCategory,
    val icon: ImageVector,
    val titleRes: Int,
)

private val HomeCategories = listOf(
    HomeCategoryEntry(MimeCategory.PHOTO, Icons.Filled.Image, R.string.home_category_photos),
    HomeCategoryEntry(MimeCategory.VIDEO, Icons.Filled.VideoFile, R.string.home_category_videos),
    HomeCategoryEntry(MimeCategory.DOCUMENT, Icons.Filled.Description, R.string.home_category_documents),
    HomeCategoryEntry(MimeCategory.AUDIO, Icons.Filled.AudioFile, R.string.home_category_audio),
    HomeCategoryEntry(MimeCategory.OTHER, Icons.Filled.Folder, R.string.home_category_other),
)

@Preview(name = "Home – empty vault", showBackground = true, heightDp = 900)
@Composable
private fun HomeContentPreview() {
    TvPreviewSurface {
        HomeContent(uiState = HomeUiState(isLoading = false), onAction = {})
    }
}
