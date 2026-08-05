package com.truevault.feature.authentication.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvCard
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvPrimaryButton
import com.truevault.core.designsystem.theme.TrueVaultTheme
import com.truevault.core.designsystem.theme.TvMotion
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.PasswordStrength
import com.truevault.core.model.PasswordSuggestion
import com.truevault.feature.authentication.R
import com.truevault.feature.authentication.domain.BiometricCapability
import kotlinx.coroutines.flow.combine

@Composable
fun CreateVaultLockScreen(
    onVaultCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateVaultLockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val passwordState = rememberTextFieldState()
    val confirmState = rememberTextFieldState()
    val runBiometricPrompt = rememberBiometricPromptRunner()

    val promptTitle = stringResource(R.string.create_lock_biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.create_lock_biometric_prompt_subtitle)
    val promptNegative = stringResource(R.string.create_lock_biometric_prompt_negative)
    val promptUnavailable = stringResource(R.string.biometric_unavailable)

    // Assessment is recomputed from the field contents, never from a password kept in UI state.
    LaunchedEffect(passwordState, confirmState) {
        combine(
            snapshotFlow { passwordState.text.toString() },
            snapshotFlow { confirmState.text.toString() },
        ) { password, confirmation -> password to confirmation }
            .collect { (password, confirmation) ->
                viewModel.onAction(
                    CreateVaultLockAction.PasswordChanged(
                        password = password.toCharArray(),
                        confirmation = confirmation.toCharArray(),
                    ),
                )
            }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CreateVaultLockEffect.RequestBiometricEnrolment -> runBiometricPrompt(
                    BiometricPromptRequest(
                        cipher = effect.cipher,
                        title = promptTitle,
                        subtitle = promptSubtitle,
                        negativeButton = promptNegative,
                        unavailableMessage = promptUnavailable,
                        onResult = { result ->
                            when (result) {
                                is BiometricPromptResult.Succeeded -> viewModel.onAction(
                                    CreateVaultLockAction.BiometricEnrolled(result.cipher),
                                )
                                // Declining biometrics is not a failure. The vault exists and the
                                // password opens it; the user simply skipped the shortcut.
                                is BiometricPromptResult.Cancelled,
                                is BiometricPromptResult.Error,
                                -> viewModel.onAction(CreateVaultLockAction.BiometricEnrolmentDeclined)

                                BiometricPromptResult.Failed -> Unit
                            }
                        },
                    ),
                )

                CreateVaultLockEffect.VaultCreated -> {
                    passwordState.clearText()
                    confirmState.clearText()
                    onVaultCreated()
                }
            }
        }
    }

    CreateVaultLockContent(
        uiState = uiState,
        passwordState = passwordState,
        confirmState = confirmState,
        onSubmit = {
            viewModel.onAction(
                CreateVaultLockAction.Submit(passwordState.text.toString().toCharArray()),
            )
        },
        onBiometricToggled = { viewModel.onAction(CreateVaultLockAction.BiometricToggled(it)) },
        modifier = modifier,
    )
}

