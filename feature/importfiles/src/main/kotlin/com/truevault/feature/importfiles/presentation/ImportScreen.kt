package com.truevault.feature.importfiles.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.common.format.formatBytes
import com.truevault.core.data.model.ImportReview
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvPrimaryButton
import com.truevault.core.designsystem.component.TvProgressBar
import com.truevault.core.designsystem.component.TvSecondaryButton
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.component.TvTextButton
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvMotion
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.DeletionOutcome
import com.truevault.core.model.ImportMode
import com.truevault.core.model.VaultError
import com.truevault.feature.importfiles.R
import kotlin.math.roundToInt

/**
 * The whole import flow, hosted in one destination.
 *
 * The five stages the specification lists as separate routes — source, review, mode, progress,
 * result — are stages of one destination here, deliberately. They share a single in-memory session
 * whose URIs must never enter a navigation argument, and a back stack that can return the user to
 * "progress" after an import has finished, or to "review" for files already encrypted, describes
 * states that cannot exist. One destination with an explicit stage machine models the flow honestly
 * and keeps the back gesture meaning "leave the import", which is the only sensible answer at every
 * point in it.
 */
@Composable
fun ImportScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_ITEMS),
    ) { uris ->
        viewModel.onAction(
            ImportAction.SourcesPicked(uris.map { it.toString() }, fromPhotoPicker = true),
        )
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.onAction(
            ImportAction.SourcesPicked(uris.map { it.toString() }, fromPhotoPicker = false),
        )
    }

    val deletionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onAction(
            ImportAction.DeletionResultReceived(
                approved = result.resultCode == android.app.Activity.RESULT_OK,
            ),
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ImportEffect.RequestOriginalDeletion -> deletionLauncher.launch(
                    IntentSenderRequest.Builder(effect.intentSender).build(),
                )

                ImportEffect.Close -> onClose()
            }
        }
    }

    ImportContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onPickPhotos = {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
        },
        onPickDocuments = { documentPicker.launch(arrayOf("*/*")) },
        onClose = onClose,
        modifier = modifier,
    )
}

