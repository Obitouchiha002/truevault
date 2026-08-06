package com.truevault.feature.authentication.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvPinPad
import com.truevault.core.designsystem.component.TvPrimaryButton
import com.truevault.core.designsystem.component.TvSecondaryButton
import com.truevault.core.designsystem.component.TvTextButton
import com.truevault.core.designsystem.theme.TvRadius
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.VaultError
import com.truevault.feature.authentication.R

@Composable
fun UnlockScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnlockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val passwordState = rememberTextFieldState()
    val recoveryState = rememberTextFieldState()
    val runBiometricPrompt = rememberBiometricPromptRunner()

    val promptTitle = stringResource(R.string.unlock_biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.unlock_biometric_prompt_subtitle)
    val promptNegative = stringResource(R.string.unlock_biometric_prompt_negative)
    val promptUnavailable = stringResource(R.string.biometric_unavailable)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UnlockEffect.LaunchBiometricPrompt -> runBiometricPrompt(
                    BiometricPromptRequest(
                        cipher = effect.cipher,
                        title = promptTitle,
                        subtitle = promptSubtitle,
                        negativeButton = promptNegative,
                        unavailableMessage = promptUnavailable,
                        onResult = { result ->
                            when (result) {
                                is BiometricPromptResult.Succeeded -> viewModel.onAction(
                                    UnlockAction.BiometricAuthenticated(result.cipher),
                                )
                                is BiometricPromptResult.Cancelled,
                                is BiometricPromptResult.Error,
                                -> viewModel.onAction(UnlockAction.BiometricDismissed)

                                BiometricPromptResult.Failed -> Unit
                            }
                        },
                    ),
                )

                UnlockEffect.Unlocked -> {
                    passwordState.clearText()
                    onUnlocked()
                }
            }
        }
    }

    UnlockContent(
        uiState = uiState,
        passwordState = passwordState,
        recoveryState = recoveryState,
        onSubmit = {
            viewModel.onAction(UnlockAction.Submit(passwordState.text.toString().toCharArray()))
        },
        onBiometricRequested = { viewModel.onAction(UnlockAction.BiometricRequested) },
        onRecoveryRequested = { viewModel.onAction(UnlockAction.RecoveryRequested) },
        onPinDigit = { viewModel.onAction(UnlockAction.PinDigitEntered(it)) },
        onPinBackspace = { viewModel.onAction(UnlockAction.PinBackspace) },
        onSubmitRecovery = {
            viewModel.onAction(UnlockAction.SubmitRecoveryKey(recoveryState.text.toString()))
            recoveryState.clearText()
        },
        modifier = modifier,
    )
}

@Composable
internal fun UnlockContent(
    uiState: UnlockUiState,
    passwordState: TextFieldState,
    recoveryState: TextFieldState,
    onSubmit: () -> Unit,
    onBiometricRequested: () -> Unit,
    onRecoveryRequested: () -> Unit,
    onSubmitRecovery: () -> Unit,
    onPinDigit: (Char) -> Unit,
    onPinBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = TvSpacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(TvRadius.card),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }

        Text(
            text = stringResource(R.string.unlock_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = TvSpacing.section),
        )
        Text(
            text = stringResource(R.string.unlock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = TvSpacing.small),
        )

        if (uiState.isThrottled) {
            // Counted down rather than shown once, so the user can watch it clear.
            TvBanner(
                title = stringResource(R.string.unlock_throttled_title),
                text = stringResource(
                    R.string.unlock_throttled_body,
                    formatWait(uiState.throttleRemainingMillis),
                ),
                tone = TvBannerTone.Warning,
                modifier = Modifier.padding(top = TvSpacing.section),
            )
        }

        val lockType = uiState.lockType
        if (lockType != null && lockType.isPin) {
            TvPinPad(
                length = lockType.pinLength ?: 6,
                entered = uiState.pinEnteredCount,
                onDigit = { onPinDigit(it) },
                onBackspace = onPinBackspace,
                enabled = !uiState.isCheckingPassword && !uiState.isThrottled,
                modifier = Modifier.padding(top = TvSpacing.section),
            )
        } else {
            OutlinedSecureTextField(
                state = passwordState,
                label = { Text(stringResource(R.string.unlock_password_label)) },
                isError = uiState.error != null,
                enabled = !uiState.isThrottled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TvSpacing.section),
            )
        }

        if (uiState.error != null) {
            TvBanner(
                text = stringResource(uiState.error.unlockMessageRes()),
                tone = TvBannerTone.Error,
                modifier = Modifier.padding(top = TvSpacing.standard),
            )
        }

        if (uiState.biometricWasReset) {
            TvBanner(
                text = stringResource(R.string.unlock_biometric_reset),
                tone = TvBannerTone.Info,
                modifier = Modifier.padding(top = TvSpacing.standard),
            )
        }

        if (lockType == null || !lockType.isPin) {
            TvPrimaryButton(
                text = stringResource(R.string.unlock_action),
                onClick = onSubmit,
                enabled = !uiState.isCheckingPassword && !uiState.isThrottled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TvSpacing.section),
            )
        }

        if (uiState.recoveryKeyAvailable && !uiState.showingRecoveryEntry) {
            TvTextButton(
                text = stringResource(R.string.unlock_use_recovery_key),
                onClick = { onRecoveryRequested() },
            )
        }

        if (uiState.showingRecoveryEntry) {
            OutlinedTextField(
                state = recoveryState,
                label = { Text(stringResource(R.string.unlock_recovery_label)) },
                supportingText = { Text(stringResource(R.string.unlock_recovery_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TvSpacing.standard),
            )
            TvPrimaryButton(
                text = stringResource(R.string.unlock_recovery_action),
                onClick = onSubmitRecovery,
                enabled = recoveryState.text.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TvSpacing.small),
            )
        }

        if (uiState.biometricAvailable) {
            TvSecondaryButton(
                text = stringResource(R.string.unlock_use_biometrics),
                onClick = onBiometricRequested,
                icon = Icons.Filled.Fingerprint,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TvSpacing.small),
            )
        }
    }
}

/**
 * Wrong password and tampered blob deliberately map to the same message: telling them apart for the
 * user would also tell them apart for anyone testing guesses.
 */
/** Whole minutes while the wait is long, seconds once it is nearly over. */
private fun formatWait(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(1)
    return if (seconds >= 60) "${(seconds + 59) / 60} min" else "$seconds s"
}

private fun VaultError.unlockMessageRes(): Int = when (this) {
    is VaultError.UnsupportedFormatVersion -> R.string.unlock_unsupported_version
    VaultError.IntegrityCheckFailed -> R.string.unlock_corrupted
    else -> R.string.unlock_incorrect
}

@Preview(name = "Unlock", showBackground = true, heightDp = 780)
@Composable
private fun UnlockPreview() {
    TvPreviewSurface {
        UnlockContent(
            uiState = UnlockUiState(biometricAvailable = true, recoveryKeyAvailable = true),
            passwordState = TextFieldState(),
            recoveryState = TextFieldState(),
            onSubmit = {},
            onBiometricRequested = {},
            onRecoveryRequested = {},
            onSubmitRecovery = {},
            onPinDigit = {},
            onPinBackspace = {},
        )
    }
}
