package com.truevault.feature.legal.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvLoadingState
import com.truevault.core.designsystem.component.TvPrimaryButton
import com.truevault.core.designsystem.component.TvTextButton
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.LegalDocumentKind
import com.truevault.feature.legal.R

/**
 * The first screen anyone sees, after the splash and before everything else.
 *
 * Design rules that are not negotiable, and each of which exists because the opposite is a common
 * dark pattern:
 *
 *  - Both checkboxes start **unticked**, and the user ticks them.
 *  - The primary button stays disabled until both are ticked. There is no scroll-to-accept, no
 *    timer, and no single box covering both documents.
 *  - "Decline and Exit" is a real button with the same weight of text as the accept action, not a
 *    grey word hiding in a corner beneath a large coloured button.
 *  - Declining leaves the documents readable. Someone who says no is still entitled to read what
 *    they said no to.
 *  - Back does not dismiss the gate.
 */
@Composable
fun LegalGateScreen(
    onAccepted: () -> Unit,
    onExit: () -> Unit,
    onOpenDocument: (LegalDocumentKind) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LegalGateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LegalGateEffect.Accepted -> onAccepted()
                LegalGateEffect.Exit -> onExit()
            }
        }
    }

    // The gate is not something to back out of. Backing out would land the user in the app without
    // having answered, which is the state this screen exists to prevent.
    BackHandler(enabled = true) { viewModel.onAction(LegalGateAction.DeclineClicked) }

    if (uiState.isLoading) {
        TvLoadingState(modifier = modifier.fillMaxSize())
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = TvSpacing.screenHorizontal,
                end = TvSpacing.screenHorizontal,
                top = TvSpacing.xl,
                bottom = TvSpacing.large,
            ),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.standard),
    ) {
        Text(
            text = stringResource(
                if (uiState.isReacceptance) R.string.legal_update_title else R.string.legal_gate_title,
            ),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        Text(
            text = stringResource(R.string.legal_gate_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (uiState.isReacceptance) {
            VersionChangeSummary(uiState = uiState)
        }

        if (uiState.failed) {
            TvBanner(
                text = stringResource(R.string.legal_document_unavailable),
                tone = TvBannerTone.Error,
            )
        }

        // Debug-time safety net. A build whose policy still says "[PRIVACY EMAIL REQUIRED]" must be
        // impossible to miss long before anyone tries to release it.
        if (uiState.unresolvedPlaceholders.isNotEmpty()) {
            TvBanner(
                title = stringResource(R.string.legal_placeholders_title),
                text = stringResource(
                    R.string.legal_placeholders_body,
                    uiState.unresolvedPlaceholders.size,
                ),
                tone = TvBannerTone.Warning,
            )
        }

        Spacer(Modifier.height(TvSpacing.small))

        DocumentRow(
            title = stringResource(R.string.legal_terms_row_title),
            body = stringResource(R.string.legal_terms_row_body),
            onClick = { onOpenDocument(LegalDocumentKind.TERMS_OF_SERVICE) },
        )

        DocumentRow(
            title = stringResource(R.string.legal_privacy_row_title),
            body = stringResource(R.string.legal_privacy_row_body),
            onClick = { onOpenDocument(LegalDocumentKind.PRIVACY_POLICY) },
        )

        Text(
            text = stringResource(R.string.legal_offline_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(TvSpacing.small))

        ConsentCheckbox(
            checked = uiState.termsAgreed,
            label = stringResource(R.string.legal_agree_terms),
            onCheckedChange = { viewModel.onAction(LegalGateAction.TermsToggled(it)) },
        )

        ConsentCheckbox(
            checked = uiState.privacyAcknowledged,
            label = stringResource(R.string.legal_acknowledge_privacy),
            onCheckedChange = { viewModel.onAction(LegalGateAction.PrivacyToggled(it)) },
        )

        Spacer(Modifier.height(TvSpacing.small))

        TvPrimaryButton(
            text = stringResource(
                if (uiState.isReacceptance) R.string.legal_update_accept else R.string.legal_accept_continue,
            ),
            onClick = { viewModel.onAction(LegalGateAction.AcceptClicked) },
            enabled = uiState.canContinue,
            modifier = Modifier.fillMaxWidth(),
        )

        TvTextButton(
            text = stringResource(R.string.legal_decline_exit),
            onClick = { viewModel.onAction(LegalGateAction.DeclineClicked) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (uiState.showingDeclineConfirmation) {
        DeclineDialog(
            onReadAgain = { viewModel.onAction(LegalGateAction.DeclineDismissed) },
            onConfirm = { viewModel.onAction(LegalGateAction.DeclineConfirmed) },
            onDismiss = { viewModel.onAction(LegalGateAction.DeclineDismissed) },
        )
    }
}

@Composable
private fun VersionChangeSummary(uiState: LegalGateUiState) {
    val versions = uiState.versions ?: return

    TvCard {
        Text(
            text = stringResource(R.string.legal_update_summary_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(TvSpacing.xs))

        uiState.previousTermsVersion?.let { previous ->
            Text(
                text = stringResource(R.string.legal_update_previous, previous),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(
                R.string.legal_update_new,
                versions.termsVersion,
                versions.termsEffectiveDate,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DocumentRow(title: String, body: String, onClick: () -> Unit) {
    TvCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One checkbox and its label as a single control.
 *
 * Merged into one node so that a screen-reader user hears the label with the state, and so that the
 * whole row is a target — a 20 dp checkbox next to unclickable text is a control that only works for
 * people with steady hands.
 */
@Composable
private fun ConsentCheckbox(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox,
            )
            .padding(vertical = TvSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

@Composable
private fun DeclineDialog(
    onReadAgain: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.legal_decline_title)) },
        text = { Text(stringResource(R.string.legal_decline_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.legal_decline_exit_app))
            }
        },
        dismissButton = {
            TextButton(onClick = onReadAgain) {
                Text(stringResource(R.string.legal_decline_reconsider))
            }
        },
    )
}
