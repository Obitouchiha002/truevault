package com.truevault.feature.vault.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.truevault.core.common.format.formatBytes
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvEmptyState
import com.truevault.core.designsystem.component.TvLoadingState
import com.truevault.core.designsystem.component.TvStatusPill
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.VaultError
import com.truevault.feature.vault.R

/**
 * Views one secured file.
 *
 * The plaintext is written to the app's internal cache, never to shared storage, and it is deleted
 * the moment this screen leaves the composition — including when the vault locks underneath it. A
 * process killed with the viewer open leaves a cached file behind, which is why startup recovery
 * clears that directory as well.
 *
 * Formats this build cannot render say so plainly instead of showing an empty frame.
 */
@Composable
fun VaultItemViewerScreen(
    vaultItemId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaultItemViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(vaultItemId) {
        viewModel.open(vaultItemId)
        onDispose { viewModel.close() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TvTopAppBar(
            title = uiState.item?.displayName ?: stringResource(R.string.viewer_title),
            onNavigateBack = onNavigateBack,
        )

        when {
            uiState.isLoading -> TvLoadingState(label = stringResource(R.string.viewer_loading))

            uiState.error != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = TvSpacing.screenHorizontal),
                verticalArrangement = Arrangement.Center,
            ) {
                TvBanner(
                    text = stringResource(uiState.error!!.viewerMessageRes()),
                    tone = TvBannerTone.Error,
                )
            }

            else -> ViewerBody(uiState = uiState)
        }
    }
}

@Composable
private fun ViewerBody(uiState: VaultItemViewerUiState) {
    val item = uiState.item ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TvSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.standard),
    ) {
        when (val content = uiState.content) {
            is ViewerContent.Image -> AsyncImage(
                model = content.file,
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )

            is ViewerContent.Text -> TvCard {
                Text(
                    text = content.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (content.truncated) {
                    Text(
                        text = stringResource(R.string.viewer_text_truncated),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = TvSpacing.small),
                    )
                }
            }

            ViewerContent.Unsupported, null -> Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TvEmptyState(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = stringResource(R.string.viewer_unsupported),
                    description = stringResource(R.string.viewer_unsupported_body),
                )
            }
        }

        TvCard {
            DetailRow(stringResource(R.string.viewer_size), formatBytes(item.originalSizeBytes))
            DetailRow(
                stringResource(R.string.viewer_type),
                item.mimeType ?: stringResource(R.string.viewer_unknown_type),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TvSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.viewer_status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TvStatusPill(status = item.privacyStatus)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
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
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * A locked vault and a failed integrity check are different situations for the user: one is fixed by
 * unlocking, the other means the stored bytes did not verify and nothing was shown.
 */
private fun VaultError.viewerMessageRes(): Int = when (this) {
    VaultError.AuthenticationRequired -> R.string.viewer_locked
    else -> R.string.viewer_corrupted
}