@Composable
internal fun ImportContent(
    uiState: ImportUiState,
    onAction: (ImportAction) -> Unit,
    onPickPhotos: () -> Unit,
    onPickDocuments: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The specs are resolved here rather than inside the transition lambda, which is not a
    // composable scope and so cannot read the reduced-motion setting.
    val enterSpec = TvMotion.enterSpec<Float>()
    val exitSpec = TvMotion.exitSpec<Float>()

    Column(modifier = modifier.fillMaxSize()) {
        TvTopAppBar(
            title = stringResource(R.string.import_title),
            onNavigateBack = onClose.takeIf { uiState.stage !is ImportStage.Running },
        )

        AnimatedContent(
            targetState = uiState.stage,
            transitionSpec = { fadeIn(enterSpec) togetherWith fadeOut(exitSpec) },
            label = "importStage",
        ) { stage ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = TvSpacing.screenHorizontal,
                        end = TvSpacing.screenHorizontal,
                        bottom = TvSpacing.large,
                    ),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.standard),
            ) {
                when (stage) {
                    ImportStage.ChoosingSource -> SourceStage(
                        onPickPhotos = onPickPhotos,
                        onPickDocuments = onPickDocuments,
                    )

                    is ImportStage.Reviewing -> ReviewStage(
                        review = stage.review,
                        onConfirm = { onAction(ImportAction.ReviewConfirmed) },
                        onCancel = onClose,
                    )

                    is ImportStage.ChoosingMode -> ModeStage(
                        stage = stage,
                        onAction = onAction,
                    )

                    is ImportStage.Running -> RunningStage(
                        stage = stage,
                        onCancel = { onAction(ImportAction.CancelImport) },
                    )

                    is ImportStage.Finished -> FinishedStage(
                        stage = stage,
                        onDone = { onAction(ImportAction.Done) },
                    )
                }

                uiState.error?.let { error ->
                    // The two storage failures get different words on purpose: a full phone is
                    // solved in system settings, a full budget is solved in TrueVault in two taps.
                    // One message for both would send half the users to fix the wrong thing.
                    when (error) {
                        is VaultError.InsufficientStorage -> TvBanner(
                            title = stringResource(R.string.import_device_full_title),
                            text = stringResource(
                                R.string.import_device_full_body,
                                formatBytes(error.requiredBytes - error.availableBytes),
                            ),
                            tone = TvBannerTone.Error,
                        )

                        is VaultError.StorageBudgetReached -> TvBanner(
                            title = stringResource(R.string.import_budget_full_title),
                            text = stringResource(
                                R.string.import_budget_full_body,
                                formatBytes(error.budget.limitBytes ?: 0L),
                            ),
                            tone = TvBannerTone.Warning,
                        )

                        else -> TvBanner(
                            text = stringResource(R.string.import_generic_error),
                            tone = TvBannerTone.Error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceStage(onPickPhotos: () -> Unit, onPickDocuments: () -> Unit) {
    TvSectionHeader(
        title = stringResource(R.string.import_choose_source),
        subtitle = stringResource(R.string.import_choose_source_summary),
    )

    TvPrimaryButton(
        text = stringResource(R.string.import_photos_videos),
        onClick = onPickPhotos,
        icon = Icons.Filled.PhotoLibrary,
        modifier = Modifier.fillMaxWidth(),
    )

    TvSecondaryButton(
        text = stringResource(R.string.import_documents),
        onClick = onPickDocuments,
        icon = Icons.Filled.Description,
        modifier = Modifier.fillMaxWidth(),
    )

    TvBanner(
        title = stringResource(R.string.import_permissions_title),
        text = stringResource(R.string.import_permissions_body),
        tone = TvBannerTone.Info,
    )
}

@Composable
private fun ReviewStage(review: ImportReview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    TvSectionHeader(title = stringResource(R.string.import_review_title))

    TvCard {
        SummaryRow(
            label = stringResource(R.string.import_review_files),
            value = pluralStringResource(R.plurals.import_file_count, review.fileCount, review.fileCount),
        )
        SummaryRow(
            label = stringResource(R.string.import_review_size),
            value = formatBytes(review.totalBytes),
        )
        SummaryRow(
            label = stringResource(R.string.import_review_required),
            value = formatBytes(review.requiredBytes),
        )
        SummaryRow(
            label = stringResource(R.string.import_review_available),
            value = formatBytes(review.availableBytes),
        )
    }

    if (review.unknownSizeCount > 0) {
        TvBanner(
            text = pluralStringResource(
                R.plurals.import_unknown_size,
                review.unknownSizeCount,
                review.unknownSizeCount,
            ),
            tone = TvBannerTone.Info,
        )
    }

    if (review.unsupportedCount > 0) {
        TvBanner(
            text = pluralStringResource(
                R.plurals.import_unreadable,
                review.unsupportedCount,
                review.unsupportedCount,
            ),
            tone = TvBannerTone.Warning,
        )
    }

    if (!review.hasEnoughSpace) {
        TvBanner(
            title = stringResource(R.string.import_not_enough_space_title),
            text = stringResource(
                R.string.import_not_enough_space_body,
                formatBytes(review.shortfallBytes),
            ),
            tone = TvBannerTone.Error,
        )
    }

    TvPrimaryButton(
        text = stringResource(R.string.import_continue),
        onClick = onConfirm,
        enabled = review.hasEnoughSpace && review.fileCount > 0,
        modifier = Modifier.fillMaxWidth(),
    )
    TvTextButton(text = stringResource(R.string.import_cancel), onClick = onCancel)
}

@Composable
private fun ModeStage(stage: ImportStage.ChoosingMode, onAction: (ImportAction) -> Unit) {
    TvSectionHeader(title = stringResource(R.string.import_mode_question))

    TvCard {
        Text(
            text = stringResource(R.string.import_mode_copy_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.import_mode_copy_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = TvSpacing.xs),
        )
    }

    TvCard {
        Text(
            text = stringResource(R.string.import_mode_move_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.import_mode_move_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = TvSpacing.xs),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
    ) {
        Checkbox(
            checked = stage.rememberChoice,
            onCheckedChange = { onAction(ImportAction.RememberToggled(it)) },
        )
        Text(
            text = stringResource(R.string.import_remember_choice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    TvPrimaryButton(
        text = stringResource(R.string.import_secure_copy),
        onClick = {
            onAction(ImportAction.ModeChosen(ImportMode.SECURE_COPY, stage.rememberChoice))
        },
        modifier = Modifier.fillMaxWidth(),
    )
    TvSecondaryButton(
        text = stringResource(R.string.import_secure_move),
        onClick = {
            onAction(ImportAction.ModeChosen(ImportMode.SECURE_MOVE, stage.rememberChoice))
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RunningStage(stage: ImportStage.Running, onCancel: () -> Unit) {
    val progress = stage.progress

    TvSectionHeader(title = stringResource(R.string.import_running_title))

    TvProgressBar(
        fraction = progress.fraction,
        leadingLabel = stringResource(
            R.string.import_running_files,
            progress.completedFiles + 1,
            progress.totalFiles,
        ),
        trailingLabel = "${(progress.fraction * 100).roundToInt()}%",
        accessibilityLabel = stringResource(
            R.string.import_running_a11y,
            (progress.fraction * 100).roundToInt(),
        ),
    )

    Text(
        text = stringResource(
            R.string.import_running_bytes,
            formatBytes(progress.currentFileBytesProcessed),
            formatBytes(progress.currentFileTotalBytes),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    TvBanner(
        text = stringResource(R.string.import_running_safe),
        tone = TvBannerTone.Info,
    )

    TvSecondaryButton(
        text = stringResource(R.string.import_cancel),
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FinishedStage(stage: ImportStage.Finished, onDone: () -> Unit) {
    val result = stage.result

    TvSectionHeader(
        title = if (result.failedCount == 0 && result.cancelledCount == 0) {
            stringResource(R.string.import_result_success_title)
        } else {
            stringResource(
                R.string.import_result_partial_title,
                result.securedCount,
                result.totalCount,
            )
        },
    )

    TvCard {
        SummaryRow(
            label = stringResource(R.string.import_result_encrypted),
            value = stringResource(R.string.import_result_verified),
        )

        SummaryRow(
            label = stringResource(R.string.import_result_original),
            value = stringResource(stage.deletionOutcome.labelRes(result.mode)),
        )
    }

    when {
        stage.awaitingDeletionConfirmation -> TvBanner(
            text = stringResource(R.string.import_awaiting_deletion),
            tone = TvBannerTone.Info,
        )

        // The two honest statements the specification calls for, chosen by what actually happened.
        stage.deletionOutcome == DeletionOutcome.USER_CANCELLED -> TvBanner(
            title = stringResource(R.string.import_vault_copy_safe_title),
            text = stringResource(R.string.import_vault_copy_safe_body),
            tone = TvBannerTone.Warning,
        )

        result.mode == ImportMode.SECURE_MOVE &&
            stage.deletionOutcome != DeletionOutcome.DELETED &&
            stage.deletionOutcome != DeletionOutcome.ALREADY_MISSING -> TvBanner(
            text = stringResource(R.string.import_original_may_remain),
            tone = TvBannerTone.Warning,
        )

        result.mode == ImportMode.SECURE_COPY -> TvBanner(
            text = stringResource(R.string.import_copy_original_remains),
            tone = TvBannerTone.Info,
        )

        else -> TvBanner(
            text = stringResource(R.string.import_move_complete),
            tone = TvBannerTone.Success,
        )
    }

    if (result.failedCount > 0) {
        TvBanner(
            text = pluralStringResource(
                R.plurals.import_failed_count,
                result.failedCount,
                result.failedCount,
            ),
            tone = TvBannerTone.Error,
        )
    }

    TvPrimaryButton(
        text = stringResource(R.string.import_done),
        onClick = onDone,
        icon = Icons.Filled.CheckCircle,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TvSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun DeletionOutcome.labelRes(mode: ImportMode): Int = when (this) {
    DeletionOutcome.DELETED, DeletionOutcome.ALREADY_MISSING -> R.string.deletion_removed
    DeletionOutcome.USER_CANCELLED -> R.string.deletion_cancelled
    DeletionOutcome.PROVIDER_NOT_SUPPORTED -> R.string.deletion_not_supported
    DeletionOutcome.PERMISSION_LOST -> R.string.deletion_permission_lost
    DeletionOutcome.FAILED -> R.string.deletion_failed
    DeletionOutcome.NOT_ATTEMPTED -> when (mode) {
        ImportMode.SECURE_COPY -> R.string.deletion_kept
        ImportMode.SECURE_MOVE -> R.string.deletion_pending
    }
}

private const val MAX_PICKED_ITEMS = 100

@Preview(name = "Import – choose source", showBackground = true, heightDp = 760)
@Composable
private fun ImportSourcePreview() {
    TvPreviewSurface {
        ImportContent(
            uiState = ImportUiState(),
            onAction = {},
            onPickPhotos = {},
            onPickDocuments = {},
            onClose = {},
        )
    }
}

@Preview(name = "Import – review", showBackground = true, heightDp = 760)
@Composable
private fun ImportReviewPreview() {
    TvPreviewSurface {
        ImportContent(
            uiState = ImportUiState(
                stage = ImportStage.Reviewing(
                    sessionId = "preview",
                    review = ImportReview(
                        fileCount = 12,
                        totalBytes = 248_000_000,
                        requiredBytes = 285_000_000,
                        availableBytes = 3_400_000_000,
                        unknownSizeCount = 1,
                        duplicateOfExistingCount = 0,
                        unsupportedCount = 0,
                    ),
                ),
            ),
            onAction = {},
            onPickPhotos = {},
            onPickDocuments = {},
            onClose = {},
        )
    }
}
