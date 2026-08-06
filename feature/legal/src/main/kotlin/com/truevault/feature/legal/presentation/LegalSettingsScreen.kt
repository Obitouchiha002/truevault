package com.truevault.feature.legal.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.LegalDocumentKind
import com.truevault.feature.legal.R

/**
 * Settings → Legal and Privacy.
 *
 * Everything a user might want to check or undo about their data, in one place, rather than
 * scattered across a settings tree where the deletion option is three levels from the policy that
 * describes it.
 */
@Composable
fun LegalSettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenDocument: (LegalDocumentKind) -> Unit,
    onOpenDataAndPermissions: () -> Unit,
    onOpenDeleteVaultData: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenLicences: () -> Unit,
    onContact: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LegalSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TvTopAppBar(
                title = stringResource(R.string.legal_settings_title),
                onNavigateBack = onNavigateBack,
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
            verticalArrangement = Arrangement.spacedBy(TvSpacing.small),
        ) {
            if (uiState.reacceptanceRequired) {
                TvBanner(
                    text = stringResource(R.string.legal_settings_reacceptance_required),
                    tone = TvBannerTone.Warning,
                )
            }

            SettingsRow(
                title = stringResource(R.string.legal_settings_privacy_policy),
                onClick = { onOpenDocument(LegalDocumentKind.PRIVACY_POLICY) },
            )
            SettingsRow(
                title = stringResource(R.string.legal_settings_terms),
                onClick = { onOpenDocument(LegalDocumentKind.TERMS_OF_SERVICE) },
            )
            SettingsRow(
                title = stringResource(R.string.legal_settings_data_permissions),
                onClick = onOpenDataAndPermissions,
            )

            TvSectionHeader(title = stringResource(R.string.legal_settings_optional_sharing))

            // Not a toggle. There is nothing behind it in this build, and a switch that controls
            // nothing is worse than an honest sentence: it implies collection is happening and the
            // user has been given a dial to turn it down.
            TvCard {
                Text(
                    text = stringResource(R.string.legal_optional_none_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.legal_optional_none_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TvSectionHeader(title = stringResource(R.string.legal_settings_delete_data))

            SettingsRow(
                title = stringResource(R.string.legal_settings_export_backup),
                onClick = onOpenBackup,
            )
            SettingsRow(
                title = stringResource(R.string.legal_settings_delete_data),
                onClick = onOpenDeleteVaultData,
            )

            TvSectionHeader(title = stringResource(R.string.legal_settings_versions))

            TvCard {
                Text(
                    text = stringResource(
                        R.string.legal_settings_versions_body,
                        uiState.termsVersion,
                        uiState.privacyVersion,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        if (uiState.hasAcceptanceRecord) {
                            R.string.legal_settings_accepted_on
                        } else {
                            R.string.legal_settings_acceptance_missing
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsRow(
                title = stringResource(R.string.legal_settings_licences),
                onClick = onOpenLicences,
            )
            SettingsRow(
                title = stringResource(R.string.legal_settings_contact),
                onClick = onContact,
            )
        }
    }
}

@Composable
private fun SettingsRow(title: String, onClick: () -> Unit) {
    TvCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
