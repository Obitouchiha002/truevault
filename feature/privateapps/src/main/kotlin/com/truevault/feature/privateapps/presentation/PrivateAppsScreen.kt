package com.truevault.feature.privateapps.presentation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.capabilities.model.CapabilityActionResult
import com.truevault.core.capabilities.model.PrivateSpaceState
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvPrimaryButton
import com.truevault.core.designsystem.component.TvSecondaryButton
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.component.TvTextButton
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.privateapps.R

/**
 * Private Apps, in both product modes.
 *
 * On Android 15+ this guides the user into Android's own Private Space and is honest about what
 * that means — a separate installation, separate data, and apps that stop running while the profile
 * is locked. On Android 8–14 it says the platform does not offer this, points at the manufacturer's
 * own feature if one is actually resolvable, and makes clear the file vault is unaffected.
 *
 * There is no path through this screen that clones an app, hides one, or reports a setup that did
 * not happen.
 */
@Composable
fun PrivateAppsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivateAppsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // Whatever the system did, re-detect rather than assume. The package may or may not be gone.
        viewModel.refresh()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PrivateAppsEffect.LaunchUninstall -> {
                    // The platform's own uninstall confirmation. TrueVault never removes a package.
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.fromParts("package", effect.packageName, null)
                    }
                    runCatching { uninstallLauncher.launch(intent) }
                }
            }
        }
    }

    // Returning from system settings is exactly when Private Space may have appeared or changed.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    PrivateAppsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
