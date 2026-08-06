package com.truevault.feature.legal.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.common.format.formatBytes
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvDestructiveButton
import com.truevault.core.designsystem.component.TvPrimaryButton
import com.truevault.core.designsystem.component.TvSecondaryButton
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.legal.R

/**
 * Settings → Legal and Privacy → Delete Vault Data.
 *
 * Three deliberate frictions, each of which exists because the action is irreversible:
 *
 *  1. The screen shows what will go **and what will not**. "Files you kept outside the vault" is the
 *     line most people actually need, and an app that stayed silent about it would be letting them
 *     assume a clean sweep it cannot perform.
 *  2. It asks whether a backup exists and says plainly when none ever has.
 *  3. It requires a typed phrase. A button anyone can tap twice is not a confirmation for something
 *     that cannot be undone.
 */
@Composable
fun DeleteVaultDataScreen(
    onNavigateBack: () -> Unit,
    onExportBackup: () -> Unit,
    onResetComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeleteVaultDataViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TvTopAppBar(
                title = stringResource(R.string.legal_delete_title),
                onNavigateBack = if (uiState.finished) null else onNavigateBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = TvSpacing.screenHorizontal,
                    end = TvSpacing.screenHorizontal,
                    bottom = TvSpacing.contentBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.standard),
        ) {
            if (uiState.finished) {
                FinishedContent(incomplete = uiState.incomplete, onContinue = onResetComplete)
                return@Column
            }

            TvBanner(
                text = stringResource(R.string.legal_delete_warning),
                tone = TvBannerTone.Error,
            )

            TvSectionHeader(title = stringResource(R.string.legal_delete_affected_title))
            TvCard {
                listOf(
                    formatBytes(uiState.vaultUsedBytes) + " " +
                        stringResource(R.string.legal_delete_affected_thumbnails),
                    stringResource(R.string.legal_delete_affected_temp),
                    stringResource(R.string.legal_delete_affected_database),
                    stringResource(R.string.legal_delete_affected_preferences),
                    stringResource(R.string.legal_delete_affected_keys),
                ).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = TvSpacing.xs),
                    )
                }
            }

            TvSectionHeader(title = stringResource(R.string.legal_delete_unaffected_title))
            TvCard {
                listOf(
                    R.string.legal_delete_unaffected_originals,
                    R.string.legal_delete_unaffected_shared,
                    R.string.legal_delete_unaffected_backups,
                ).forEach { res ->
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = TvSpacing.xs),
                    )
                }
            }

            TvSectionHeader(title = stringResource(R.string.legal_delete_backup_prompt))
            TvCard {
                Text(
                    text = stringResource(
                        if (uiState.hasEverBackedUp) {
                            R.string.legal_delete_backup_export
                        } else {
                            R.string.legal_delete_backup_never
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.hasEverBackedUp) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            TvSecondaryButton(
                text = stringResource(R.string.legal_delete_backup_export),
                onClick = onExportBackup,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.typedPhrase,
                onValueChange = {
                    viewModel.onAction(DeleteVaultDataAction.PhraseChanged(it))
                },
                label = { Text(stringResource(R.string.legal_delete_phrase_label)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.legal_delete_phrase),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TvDestructiveButton(
                text = stringResource(
                    if (uiState.isDeleting) {
                        R.string.legal_delete_in_progress
                    } else {
                        R.string.legal_delete_confirm
                    },
                ),
                onClick = { viewModel.onAction(DeleteVaultDataAction.DeleteConfirmed) },
                enabled = uiState.canDelete,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.legal_delete_not_account),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FinishedContent(incomplete: Boolean, onContinue: () -> Unit) {
    Text(
        text = stringResource(R.string.legal_delete_done_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Text(
        text = stringResource(R.string.legal_delete_done_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Reported, not swallowed. "Some data could not be removed" is a worse message to write and a
    // much better one to receive than a success screen that was not true.
    if (incomplete) {
        TvBanner(
            text = stringResource(R.string.legal_delete_failed),
            tone = TvBannerTone.Warning,
        )
    }

    TvPrimaryButton(
        text = stringResource(R.string.legal_delete_done_continue),
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(),
    )
}
