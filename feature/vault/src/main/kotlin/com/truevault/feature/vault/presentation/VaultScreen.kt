package com.truevault.feature.vault.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvEmptyState
import com.truevault.core.designsystem.component.TvLoadingState
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvPrimaryButton
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.vault.R

@Composable
fun VaultScreen(
    onAddFiles: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                VaultEffect.NavigateToImport -> onAddFiles()
            }
        }
    }

    VaultContent(uiState = uiState, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
internal fun VaultContent(
    uiState: VaultUiState,
    onAction: (VaultAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TvTopAppBar(title = stringResource(R.string.vault_title))

        when {
            uiState.isLoading -> TvLoadingState()

            uiState.isEmpty -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = TvSpacing.screenHorizontal),
                verticalArrangement = Arrangement.Center,
            ) {
                TvEmptyState(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.vault_empty_title),
                    description = stringResource(R.string.vault_empty_body),
                    action = {
                        TvPrimaryButton(
                            text = stringResource(R.string.vault_empty_action),
                            onClick = { onAction(VaultAction.AddFilesClicked) },
                        )
                    },
                )
            }

            // The paged, encrypted-thumbnail vault grid arrives with the storage layer in Phase 2.
            else -> TvLoadingState()
        }
    }
}

@Preview(name = "Vault – empty", showBackground = true, heightDp = 700)
@Composable
private fun VaultEmptyPreview() {
    TvPreviewSurface {
        VaultContent(uiState = VaultUiState(isLoading = false, isEmpty = true), onAction = {})
    }
}
