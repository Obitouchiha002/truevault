package com.truevault.feature.scanner.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.common.format.formatBytes
import com.truevault.core.data.ScanFinding
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvEmptyState
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvPrimaryButton
import com.truevault.core.designsystem.component.TvProgressBar
import com.truevault.core.designsystem.component.TvSecondaryButton
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.component.TvTextButton
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.DeletionOutcome
import com.truevault.core.model.ScanMatchType
import com.truevault.feature.scanner.R

/**
 * Privacy scan.
 *
 * The limitation banner is permanent, not a first-run tip: the scanner can only ever see what the
 * user has granted access to, and saying so up front is what keeps the result trustworthy.
 */
@Composable
fun ScannerScreen(
    modifier: Modifier = Modifier,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        viewModel.onAction(ScannerAction.ScopeChosen(uri?.toString()))
    }

    val deletionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onAction(
            ScannerAction.DeletionResultReceived(
                approved = result.resultCode == android.app.Activity.RESULT_OK,
            ),
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ScannerEffect.RequestDeletion -> deletionLauncher.launch(
                    IntentSenderRequest.Builder(effect.intentSender).build(),
                )
            }
        }
    }

    ScannerContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onChooseFolder = { folderPicker.launch(null) },
        modifier = modifier,
    )
}

@Composable
internal fun ScannerContent(
    uiState: ScannerUiState,
    onAction: (ScannerAction) -> Unit,
    onChooseFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TvTopAppBar(title = stringResource(R.string.scanner_title))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = TvSpacing.screenHorizontal,
                end = TvSpacing.screenHorizontal,
                bottom = TvSpacing.contentBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.standard),
        ) {
            item(key = "limits") {
                TvBanner(
                    title = stringResource(R.string.scanner_limits_title),
                    text = stringResource(R.string.scanner_limits_body),
                    tone = TvBannerTone.Info,
                )
            }

            when (val stage = uiState.stage) {
                ScanStage.Idle -> item(key = "idle") {
                    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
                        TvEmptyState(
                            icon = Icons.Filled.Radar,
                            title = stringResource(R.string.scanner_idle_title),
                            description = stringResource(R.string.scanner_idle_body),
                        )
                        TvPrimaryButton(
                            text = stringResource(R.string.scanner_choose_folder),
                            onClick = onChooseFolder,
                            icon = Icons.Filled.FolderOpen,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                is ScanStage.Enumerating -> item(key = "enumerating") {
                    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.small)) {
                        TvSectionHeader(title = stringResource(R.string.scanner_reading_folder))
                        Text(
                            text = pluralStringResource(
                                R.plurals.scanner_files_found,
                                stage.filesFound,
                                stage.filesFound,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is ScanStage.Comparing -> item(key = "comparing") {
                    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.small)) {
                        TvSectionHeader(title = stringResource(R.string.scanner_comparing))
                        TvProgressBar(
                            fraction = if (stage.total == 0) {
                                0f
                            } else {
                                stage.checked.toFloat() / stage.total
                            },
                            leadingLabel = stringResource(
                                R.string.scanner_comparing_progress,
                                stage.checked,
                                stage.total,
                            ),
                        )
                    }
                }

                is ScanStage.Results -> {
                    item(key = "summary") {
                        ScanSummary(uiState = uiState, stage = stage, onRescan = onChooseFolder)
                    }

                    val visible = stage.report.findings
                        .filterNot { it.id in uiState.resolvedFindingIds }

                    items(count = visible.size, key = { visible[it].id }) { index ->
                        val finding = visible[index]
                        FindingCard(
                            finding = finding,
                            onRemove = { onAction(ScannerAction.RemoveMatchRequested(finding)) },
                            onKeep = { onAction(ScannerAction.KeepMatch(finding)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanSummary(
    uiState: ScannerUiState,
    stage: ScanStage.Results,
    onRescan: () -> Unit,
) {
    val report = stage.report
    val remaining = report.findings.count { it.id !in uiState.resolvedFindingIds }

    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
        TvSectionHeader(
            title = if (remaining == 0) {
                stringResource(R.string.scanner_clean_title)
            } else {
                pluralStringResource(R.plurals.scanner_found_title, remaining, remaining)
            },
            subtitle = pluralStringResource(
                R.plurals.scanner_examined,
                report.filesExamined,
                report.filesExamined,
            ),
        )

        if (report.truncated) {
            TvBanner(
                text = stringResource(R.string.scanner_truncated),
                tone = TvBannerTone.Warning,
            )
        }

        if (uiState.lastDeletionOutcome == DeletionOutcome.USER_CANCELLED) {
            TvBanner(
                text = stringResource(R.string.scanner_deletion_cancelled),
                tone = TvBannerTone.Info,
            )
        }

        if (remaining == 0) {
            TvBanner(
                text = stringResource(R.string.scanner_clean_body),
                tone = TvBannerTone.Success,
            )
        }

        TvSecondaryButton(
            text = stringResource(R.string.scanner_scan_another),
            onClick = onRescan,
            icon = Icons.Filled.FolderOpen,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FindingCard(
    finding: ScanFinding,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
) {
    TvCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(finding.matchType.titleRes()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(finding.matchType.explanationRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = TvSpacing.xs),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TvSpacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = finding.matchedDisplayName ?: finding.vaultItemName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatBytes(finding.matchedSizeBytes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = stringResource(R.string.scanner_confidence, finding.confidence),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = TvSpacing.xs),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TvSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
        ) {
            TvPrimaryButton(
                text = stringResource(R.string.scanner_remove_copy),
                onClick = onRemove,
                modifier = Modifier.weight(1f),
            )
            TvTextButton(text = stringResource(R.string.scanner_keep), onClick = onKeep)
        }
    }
}

private fun ScanMatchType.titleRes(): Int = when (this) {
    ScanMatchType.EXACT_DUPLICATE -> R.string.match_exact_duplicate
    ScanMatchType.POSSIBLE_DUPLICATE -> R.string.match_possible_duplicate
    ScanMatchType.ORIGINAL_REMAINS -> R.string.match_original_remains
    ScanMatchType.TRASH_STATUS_UNKNOWN -> R.string.match_trash_unknown
    ScanMatchType.CLOUD_COPY_POSSIBLE -> R.string.match_cloud_copy
    ScanMatchType.UNSUPPORTED_LOCATION -> R.string.match_unsupported_location
}

private fun ScanMatchType.explanationRes(): Int = when (this) {
    ScanMatchType.EXACT_DUPLICATE -> R.string.match_exact_duplicate_body
    ScanMatchType.POSSIBLE_DUPLICATE -> R.string.match_possible_duplicate_body
    ScanMatchType.ORIGINAL_REMAINS -> R.string.match_original_remains_body
    ScanMatchType.TRASH_STATUS_UNKNOWN -> R.string.match_trash_unknown_body
    ScanMatchType.CLOUD_COPY_POSSIBLE -> R.string.match_cloud_copy_body
    ScanMatchType.UNSUPPORTED_LOCATION -> R.string.match_unsupported_location_body
}

@Preview(name = "Scanner – idle", showBackground = true, heightDp = 760)
@Composable
private fun ScannerPreview() {
    TvPreviewSurface {
        ScannerContent(uiState = ScannerUiState(), onAction = {}, onChooseFolder = {})
    }
}
