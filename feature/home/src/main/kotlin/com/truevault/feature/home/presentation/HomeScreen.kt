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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvAccentCard
import com.truevault.core.designsystem.component.TvCategoryRow
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvQuickAction
import com.truevault.core.designsystem.component.TvScoreRing
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.capabilities.model.PrivateSpaceState
import com.truevault.core.model.MimeCategory
import com.truevault.feature.home.R

@Composable
fun HomeScreen(
    onAddFiles: () -> Unit,
    onRunScan: () -> Unit,
    onOpenPrivateApps: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
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
                HomeEffect.NavigateToSecuritySettings -> onOpenSecuritySettings()
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
            val score = uiState.privacyScore
            if (score == null) {
                // A fresh vault has nothing to score. A flattering "100%" here would be a number
                // with no meaning behind it.
                TvBanner(
                    title = stringResource(R.string.home_setup_title),
                    text = stringResource(R.string.home_setup_body),
                    tone = TvBannerTone.Info,
                )
            } else {
                PrivacyScoreCard(score = score, onAction = onAction)
            }
        }

        item(key = "quick_actions") {
            Column {
                TvSectionHeader(title = stringResource(R.string.home_quick_actions))
                QuickActionRow(uiState = uiState, onAction = onAction)
            }
        }

        item(key = "private_apps_card") {
            PrivateAppsCard(uiState = uiState, onAction = onAction)
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
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.small)) {
                        uiState.recentActivity.forEach { entry ->
                            Text(
                                text = pluralStringResource(
                                    entry.kind.pluralRes(),
                                    entry.itemCount,
                                    entry.itemCount,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The score, with the reasons underneath it.
 *
 * The breakdown is not optional detail: a number without its reasons is decoration, and the user
 * cannot act on it.
 */
@Composable
private fun PrivacyScoreCard(
    score: com.truevault.core.model.PrivacyScore,
    onAction: (HomeAction) -> Unit,
) {
    TvAccentCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.standard),
        ) {
            TvScoreRing(
                score = score.score,
                label = stringResource(R.string.home_score_label),
                contentDescription = stringResource(R.string.home_score_a11y, score.score),
                diameter = 116.dp,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_score_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (score.hasIssues) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.home_score_issues,
                            score.itemsNeedingAttention,
                            score.itemsNeedingAttention,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = TvSpacing.xs),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.home_score_clean),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = TvSpacing.xs),
                    )
                }
            }
        }

        score.deductions.forEach { deduction ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TvSpacing.small),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(deduction.reason.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.home_score_deduction, deduction.points),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun com.truevault.core.model.PrivacyDeductionReason.labelRes(): Int = when (this) {
    com.truevault.core.model.PrivacyDeductionReason.ORIGINAL_REMAINS -> R.string.deduction_original_remains
    com.truevault.core.model.PrivacyDeductionReason.EXACT_DUPLICATE_EXISTS -> R.string.deduction_duplicate
    com.truevault.core.model.PrivacyDeductionReason.BACKUP_NOT_CONFIGURED -> R.string.deduction_backup
    com.truevault.core.model.PrivacyDeductionReason.RECOVERY_KEY_NOT_CONFIGURED -> R.string.deduction_recovery
    com.truevault.core.model.PrivacyDeductionReason.FAILED_IMPORT_UNRESOLVED -> R.string.deduction_failed_import
    com.truevault.core.model.PrivacyDeductionReason.INTEGRITY_FAILURE -> R.string.deduction_integrity
}

private fun HomeActivityItem.Kind.pluralRes(): Int = when (this) {
    HomeActivityItem.Kind.FILES_SECURED -> R.plurals.activity_files_secured
    HomeActivityItem.Kind.ORIGINAL_DELETED -> R.plurals.activity_original_deleted
    HomeActivityItem.Kind.DUPLICATE_DETECTED -> R.plurals.activity_duplicate_detected
    HomeActivityItem.Kind.BACKUP_COMPLETED -> R.plurals.activity_backup_completed
    HomeActivityItem.Kind.IMPORT_FAILED -> R.plurals.activity_import_failed
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

/**
 * Quick actions, chosen by what the device can actually do.
 *
 * Modern devices get Private Apps here. Core devices get Security Settings in that slot instead —
 * an unavailable button is not shown greyed out in the main flow, it is simply not there.
 */
@Composable
private fun QuickActionRow(uiState: HomeUiState, onAction: (HomeAction) -> Unit) {
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

        if (uiState.showsPrivateAppsAction) {
            TvQuickAction(
                icon = Icons.Filled.Apps,
                label = stringResource(R.string.home_action_private_apps),
                onClick = { onAction(HomeAction.PrivateAppsClicked) },
                modifier = Modifier.weight(1f),
            )
        } else {
            TvQuickAction(
                icon = Icons.Filled.Shield,
                label = stringResource(R.string.home_action_security),
                onClick = { onAction(HomeAction.SecuritySettingsClicked) },
                modifier = Modifier.weight(1f),
            )
        }

        TvQuickAction(
            icon = Icons.Filled.CloudUpload,
            label = stringResource(R.string.home_action_backup),
            onClick = { onAction(HomeAction.BackupClicked) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The Private Apps card.
 *
 * On Modern devices it reports the live Private Space state. On Core devices it is an informational
 * card that says the platform does not provide this and points at the manufacturer — never a button
 * that leads nowhere.
 */
@Composable
private fun PrivateAppsCard(uiState: HomeUiState, onAction: (HomeAction) -> Unit) {
    if (uiState.showsPrivateAppsAction) {
        TvCategoryRow(
            icon = Icons.Filled.Apps,
            title = stringResource(R.string.home_private_apps_title),
            supporting = stringResource(
                when (uiState.privateSpaceState) {
                    PrivateSpaceState.ConfiguredUnlocked -> R.string.home_private_ready
                    PrivateSpaceState.ConfiguredLocked -> R.string.home_private_locked
                    PrivateSpaceState.NotConfigured -> R.string.home_private_setup_required
                    PrivateSpaceState.RestrictedByPolicy -> R.string.home_private_restricted
                    else -> R.string.home_private_unknown
                },
            ),
            onClick = { onAction(HomeAction.PrivateAppsClicked) },
        )
    } else {
        TvBanner(
            title = stringResource(R.string.home_core_privacy_title),
            text = stringResource(R.string.home_core_privacy_body),
            tone = TvBannerTone.Info,
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
