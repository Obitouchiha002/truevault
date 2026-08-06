package com.truevault.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.capabilities.model.BiometricCapability
import com.truevault.core.capabilities.model.DeviceCapabilities
import com.truevault.core.capabilities.model.DocumentDeleteCapability
import com.truevault.core.capabilities.model.MediaPickerCapability
import com.truevault.core.capabilities.model.SecureHardwareCapability
import com.truevault.core.capabilities.model.TrueVaultProductMode
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.settings.R

@Composable
fun DeviceCapabilitiesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceCapabilitiesViewModel = hiltViewModel(),
) {
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    DeviceCapabilitiesContent(
        capabilities = capabilities,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
internal fun DeviceCapabilitiesContent(
    capabilities: DeviceCapabilities,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TvTopAppBar(
            title = stringResource(R.string.capabilities_title),
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
                text = stringResource(R.string.capabilities_explainer),
                tone = TvBannerTone.Info,
            )

            Column {
                TvSectionHeader(title = stringResource(R.string.capabilities_platform))
                TvCard {
                    CapabilityRow(
                        stringResource(R.string.capabilities_android_version),
                        stringResource(R.string.capabilities_api_level, capabilities.sdkInt),
                    )
                    CapabilityRow(
                        stringResource(R.string.capabilities_mode),
                        stringResource(
                            when (capabilities.productMode) {
                                TrueVaultProductMode.MODERN -> R.string.capabilities_mode_modern
                                TrueVaultProductMode.CORE -> R.string.capabilities_mode_core
                            },
                        ),
                    )
                }
            }

            Column {
                TvSectionHeader(title = stringResource(R.string.capabilities_vault))
                TvCard {
                    CapabilityRow(
                        stringResource(R.string.capabilities_file_vault),
                        stringResource(R.string.capabilities_fully_supported),
                    )
                    CapabilityRow(
                        stringResource(R.string.capabilities_backup),
                        stringResource(R.string.capabilities_fully_supported),
                    )
                    CapabilityRow(
                        stringResource(R.string.capabilities_secure_hardware),
                        stringResource(capabilities.secureHardwareCapability.labelRes()),
                    )
                    CapabilityRow(
                        stringResource(R.string.capabilities_biometric),
                        stringResource(capabilities.biometricCapability.labelRes()),
                    )
                    CapabilityRow(
                        stringResource(R.string.capabilities_photo_picker),
                        stringResource(capabilities.mediaPickerCapability.labelRes()),
                    )
                    CapabilityRow(
                        stringResource(R.string.capabilities_deletion),
                        stringResource(capabilities.documentDeleteCapability.labelRes()),
                    )
                }
            }

            Column {
                TvSectionHeader(title = stringResource(R.string.capabilities_private_apps))
                TvCard {
                    CapabilityRow(
                        stringResource(R.string.capabilities_private_space),
                        stringResource(
                            when {
                                capabilities.productMode == TrueVaultProductMode.CORE ->
                                    R.string.capabilities_private_space_needs_15

                                capabilities.isManagedDevice && !capabilities.privateSpaceAvailable ->
                                    R.string.capabilities_private_space_policy

                                capabilities.privateSpaceConfigured == true ->
                                    R.string.capabilities_private_space_configured

                                capabilities.privateSpaceAvailable ->
                                    R.string.capabilities_private_space_supported

                                else -> R.string.capabilities_private_space_unsupported
                            },
                        ),
                    )
                    CapabilityRow(
                        stringResource(R.string.capabilities_secure_launcher),
                        stringResource(
                            if (capabilities.isDefaultLauncher) {
                                R.string.capabilities_enabled
                            } else {
                                R.string.capabilities_not_enabled
                            },
                        ),
                    )
                    CapabilityRow(
                        stringResource(R.string.capabilities_work_profile),
                        stringResource(
                            if (capabilities.hasWorkProfile) {
                                R.string.capabilities_present
                            } else {
                                R.string.capabilities_absent
                            },
                        ),
                    )
                    CapabilityRow(
                        stringResource(R.string.capabilities_oem_tools),
                        stringResource(
                            if (capabilities.oemPrivacySettingsAvailable) {
                                R.string.capabilities_check_device_settings
                            } else {
                                R.string.capabilities_none_detected
                            },
                        ),
                    )
                }
            }

            Text(
                text = stringResource(R.string.capabilities_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun CapabilityRow(label: String, value: String) {
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
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun SecureHardwareCapability.labelRes(): Int = when (this) {
    SecureHardwareCapability.STRONGBOX -> R.string.capabilities_hardware_strongbox
    SecureHardwareCapability.TRUSTED_ENVIRONMENT -> R.string.capabilities_hardware_tee
    SecureHardwareCapability.SOFTWARE_ONLY -> R.string.capabilities_hardware_software
    SecureHardwareCapability.UNKNOWN -> R.string.capabilities_unknown
}

private fun BiometricCapability.labelRes(): Int = when (this) {
    BiometricCapability.AVAILABLE -> R.string.capabilities_biometric_available
    BiometricCapability.NOT_ENROLLED -> R.string.capabilities_biometric_not_enrolled
    BiometricCapability.TEMPORARILY_UNAVAILABLE -> R.string.capabilities_biometric_unavailable
    BiometricCapability.ONLY_WEAK_AVAILABLE -> R.string.capabilities_biometric_weak
    BiometricCapability.SECURITY_UPDATE_REQUIRED -> R.string.capabilities_biometric_update
    BiometricCapability.UNSUPPORTED -> R.string.capabilities_biometric_unsupported
}

private fun MediaPickerCapability.labelRes(): Int = when (this) {
    MediaPickerCapability.PLATFORM_PHOTO_PICKER -> R.string.capabilities_picker_platform
    MediaPickerCapability.BACKPORTED_PHOTO_PICKER -> R.string.capabilities_picker_backport
    MediaPickerCapability.DOCUMENT_PICKER_ONLY -> R.string.capabilities_picker_saf
}

private fun DocumentDeleteCapability.labelRes(): Int = when (this) {
    DocumentDeleteCapability.SYSTEM_DELETE_REQUEST -> R.string.capabilities_delete_system
    DocumentDeleteCapability.RECOVERABLE_SECURITY_EXCEPTION -> R.string.capabilities_delete_per_file
    DocumentDeleteCapability.PROVIDER_DELETE_ONLY -> R.string.capabilities_delete_provider_only
}

@Preview(name = "Device capabilities", showBackground = true, heightDp = 950)
@Composable
private fun DeviceCapabilitiesPreview() {
    TvPreviewSurface {
        DeviceCapabilitiesContent(
            capabilities = DeviceCapabilities.Unknown.copy(sdkInt = 34),
            onNavigateBack = {},
        )
    }
}
