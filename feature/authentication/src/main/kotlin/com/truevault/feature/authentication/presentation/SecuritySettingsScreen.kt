package com.truevault.feature.authentication.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvSecondaryButton
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.AutoLockDuration
import com.truevault.feature.authentication.R
import com.truevault.feature.authentication.domain.BiometricCapability

@Composable
fun SecuritySettingsScreen(
    onNavigateBack: () -> Unit,
    onLocked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SecuritySettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val runBiometricPrompt = rememberBiometricPromptRunner()

    val promptTitle = stringResource(R.string.create_lock_biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.create_lock_biometric_prompt_subtitle)
    val promptNegative = stringResource(R.string.create_lock_biometric_prompt_negative)
    val promptUnavailable = stringResource(R.string.biometric_unavailable)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SecuritySettingsEffect.RequestBiometricEnrolment -> runBiometricPrompt(
                    BiometricPromptRequest(
                        cipher = effect.cipher,
                        title = promptTitle,
                        subtitle = promptSubtitle,
                        negativeButton = promptNegative,
                        unavailableMessage = promptUnavailable,
                        onResult = { result ->
                            when (result) {
                                is BiometricPromptResult.Succeeded -> viewModel.onAction(
                                    SecuritySettingsAction.BiometricEnrolled(result.cipher),
                                )
                                is BiometricPromptResult.Cancelled,
                                is BiometricPromptResult.Error,
                                -> viewModel.onAction(
                                    SecuritySettingsAction.BiometricEnrolmentCancelled,
                                )

                                BiometricPromptResult.Failed -> Unit
                            }
                        },
                    ),
                )

                SecuritySettingsEffect.Locked -> onLocked()
            }
        }
    }

    SecuritySettingsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
internal fun SecuritySettingsContent(
    uiState: SecuritySettingsUiState,
    onAction: (SecuritySettingsAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TvTopAppBar(
            title = stringResource(R.string.security_settings_title),
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
            // Stated plainly rather than implied. On a device without a secure element the vault is
            // still encrypted, but the protection is not the same, and the user is entitled to know.
            TvBanner(
                title = stringResource(
                    if (uiState.hardwareBackedKeystore) {
                        R.string.security_keystore_hardware_title
                    } else {
                        R.string.security_keystore_software_title
                    },
                ),
                text = stringResource(
                    if (uiState.hardwareBackedKeystore) {
                        R.string.security_keystore_hardware_body
                    } else {
                        R.string.security_keystore_software_body
                    },
                ),
                tone = if (uiState.hardwareBackedKeystore) {
                    TvBannerTone.Success
                } else {
                    TvBannerTone.Warning
                },
            )

            Column {
                TvSectionHeader(
                    title = stringResource(R.string.security_auto_lock),
                    subtitle = stringResource(R.string.security_auto_lock_summary),
                )
                TvCard {
                    Column(modifier = Modifier.selectableGroup()) {
                        AutoLockDuration.entries.forEach { duration ->
                            AutoLockRow(
                                duration = duration,
                                selected = uiState.autoLockDuration == duration,
                                onSelected = {
                                    onAction(SecuritySettingsAction.AutoLockSelected(duration))
                                },
                            )
                        }
                    }
                }
            }

            Column {
                TvSectionHeader(title = stringResource(R.string.security_protection))
                TvCard {
                    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
                        SwitchRow(
                            title = stringResource(R.string.security_lock_on_screen_off),
                            summary = stringResource(R.string.security_lock_on_screen_off_summary),
                            checked = uiState.lockOnScreenOff,
                            onCheckedChange = {
                                onAction(SecuritySettingsAction.LockOnScreenOffToggled(it))
                            },
                        )
                        SwitchRow(
                            title = stringResource(R.string.security_block_screenshots),
                            summary = stringResource(R.string.security_block_screenshots_summary),
                            checked = uiState.blockScreenshots,
                            onCheckedChange = {
                                onAction(SecuritySettingsAction.BlockScreenshotsToggled(it))
                            },
                        )
                        SwitchRow(
                            title = stringResource(R.string.security_biometric_unlock),
                            summary = stringResource(uiState.biometricCapability.summaryRes()),
                            checked = uiState.biometricUnlockEnabled,
                            enabled = uiState.biometricCapability == BiometricCapability.AVAILABLE,
                            onCheckedChange = {
                                onAction(SecuritySettingsAction.BiometricToggled(it))
                            },
                        )
                    }
                }
            }

            TvSecondaryButton(
                text = stringResource(R.string.security_lock_now),
                onClick = { onAction(SecuritySettingsAction.LockNow) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AutoLockRow(
    duration: AutoLockDuration,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TvSpacing.minTouchTarget)
            .selectable(selected = selected, onClick = onSelected, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(duration.labelRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.standard),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private fun AutoLockDuration.labelRes(): Int = when (this) {
    AutoLockDuration.IMMEDIATE -> R.string.auto_lock_immediate
    AutoLockDuration.THIRTY_SECONDS -> R.string.auto_lock_30s
    AutoLockDuration.ONE_MINUTE -> R.string.auto_lock_1m
    AutoLockDuration.FIVE_MINUTES -> R.string.auto_lock_5m
    AutoLockDuration.FIFTEEN_MINUTES -> R.string.auto_lock_15m
}

private fun BiometricCapability.summaryRes(): Int = when (this) {
    BiometricCapability.AVAILABLE -> R.string.security_biometric_available
    BiometricCapability.NOT_ENROLLED -> R.string.biometric_not_enrolled
    BiometricCapability.TEMPORARILY_UNAVAILABLE -> R.string.biometric_temporarily_unavailable
    BiometricCapability.UNSUPPORTED -> R.string.biometric_unsupported
}

@Preview(name = "Security settings", showBackground = true, heightDp = 950)
@Composable
private fun SecuritySettingsPreview() {
    TvPreviewSurface {
        SecuritySettingsContent(
            uiState = SecuritySettingsUiState(
                isLoading = false,
                biometricCapability = BiometricCapability.AVAILABLE,
                hardwareBackedKeystore = true,
            ),
            onAction = {},
            onNavigateBack = {},
        )
    }
}
