package com.truevault.feature.launcher.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.capabilities.model.TrueVaultProductMode
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvSecondaryButton
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.launcher.R

/**
 * Settings → Advanced Privacy.
 *
 * Two optional features live here and nowhere else. Neither appears in onboarding, neither is on by
 * default, and each explains what it actually does — including what it does *not* do — before it can
 * be switched on.
 */
@Composable
fun AdvancedPrivacyScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdvancedPrivacyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The Home role can be granted or revoked outside the app.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    AdvancedPrivacyContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
internal fun AdvancedPrivacyContent(
    uiState: AdvancedPrivacyUiState,
    onAction: (AdvancedPrivacyAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.showingRoleExplanation) {
        AlertDialog(
            onDismissRequest = { onAction(AdvancedPrivacyAction.RoleExplanationDismissed) },
            title = { Text(stringResource(R.string.secure_launcher_title)) },
            text = { Text(stringResource(R.string.secure_launcher_role_explanation)) },
            confirmButton = {
                TextButton(onClick = { onAction(AdvancedPrivacyAction.RoleRequestConfirmed) }) {
                    Text(stringResource(R.string.secure_launcher_set_home))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(AdvancedPrivacyAction.RoleExplanationDismissed) }) {
                    Text(stringResource(R.string.secure_launcher_not_now))
                }
            },
        )
    }

    if (uiState.showingVisibilityWarning) {
        AlertDialog(
            onDismissRequest = { onAction(AdvancedPrivacyAction.VisibilityWarningDismissed) },
            title = { Text(stringResource(R.string.launcher_visibility_title)) },
            text = { Text(stringResource(R.string.launcher_visibility_warning)) },
            confirmButton = {
                TextButton(onClick = { onAction(AdvancedPrivacyAction.VisibilityConfirmed) }) {
                    Text(stringResource(R.string.launcher_visibility_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(AdvancedPrivacyAction.VisibilityWarningDismissed) }) {
                    Text(stringResource(R.string.secure_launcher_not_now))
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        TvTopAppBar(
            title = stringResource(R.string.advanced_privacy_title),
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
            if (uiState.lastResult != null) {
                TvBanner(
                    text = stringResource(R.string.advanced_privacy_action_note),
                    tone = TvBannerTone.Info,
                )
            }

            if (uiState.capabilities.productMode == TrueVaultProductMode.MODERN) {
                Column {
                    TvSectionHeader(
                        title = stringResource(R.string.secure_launcher_title),
                        subtitle = stringResource(R.string.secure_launcher_subtitle),
                    )

                    TvCard {
                        Text(
                            text = stringResource(
                                if (uiState.capabilities.isDefaultLauncher) {
                                    R.string.secure_launcher_active
                                } else {
                                    R.string.secure_launcher_inactive
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (!uiState.capabilities.isDefaultLauncher) {
                        TvSecondaryButton(
                            text = stringResource(R.string.secure_launcher_enable),
                            onClick = { onAction(AdvancedPrivacyAction.SecureLauncherRequested) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = TvSpacing.small),
                        )
                    }
                }
            }

            Column {
                TvSectionHeader(
                    title = stringResource(R.string.launcher_visibility_title),
                    subtitle = stringResource(R.string.launcher_visibility_subtitle),
                )

                TvBanner(
                    text = stringResource(R.string.launcher_visibility_not_security),
                    tone = TvBannerTone.Warning,
                )

                TvCard(modifier = Modifier.padding(top = TvSpacing.small)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.launcher_visibility_toggle),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (uiState.capabilities.isDefaultLauncher) {
                                    pluralStringResource(
                                        R.plurals.launcher_visibility_hidden_count,
                                        uiState.hiddenCount,
                                        uiState.hiddenCount,
                                    )
                                } else {
                                    stringResource(R.string.launcher_visibility_needs_home)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.visibilityEnabled,
                            // Only available while TrueVault is the active Home app: hiding icons in
                            // a launcher nobody is using would be theatre.
                            enabled = uiState.capabilities.isDefaultLauncher,
                            onCheckedChange = { enabled ->
                                onAction(
                                    if (enabled) {
                                        AdvancedPrivacyAction.VisibilityRequested
                                    } else {
                                        AdvancedPrivacyAction.VisibilityDisabled
                                    },
                                )
                            },
                        )
                    }
                }

                if (uiState.hiddenCount > 0) {
                    TvSecondaryButton(
                        text = stringResource(R.string.launcher_visibility_restore_all),
                        onClick = { onAction(AdvancedPrivacyAction.RestoreAllIcons) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TvSpacing.small),
                    )
                }
            }
        }
    }
}

@Preview(name = "Advanced privacy", showBackground = true, heightDp = 800)
@Composable
private fun AdvancedPrivacyPreview() {
    TvPreviewSurface {
        AdvancedPrivacyContent(
            uiState = AdvancedPrivacyUiState(),
            onAction = {},
            onNavigateBack = {},
        )
    }
}
