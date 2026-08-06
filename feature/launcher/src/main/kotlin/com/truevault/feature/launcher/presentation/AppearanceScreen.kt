package com.truevault.feature.launcher.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvPrimaryButton
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.AppearanceProfile
import com.truevault.core.model.AppearanceSwitchResult
import com.truevault.feature.launcher.R

/**
 * Settings → Appearance and Privacy → App Appearance.
 *
 * The most important element on this screen is not a control: it is the sentence explaining what
 * changing the icon does **not** do. Someone choosing this feature may be relying on it in a
 * situation that matters, and letting them believe the app becomes invisible — when Android Settings
 * will list it plainly — would be putting them at risk on a promise the app cannot keep.
 */
@Composable
fun AppearanceScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TvTopAppBar(
                title = stringResource(R.string.appearance_title),
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
            verticalArrangement = Arrangement.spacedBy(TvSpacing.standard),
        ) {
            // Stated before the options, not after them. A limitation printed underneath the button
            // someone already pressed is a disclaimer; printed above it, it is information.
            TvBanner(
                title = stringResource(R.string.appearance_limits_title),
                text = stringResource(R.string.appearance_limits_body),
                tone = TvBannerTone.Warning,
            )

            TvSectionHeader(title = stringResource(R.string.appearance_choose))

            Column(modifier = Modifier.selectableGroup()) {
                AppearanceProfile.entries.forEach { profile ->
                    ProfileRow(
                        profile = profile,
                        selected = profile == uiState.selected,
                        isCurrent = profile == uiState.current,
                        onSelected = {
                            viewModel.onAction(AppearanceAction.ProfileSelected(profile))
                        },
                    )
                }
            }

            TvPrimaryButton(
                text = stringResource(
                    if (uiState.isApplying) {
                        R.string.appearance_applying
                    } else {
                        R.string.appearance_apply
                    },
                ),
                onClick = { viewModel.onAction(AppearanceAction.ApplyRequested) },
                enabled = uiState.hasChange && !uiState.isApplying,
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.lastResult?.let { result -> ResultBanner(result) }

            Text(
                text = stringResource(R.string.appearance_vault_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultBanner(result: AppearanceSwitchResult) {
    when (result) {
        is AppearanceSwitchResult.Applied -> TvBanner(
            title = stringResource(R.string.appearance_updated_title),
            // Launcher icon caches are the launcher's business, and some take a while. Saying so is
            // the difference between "it's slow" and "it's broken".
            text = stringResource(R.string.appearance_updated_body),
            tone = TvBannerTone.Success,
        )

        is AppearanceSwitchResult.PartiallyApplied -> TvBanner(
            title = stringResource(R.string.appearance_partial_title),
            text = stringResource(R.string.appearance_partial_body),
            tone = TvBannerTone.Warning,
        )

        is AppearanceSwitchResult.Failed -> TvBanner(
            text = result.safeReason,
            tone = TvBannerTone.Error,
        )

        AppearanceSwitchResult.NoChange -> Unit
    }
}

@Composable
private fun ProfileRow(
    profile: AppearanceProfile,
    selected: Boolean,
    isCurrent: Boolean,
    onSelected: () -> Unit,
) {
    val (titleRes, bodyRes) = when (profile) {
        AppearanceProfile.TRUE_VAULT ->
            R.string.appearance_truevault to R.string.appearance_truevault_body
        AppearanceProfile.NEXA ->
            R.string.appearance_nexa to R.string.appearance_nexa_body
        AppearanceProfile.NEXA_NOTES ->
            R.string.appearance_nexa_notes to R.string.appearance_nexa_notes_body
        AppearanceProfile.NEXA_FILES ->
            R.string.appearance_nexa_files to R.string.appearance_nexa_files_body
    }

    TvCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onSelected, role = Role.RadioButton),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
        ) {
            RadioButton(selected = selected, onClick = null)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(bodyRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isCurrent) {
                    Text(
                        text = stringResource(R.string.appearance_current),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
