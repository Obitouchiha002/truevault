package com.truevault.feature.backup.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvPrimaryButton
import com.truevault.core.designsystem.component.TvProgressBar
import com.truevault.core.designsystem.component.TvSecondaryButton
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.backup.R

/**
 * Backup, restore and recovery key.
 *
 * The warning that a backup is only as private as where it is stored is shown before the file
 * chooser opens, not after — once the archive is written to a shared folder or a cloud drive, its
 * safety depends entirely on the passphrase the user chose here.
 */
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportPassphrase = rememberTextFieldState()
    val restorePassphrase = rememberTextFieldState()
    val confirmEntry = rememberTextFieldState()

    val createArchive = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        viewModel.onAction(
            BackupAction.ExportDestinationChosen(
                uriToken = uri?.toString(),
                passphrase = exportPassphrase.text.toString().toCharArray(),
            ),
        )
        exportPassphrase.clearText()
    }

    val openArchive = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        viewModel.onAction(BackupAction.RestoreSourceChosen(uri?.toString()))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BackupEffect.CreateArchive -> createArchive.launch(effect.suggestedName)
                BackupEffect.OpenArchive -> openArchive.launch(arrayOf("*/*"))
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TvTopAppBar(
            title = stringResource(R.string.backup_title),
            onNavigateBack = onNavigateBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = TvSpacing.screenHorizontal,
                    end = TvSpacing.screenHorizontal,
                    bottom = TvSpacing.large,
                ),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.section),
        ) {
            if (uiState.error != null) {
                TvBanner(
                    text = uiState.errorDetail ?: stringResource(R.string.backup_generic_error),
                    tone = TvBannerTone.Error,
                )
            }

            when (val stage = uiState.stage) {
                BackupStage.Overview -> Overview(
                    uiState = uiState,
                    exportPassphrase = exportPassphrase,
                    onGenerateRecoveryKey = { viewModel.onAction(BackupAction.GenerateRecoveryKey) },
                    onExport = viewModel::requestExport,
                    onRestore = viewModel::requestRestore,
                )

                is BackupStage.RecoveryKeyShown -> RecoveryKeyShown(
                    formattedKey = stage.key,
                    onContinue = viewModel::startConfirmation,
                )

                is BackupStage.RecoveryKeyConfirm -> RecoveryKeyConfirm(
                    groupIndex = stage.groupIndex,
                    entry = confirmEntry,
                    onConfirm = {
                        viewModel.onAction(
                            BackupAction.ConfirmRecoveryGroup(confirmEntry.text.toString()),
                        )
                        confirmEntry.clearText()
                    },
                )

                is BackupStage.Exporting -> ProgressStage(
                    title = stringResource(R.string.backup_exporting),
                    completed = stage.completed,
                    total = stage.total,
                )

                is BackupStage.ExportFinished -> Column(
                    verticalArrangement = Arrangement.spacedBy(TvSpacing.standard),
                ) {
                    TvSectionHeader(title = stringResource(R.string.backup_export_done_title))
                    TvBanner(
                        text = pluralStringResource(
                            R.plurals.backup_export_done_body,
                            stage.itemCount,
                            stage.itemCount,
                        ),
                        tone = TvBannerTone.Success,
                    )
                    TvPrimaryButton(
                        text = stringResource(R.string.backup_done),
                        onClick = { viewModel.onAction(BackupAction.Dismiss) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is BackupStage.RestorePreview -> RestorePreview(
                    itemCount = stage.manifest.itemCount,
                    passphrase = restorePassphrase,
                    onRestore = {
                        viewModel.onAction(
                            BackupAction.RestoreConfirmed(
                                restorePassphrase.text.toString().toCharArray(),
                            ),
                        )
                        restorePassphrase.clearText()
                    },
                    onCancel = { viewModel.onAction(BackupAction.Dismiss) },
                )

                is BackupStage.Restoring -> ProgressStage(
                    title = stringResource(R.string.backup_restoring),
                    completed = stage.completed,
                    total = stage.total,
                )

                is BackupStage.RestoreFinished -> Column(
                    verticalArrangement = Arrangement.spacedBy(TvSpacing.standard),
                ) {
                    TvSectionHeader(title = stringResource(R.string.backup_restore_done_title))
                    TvCard {
                        SummaryLine(
                            stringResource(R.string.backup_restored),
                            stage.report.itemsRestored.toString(),
                        )
                        SummaryLine(
                            stringResource(R.string.backup_skipped),
                            stage.report.itemsSkippedAsDuplicate.toString(),
                        )
                        SummaryLine(
                            stringResource(R.string.backup_failed),
                            stage.report.itemsFailed.toString(),
                        )
                    }
                    if (stage.report.itemsSkippedAsDuplicate > 0) {
                        TvBanner(
                            text = stringResource(R.string.backup_skipped_explanation),
                            tone = TvBannerTone.Info,
                        )
                    }
                    TvPrimaryButton(
                        text = stringResource(R.string.backup_done),
                        onClick = { viewModel.onAction(BackupAction.Dismiss) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Overview(
    uiState: BackupUiState,
    exportPassphrase: TextFieldState,
    onGenerateRecoveryKey: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.section)) {
        Column {
            TvSectionHeader(
                title = stringResource(R.string.backup_recovery_title),
                subtitle = stringResource(R.string.backup_recovery_subtitle),
            )
            TvCard {
                Text(
                    text = stringResource(
                        if (uiState.recoveryKeyConfigured) {
                            R.string.backup_recovery_configured
                        } else {
                            R.string.backup_recovery_missing
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            TvSecondaryButton(
                text = stringResource(
                    if (uiState.recoveryKeyConfigured) {
                        R.string.backup_recovery_regenerate
                    } else {
                        R.string.backup_recovery_generate
                    },
                ),
                onClick = onGenerateRecoveryKey,
                icon = Icons.Filled.Key,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TvSpacing.small),
            )
        }

        Column {
            TvSectionHeader(title = stringResource(R.string.backup_export_title))

            TvBanner(
                title = stringResource(R.string.backup_warning_title),
                text = stringResource(R.string.backup_warning_body),
                tone = TvBannerTone.Warning,
            )

            OutlinedSecureTextField(
                state = exportPassphrase,
                label = { Text(stringResource(R.string.backup_passphrase)) },
                supportingText = { Text(stringResource(R.string.backup_passphrase_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TvSpacing.standard),
            )

            TvPrimaryButton(
                text = stringResource(R.string.backup_export_action),
                onClick = onExport,
                icon = Icons.Filled.Save,
                enabled = uiState.vaultItemCount > 0 && exportPassphrase.text.length >= 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TvSpacing.small),
            )
        }

        Column {
            TvSectionHeader(
                title = stringResource(R.string.backup_restore_title),
                subtitle = stringResource(R.string.backup_restore_subtitle),
            )
            TvSecondaryButton(
                text = stringResource(R.string.backup_restore_action),
                onClick = onRestore,
                icon = Icons.Filled.Restore,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RecoveryKeyShown(formattedKey: String, onContinue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
        TvSectionHeader(title = stringResource(R.string.backup_recovery_shown_title))

        TvCard {
            Text(
                text = formattedKey,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        TvBanner(
            title = stringResource(R.string.backup_recovery_once_title),
            text = stringResource(R.string.backup_recovery_once_body),
            tone = TvBannerTone.Warning,
        )

        TvBanner(
            text = stringResource(R.string.backup_recovery_screenshot),
            tone = TvBannerTone.Info,
        )

        TvPrimaryButton(
            text = stringResource(R.string.backup_recovery_written_down),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RecoveryKeyConfirm(
    groupIndex: Int,
    entry: TextFieldState,
    onConfirm: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
        TvSectionHeader(
            title = stringResource(R.string.backup_recovery_confirm_title),
            subtitle = stringResource(R.string.backup_recovery_confirm_subtitle, groupIndex + 1),
        )

        OutlinedTextField(
            state = entry,
            label = { Text(stringResource(R.string.backup_recovery_group, groupIndex + 1)) },
            modifier = Modifier.fillMaxWidth(),
        )

        TvPrimaryButton(
            text = stringResource(R.string.backup_recovery_confirm_action),
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RestorePreview(
    itemCount: Int,
    passphrase: TextFieldState,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
        TvSectionHeader(title = stringResource(R.string.backup_restore_preview_title))

        TvBanner(
            text = pluralStringResource(R.plurals.backup_restore_preview, itemCount, itemCount),
            tone = TvBannerTone.Info,
        )

        TvBanner(
            text = stringResource(R.string.backup_restore_no_overwrite),
            tone = TvBannerTone.Info,
        )

        OutlinedSecureTextField(
            state = passphrase,
            label = { Text(stringResource(R.string.backup_passphrase)) },
            modifier = Modifier.fillMaxWidth(),
        )

        TvPrimaryButton(
            text = stringResource(R.string.backup_restore_action),
            onClick = onRestore,
            enabled = passphrase.text.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        TvSecondaryButton(
            text = stringResource(R.string.backup_cancel),
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProgressStage(title: String, completed: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
        TvSectionHeader(title = title)
        TvProgressBar(
            fraction = if (total == 0) 0f else completed.toFloat() / total,
            leadingLabel = "$completed / $total",
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
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

@Preview(name = "Backup", showBackground = true, heightDp = 900)
@Composable
private fun BackupPreview() {
    TvPreviewSurface {
        Overview(
            uiState = BackupUiState(vaultItemCount = 12),
            exportPassphrase = TextFieldState(),
            onGenerateRecoveryKey = {},
            onExport = {},
            onRestore = {},
        )
    }
}
