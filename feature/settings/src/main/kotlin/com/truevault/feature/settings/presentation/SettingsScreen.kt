package com.truevault.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.common.format.formatBytes
import com.truevault.core.model.StorageBudget
import com.truevault.core.model.ThemePreference
import com.truevault.feature.settings.R

@Composable
fun SettingsScreen(
    onOpenSecuritySettings: () -> Unit,
    onOpenDeviceCapabilities: () -> Unit,
    onOpenAdvancedPrivacy: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenPrivateApps: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.NavigateToSecuritySettings -> onOpenSecuritySettings()
                SettingsEffect.NavigateToDeviceCapabilities -> onOpenDeviceCapabilities()
                SettingsEffect.NavigateToAdvancedPrivacy -> onOpenAdvancedPrivacy()
                SettingsEffect.NavigateToAppearance -> onOpenAppearance()
                SettingsEffect.NavigateToUnlock -> onOpenVault()
                SettingsEffect.NavigateToPrivateApps -> onOpenPrivateApps()
            }
        }
    }

    SettingsContent(uiState = uiState, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
    TvTopAppBar(title = stringResource(R.string.settings_title))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = TvSpacing.screenHorizontal,
                end = TvSpacing.screenHorizontal,
                top = TvSpacing.standard,
                bottom = TvSpacing.contentBottom,
            ),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.section),
    ) {
        Column {
            TvSectionHeader(title = stringResource(R.string.settings_appearance))
            TvCard {
                Column(modifier = Modifier.selectableGroup()) {
                    ThemePreference.entries.forEach { theme ->
                        ThemeOptionRow(
                            theme = theme,
                            selected = uiState.theme == theme,
                            onSelected = { onAction(SettingsAction.ThemeSelected(theme)) },
                        )
                    }
                }
            }
        }

        Column {
            TvSectionHeader(
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_summary),
            )
            TvCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_dynamic_color_toggle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = uiState.useDynamicColor,
                        onCheckedChange = { onAction(SettingsAction.DynamicColorToggled(it)) },
                    )
                }
            }
        }

        Column {
            TvSectionHeader(title = stringResource(R.string.settings_security))
            TvCard(onClick = { onAction(SettingsAction.SecuritySettingsClicked) }) {
                NavigationRow(title = stringResource(R.string.settings_security_options))
            }
        }

        Column {
            TvSectionHeader(title = stringResource(R.string.settings_device))
            TvCard(onClick = { onAction(SettingsAction.DeviceCapabilitiesClicked) }) {
                NavigationRow(title = stringResource(R.string.settings_device_capabilities))
            }
        }

        // Private Apps appears in Settings on every version, but only as a destination that tells
        // the truth about this device. It is in the main navigation bar on neither.
        Column {
            TvSectionHeader(
                title = stringResource(R.string.settings_private_apps),
                subtitle = stringResource(
                    if (uiState.capabilities.showsPrivateAppsDestination) {
                        R.string.settings_private_apps_modern
                    } else {
                        R.string.settings_private_apps_core
                    },
                ),
            )
            TvCard(onClick = { onAction(SettingsAction.PrivateAppsClicked) }) {
                NavigationRow(title = stringResource(R.string.settings_private_apps_open))
            }
        }

        Column {
            TvSectionHeader(
                title = stringResource(R.string.settings_open_vault),
                subtitle = stringResource(R.string.settings_open_vault_summary),
            )
            TvCard(onClick = { onAction(SettingsAction.OpenVaultClicked) }) {
                NavigationRow(title = stringResource(R.string.settings_open_vault))
            }
        }

        Column {
            TvSectionHeader(
                title = stringResource(R.string.settings_storage),
                subtitle = stringResource(R.string.settings_storage_summary),
            )
            TvCard {
                Text(
                    text = stringResource(
                        R.string.settings_storage_used,
                        formatBytes(uiState.vaultUsedBytes),
                        if (uiState.storageBudget.isUnlimited) {
                            stringResource(R.string.settings_storage_unlimited)
                        } else {
                            formatBytes(uiState.storageBudget.limitBytes ?: 0L)
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                uiState.budgetFraction?.let { fraction ->
                    Spacer(Modifier.height(TvSpacing.small))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(TvSpacing.xs))
                Text(
                    text = stringResource(
                        R.string.settings_storage_free,
                        formatBytes(uiState.deviceFreeBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(TvSpacing.standard))

                // Only ceilings at or above what is already stored. Offering a smaller one would
                // strand the vault over budget with no way back except deleting files, and the
                // budget deliberately cannot delete anything.
                Column(modifier = Modifier.selectableGroup()) {
                    uiState.selectableBudgets.forEach { budget ->
                        StorageBudgetRow(
                            budget = budget,
                            selected = budget == uiState.storageBudget,
                            onSelected = {
                                onAction(SettingsAction.StorageBudgetSelected(budget))
                            },
                        )
                    }
                }

                if (uiState.isOverBudget) {
                    Spacer(Modifier.height(TvSpacing.small))
                    TvBanner(
                        text = stringResource(R.string.settings_storage_over),
                        tone = TvBannerTone.Warning,
                    )
                }

                Spacer(Modifier.height(TvSpacing.small))
                Text(
                    text = stringResource(R.string.settings_storage_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column {
            TvSectionHeader(
                title = stringResource(R.string.settings_advanced),
                subtitle = stringResource(R.string.settings_advanced_summary),
            )
            TvCard(onClick = { onAction(SettingsAction.AdvancedPrivacyClicked) }) {
                NavigationRow(title = stringResource(R.string.settings_advanced_privacy))
            }
            TvCard(onClick = { onAction(SettingsAction.AppearanceClicked) }) {
                NavigationRow(title = stringResource(R.string.settings_app_icon))
            }
        }
    }
    }
}

@Composable
private fun ThemeOptionRow(
    theme: ThemePreference,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val labelRes = when (theme) {
        ThemePreference.SYSTEM -> R.string.settings_theme_system
        ThemePreference.LIGHT -> R.string.settings_theme_light
        ThemePreference.DARK -> R.string.settings_theme_dark
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TvSpacing.minTouchTarget)
            .selectable(
                selected = selected,
                onClick = onSelected,
                role = Role.RadioButton,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
    ) {
        // Click handling lives on the row so the whole row is one 48dp target; the radio itself
        // must not also be clickable or the row would be announced twice.
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun NavigationRow(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "Settings", showBackground = true, heightDp = 900)
@Composable
private fun SettingsPreview() {
    TvPreviewSurface {
        SettingsContent(uiState = SettingsUiState(isLoading = false), onAction = {})
    }
}

/**
 * One storage ceiling.
 *
 * A radio row rather than a slider: the choices are a short list of round numbers, and a slider
 * would invite someone to set 3.7 GB, which is not a number anyone means.
 */
@Composable
private fun StorageBudgetRow(
    budget: StorageBudget,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val label = if (budget.isUnlimited) {
        stringResource(R.string.settings_storage_unlimited)
    } else {
        formatBytes(budget.limitBytes ?: 0L)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelected, role = Role.RadioButton)
            .padding(vertical = TvSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