internal fun PrivateAppsContent(
    uiState: PrivateAppsUiState,
    onAction: (PrivateAppsAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.showingWarnings) {
        SeparateInstallationDialog(
            onAcknowledge = { onAction(PrivateAppsAction.WarningsAcknowledged) },
            onDismiss = { onAction(PrivateAppsAction.WarningsDismissed) },
        )
    }

    uiState.removalCandidate?.let { candidate ->
        RemovalDialog(
            candidate = candidate,
            onOpenPrivateCopy = { onAction(PrivateAppsAction.OpenPrivateCopy) },
            onManualConfirm = { onAction(PrivateAppsAction.ManualVerificationConfirmed) },
            onRemove = { onAction(PrivateAppsAction.RemoveMainCopy) },
            onDismiss = { onAction(PrivateAppsAction.RemovalDismissed) },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        TvTopAppBar(
            title = stringResource(R.string.private_apps_title),
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
            TvBanner(
                title = stringResource(R.string.private_apps_honesty_title),
                text = stringResource(R.string.private_apps_honesty_body),
                tone = TvBannerTone.Info,
            )

            uiState.lastActionResult?.let { result ->
                TvBanner(
                    text = stringResource(result.messageRes()),
                    tone = if (result is CapabilityActionResult.Success) {
                        TvBannerTone.Success
                    } else {
                        TvBannerTone.Warning
                    },
                    action = {
                        TvTextButton(
                            text = stringResource(R.string.private_apps_dismiss),
                            onClick = { onAction(PrivateAppsAction.ResultDismissed) },
                        )
                    },
                )
            }

            when (val state = uiState.privateSpaceState) {
                PrivateSpaceState.NotConfigured -> GuidedSetup(uiState = uiState, onAction = onAction)

                PrivateSpaceState.ConfiguredUnlocked -> ConfiguredUnlocked(onAction = onAction)

                PrivateSpaceState.ConfiguredLocked -> TvCard {
                    Text(
                        text = stringResource(R.string.private_space_locked_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.private_space_locked_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = TvSpacing.xs),
                    )
                }

                PrivateSpaceState.RestrictedByPolicy -> TvBanner(
                    title = stringResource(R.string.private_apps_managed_title),
                    text = stringResource(R.string.private_apps_managed_body),
                    tone = TvBannerTone.Warning,
                )

                PrivateSpaceState.HomeRoleRequired -> TvBanner(
                    title = stringResource(R.string.private_apps_role_title),
                    text = stringResource(R.string.private_apps_role_body),
                    tone = TvBannerTone.Info,
                )

                PrivateSpaceState.PermissionRequired -> TvBanner(
                    text = stringResource(R.string.private_apps_permission_body),
                    tone = TvBannerTone.Warning,
                )

                is PrivateSpaceState.Error -> TvBanner(
                    text = state.safeReason,
                    tone = TvBannerTone.Warning,
                )

                PrivateSpaceState.Unsupported -> CoreModeFallback(
                    oemAvailable = uiState.capabilities.oemPrivacySettingsAvailable,
                    onAction = onAction,
                )
            }

            if (uiState.capabilities.hasWorkProfile) {
                TvBanner(
                    title = stringResource(R.string.private_apps_work_profile_title),
                    text = stringResource(R.string.private_apps_work_profile_body),
                    tone = TvBannerTone.Info,
                )
            }

            TvBanner(
                text = stringResource(R.string.private_apps_vault_independent),
                tone = TvBannerTone.Success,
            )
        }
    }
}

@Composable
private fun GuidedSetup(
    uiState: PrivateAppsUiState,
    onAction: (PrivateAppsAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
        TvSectionHeader(title = stringResource(R.string.private_apps_supported_title))

        TvCard {
            StepText(1, stringResource(R.string.private_apps_step_1))
            StepText(2, stringResource(R.string.private_apps_step_2))
            StepText(3, stringResource(R.string.private_apps_step_3))
        }

        if (!uiState.warningsAcknowledged) {
            TvPrimaryButton(
                text = stringResource(R.string.private_apps_read_first),
                onClick = { onAction(PrivateAppsAction.WarningsRequested) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TvPrimaryButton(
                text = stringResource(R.string.private_apps_open_settings),
                onClick = { onAction(PrivateAppsAction.SetupRequested) },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConfiguredUnlocked(onAction: (PrivateAppsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
        TvSectionHeader(title = stringResource(R.string.private_space_ready_title))

        TvBanner(
            text = stringResource(R.string.private_space_ready_body),
            tone = TvBannerTone.Success,
        )

        TvCard {
            StepText(1, stringResource(R.string.private_apps_install_step_1))
            StepText(2, stringResource(R.string.private_apps_install_step_2))
            StepText(3, stringResource(R.string.private_apps_install_step_3))
        }

        TvSecondaryButton(
            text = stringResource(R.string.private_apps_open_settings),
            onClick = { onAction(PrivateAppsAction.SetupRequested) },
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CoreModeFallback(
    oemAvailable: Boolean,
    onAction: (PrivateAppsAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
        TvSectionHeader(title = stringResource(R.string.private_apps_core_title))

        TvBanner(
            text = stringResource(R.string.private_apps_core_body),
            tone = TvBannerTone.Warning,
        )

        TvBanner(
            title = stringResource(R.string.oem_guidance_title),
            text = stringResource(R.string.oem_guidance_body),
            tone = TvBannerTone.Info,
        )

        if (oemAvailable) {
            TvSecondaryButton(
                text = stringResource(R.string.private_apps_check_device_options),
                onClick = { onAction(PrivateAppsAction.OpenOemSettings) },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TvBanner(
                text = stringResource(R.string.oem_manual_instructions),
                tone = TvBannerTone.Info,
            )
        }
    }
}

/**
 * The warnings a user must read before putting an app in Private Space.
 *
 * Acknowledgement is a checkbox rather than a button label, because the sentence being agreed to —
 * "this is a separate app installation" — is the one people get wrong.
 */
@Composable
private fun SeparateInstallationDialog(onAcknowledge: () -> Unit, onDismiss: () -> Unit) {
    var acknowledged by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.private_apps_warning_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.small)) {
                listOf(
                    R.string.private_apps_warning_1,
                    R.string.private_apps_warning_2,
                    R.string.private_apps_warning_3,
                    R.string.private_apps_warning_4,
                    R.string.private_apps_warning_5,
                    R.string.private_apps_warning_6,
                ).forEach { line ->
                    Text(
                        text = "•  ${stringResource(line)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    text = stringResource(R.string.private_apps_not_recommended),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = TvSpacing.small),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TvSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
                ) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                    Text(
                        text = stringResource(R.string.private_apps_acknowledge),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge, enabled = acknowledged) {
                Text(stringResource(R.string.private_apps_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.private_apps_not_now)) }
        },
    )
}

/**
 * "Verify your private copy first."
 *
 * Removal stays disabled until either a private copy was observed through a supported API, or the
 * user explicitly states they checked it themselves. The two are labelled differently on purpose —
 * the screen never describes a manual confirmation as an automatic verification.
 */
@Composable
private fun RemovalDialog(
    candidate: RemovalCandidate,
    onOpenPrivateCopy: () -> Unit,
    onManualConfirm: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.removal_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.small)) {
                Text(
                    text = stringResource(R.string.removal_body),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = stringResource(
                        if (candidate.privateCopyVerified) {
                            R.string.removal_verified
                        } else if (candidate.manuallyConfirmed) {
                            R.string.removal_manually_confirmed
                        } else {
                            R.string.removal_unverified
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (candidate.canRemove) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )

                if (!candidate.privateCopyVerified && !candidate.manuallyConfirmed) {
                    TvTextButton(
                        text = stringResource(R.string.removal_confirm_manually),
                        onClick = onManualConfirm,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRemove, enabled = candidate.canRemove) {
                Text(stringResource(R.string.removal_remove_main))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenPrivateCopy) {
                    Text(stringResource(R.string.removal_open_private))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.removal_keep_both))
                }
            }
        },
    )
}

@Composable
private fun StepText(number: Int, text: String) {
    Text(
        text = "$number.  $text",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = TvSpacing.xs),
    )
}

private fun CapabilityActionResult.messageRes(): Int = when (this) {
    CapabilityActionResult.Success -> R.string.capability_success
    CapabilityActionResult.UserCancelled -> R.string.capability_cancelled
    CapabilityActionResult.Unsupported -> R.string.capability_unsupported
    CapabilityActionResult.RoleRequired -> R.string.capability_role_required
    CapabilityActionResult.PermissionRequired -> R.string.capability_permission_required
    CapabilityActionResult.RestrictedByPolicy -> R.string.capability_restricted
    CapabilityActionResult.SettingsUnavailable -> R.string.capability_settings_unavailable
    is CapabilityActionResult.Failure -> R.string.capability_failure
}

@Preview(name = "Private apps – Core mode", showBackground = true, heightDp = 900)
@Composable
private fun PrivateAppsCorePreview() {
    TvPreviewSurface {
        PrivateAppsContent(
            uiState = PrivateAppsUiState(privateSpaceState = PrivateSpaceState.Unsupported),
            onAction = {},
            onNavigateBack = {},
        )
    }
}

@Preview(name = "Private apps – setup", showBackground = true, heightDp = 900)
@Composable
private fun PrivateAppsSetupPreview() {
    TvPreviewSurface {
        PrivateAppsContent(
            uiState = PrivateAppsUiState(privateSpaceState = PrivateSpaceState.NotConfigured),
            onAction = {},
            onNavigateBack = {},
        )
    }
}
