package com.truevault.feature.legal.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.component.TvTextButton
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.legal.R

/**
 * Settings → Legal and Privacy → Data and Permissions.
 *
 * A dashboard built from what the app can actually do on **this** device, not from a static list.
 * The Private Apps row, for instance, says "not available on this device, so nothing is read" when
 * the capability layer reports the platform does not support it — because a generic disclosure that
 * describes a feature the device cannot run is noise, and noise is where real disclosures go to hide.
 *
 * The external-processing section is deliberately never rendered as a reassuring green badge with no
 * detail. It lists each channel and its state, so "Off" is a fact the user can check rather than a
 * colour they have to trust.
 */
@Composable
fun DataAndPermissionsScreen(
    onNavigateBack: () -> Unit,
    onOpenDetailedPractices: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataAndPermissionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TvTopAppBar(
                title = stringResource(R.string.legal_data_title),
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
            TvSectionHeader(title = stringResource(R.string.legal_data_title))

            DataCard(
                title = stringResource(R.string.legal_data_files_title),
                lines = listOf(
                    stringResource(R.string.legal_data_files_access),
                    stringResource(R.string.legal_data_files_purpose),
                    stringResource(R.string.legal_data_files_sent),
                ),
            )

            DataCard(
                title = stringResource(R.string.legal_data_biometrics_title),
                lines = listOfNotNull(
                    stringResource(R.string.legal_data_biometrics_access),
                    stringResource(R.string.legal_data_biometrics_purpose),
                    stringResource(R.string.legal_data_biometrics_note),
                ),
                unavailableNote = if (uiState.biometricsAvailable) {
                    null
                } else {
                    stringResource(R.string.legal_data_private_apps_unsupported)
                },
            )

            DataCard(
                title = stringResource(R.string.legal_data_private_apps_title),
                lines = listOf(
                    stringResource(R.string.legal_data_private_apps_access),
                    stringResource(R.string.legal_data_private_apps_purpose),
                ),
                unavailableNote = if (uiState.privateAppsAvailable) {
                    null
                } else {
                    stringResource(R.string.legal_data_private_apps_unsupported)
                },
            )

            TvSectionHeader(title = stringResource(R.string.legal_data_on_device))

            TvCard {
                uiState.onDeviceProcessing.forEach { label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = TvSpacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.legal_data_state_on_device_short),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            TvSectionHeader(title = stringResource(R.string.legal_data_external))

            TvCard {
                ExternalRow(
                    label = stringResource(R.string.legal_data_crash),
                    state = stringResource(R.string.legal_data_state_not_present),
                )
                ExternalRow(
                    label = stringResource(R.string.legal_data_analytics),
                    state = stringResource(R.string.legal_data_state_not_present),
                )
                ExternalRow(
                    label = stringResource(R.string.legal_data_cloud_backup),
                    state = stringResource(R.string.legal_data_state_not_configured),
                )
            }

            TvBanner(
                text = stringResource(R.string.legal_data_no_external),
                tone = TvBannerTone.Info,
            )

            TvTextButton(
                text = stringResource(R.string.legal_data_view_details),
                onClick = onOpenDetailedPractices,
            )
        }
    }
}

@Composable
private fun DataCard(title: String, lines: List<String>, unavailableNote: String? = null) {
    TvCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = TvSpacing.xs),
            )
        }
        unavailableNote?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = TvSpacing.xs),
            )
        }
    }
}

@Composable
private fun ExternalRow(label: String, state: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TvSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = state,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
