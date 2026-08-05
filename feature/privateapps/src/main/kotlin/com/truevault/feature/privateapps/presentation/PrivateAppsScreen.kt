package com.truevault.feature.privateapps.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvPrimaryButton
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.PrivateAppsCapability
import com.truevault.feature.privateapps.R

/**
 * Private Apps.
 *
 * This screen exists to be honest about a capability the platform owns. On Android 15 and later it
 * explains Private Space and opens the system's own setup. Everywhere else it says the device does
 * not support it — it never shows a fake "setup complete", and the file vault works fully either
 * way.
 */
@Composable
fun PrivateAppsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivateAppsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
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

            when (uiState.capability) {
                PrivateAppsCapability.SUPPORTED_GUIDED_SETUP,
                PrivateAppsCapability.SUPPORTED_LAUNCHER_INTEGRATION,
                -> Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
                    TvSectionHeader(title = stringResource(R.string.private_apps_supported_title))

                    TvCard {
                        StepText(1, stringResource(R.string.private_apps_step_1))
                        StepText(2, stringResource(R.string.private_apps_step_2))
                        StepText(3, stringResource(R.string.private_apps_step_3))
                    }

                    TvBanner(
                        text = stringResource(R.string.private_apps_separate_data),
                        tone = TvBannerTone.Warning,
                    )

                    TvPrimaryButton(
                        text = stringResource(R.string.private_apps_open_settings),
                        onClick = {
                            viewModel.systemSettingsIntent()?.let(context::startActivity)
                        },
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                PrivateAppsCapability.MANAGED_DEVICE_RESTRICTED -> TvBanner(
                    title = stringResource(R.string.private_apps_managed_title),
                    text = stringResource(R.string.private_apps_managed_body),
                    tone = TvBannerTone.Warning,
                )

                PrivateAppsCapability.NOT_SUPPORTED,
                PrivateAppsCapability.UNKNOWN,
                -> TvBanner(
                    title = stringResource(R.string.private_apps_unsupported_title),
                    text = stringResource(R.string.private_apps_unsupported_body),
                    tone = TvBannerTone.Warning,
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
private fun StepText(number: Int, text: String) {
    Text(
        text = "$number.  $text",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = TvSpacing.xs),
    )
}

@Preview(name = "Private apps", showBackground = true, heightDp = 800)
@Composable
private fun PrivateAppsPreview() {
    TvPreviewSurface {
        Column {
            TvSectionHeader(title = "Private apps")
        }
    }
}