@Composable
internal fun CreateVaultLockContent(
    uiState: CreateVaultLockUiState,
    passwordState: TextFieldState,
    confirmState: TextFieldState,
    onSubmit: () -> Unit,
    onBiometricToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = TvSpacing.screenHorizontal,
                end = TvSpacing.screenHorizontal,
                top = TvSpacing.large,
                bottom = TvSpacing.large,
            ),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.standard),
    ) {
        Text(
            text = stringResource(R.string.create_lock_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.create_lock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedSecureTextField(
            state = passwordState,
            label = { Text(stringResource(R.string.create_lock_password_label)) },
            supportingText = { Text(stringResource(R.string.create_lock_password_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TvSpacing.small),
        )

        StrengthMeter(uiState = uiState)

        OutlinedSecureTextField(
            state = confirmState,
            label = { Text(stringResource(R.string.create_lock_confirm_label)) },
            isError = uiState.confirmTouched && !uiState.passwordsMatch,
            supportingText = {
                if (uiState.confirmTouched && !uiState.passwordsMatch) {
                    Text(stringResource(R.string.create_lock_mismatch))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        BiometricOption(
            capability = uiState.biometricCapability,
            enabled = uiState.enableBiometrics,
            onToggled = onBiometricToggled,
        )

        TvBanner(
            title = stringResource(R.string.create_lock_warning_title),
            text = stringResource(R.string.create_lock_warning_body),
            tone = TvBannerTone.Warning,
        )

        if (uiState.error != null) {
            TvBanner(
                text = stringResource(R.string.create_lock_failed),
                tone = TvBannerTone.Error,
            )
        }

        TvPrimaryButton(
            text = stringResource(R.string.create_lock_action),
            onClick = onSubmit,
            enabled = uiState.canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TvSpacing.small),
        )
    }
}

@Composable
private fun StrengthMeter(uiState: CreateVaultLockUiState) {
    val status = TrueVaultTheme.statusColors
    val assessment = uiState.assessment

    val (fraction, color, labelRes) = when (uiState.strength) {
        PasswordStrength.TOO_SHORT -> Triple(0.08f, MaterialTheme.colorScheme.error, R.string.strength_too_short)
        PasswordStrength.WEAK -> Triple(0.25f, MaterialTheme.colorScheme.error, R.string.strength_weak)
        PasswordStrength.FAIR -> Triple(0.5f, status.warning, R.string.strength_fair)
        PasswordStrength.GOOD -> Triple(0.75f, MaterialTheme.colorScheme.primary, R.string.strength_good)
        PasswordStrength.STRONG -> Triple(1f, status.success, R.string.strength_strong)
    }

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = TvMotion.standardSpec(),
        label = "strengthFraction",
    )
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = TvMotion.standardSpec(),
        label = "strengthColor",
    )

    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.small)) {
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = animatedColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = animatedColor,
            )
            if (assessment != null && assessment.estimatedBits > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.strength_bits,
                        assessment.estimatedBits,
                        assessment.estimatedBits,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        assessment?.suggestions?.forEach { suggestion ->
            Text(
                text = stringResource(suggestion.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BiometricOption(
    capability: BiometricCapability,
    enabled: Boolean,
    onToggled: (Boolean) -> Unit,
) {
    TvCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.standard),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.create_lock_biometric_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(capability.explanationRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(
                checked = enabled && capability == BiometricCapability.AVAILABLE,
                onCheckedChange = onToggled,
                enabled = capability == BiometricCapability.AVAILABLE,
            )
        }
    }
}

private fun PasswordSuggestion.labelRes(): Int = when (this) {
    PasswordSuggestion.MAKE_IT_LONGER -> R.string.suggestion_longer
    PasswordSuggestion.USE_A_PASSPHRASE -> R.string.suggestion_passphrase
    PasswordSuggestion.AVOID_REPETITION -> R.string.suggestion_repetition
    PasswordSuggestion.AVOID_COMMON_PASSWORD -> R.string.suggestion_common
    PasswordSuggestion.MIX_CHARACTER_TYPES -> R.string.suggestion_mix
}

private fun BiometricCapability.explanationRes(): Int = when (this) {
    BiometricCapability.AVAILABLE -> R.string.biometric_available
    BiometricCapability.NOT_ENROLLED -> R.string.biometric_not_enrolled
    BiometricCapability.TEMPORARILY_UNAVAILABLE -> R.string.biometric_temporarily_unavailable
    BiometricCapability.UNSUPPORTED -> R.string.biometric_unsupported
}

@Preview(name = "Create vault lock", showBackground = true, heightDp = 900)
@Composable
private fun CreateVaultLockPreview() {
    TvPreviewSurface {
        CreateVaultLockContent(
            uiState = CreateVaultLockUiState(
                assessment = com.truevault.core.model.assessPassword(
                    "river stone lantern".toCharArray(),
                ),
                passwordsMatch = true,
                biometricCapability = BiometricCapability.AVAILABLE,
            ),
            passwordState = TextFieldState("river stone lantern"),
            confirmState = TextFieldState("river stone lantern"),
            onSubmit = {},
            onBiometricToggled = {},
        )
    }
}
