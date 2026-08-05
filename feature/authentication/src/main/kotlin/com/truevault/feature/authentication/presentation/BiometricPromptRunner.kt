package com.truevault.feature.authentication.presentation

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/** Outcome of a biometric prompt, as the screen needs to react to it. */
sealed interface BiometricPromptResult {
    /** The cipher is now authenticated and can decrypt exactly one payload. */
    data class Succeeded(val cipher: Cipher) : BiometricPromptResult

    /** The user dismissed the prompt or pressed the negative button. */
    data object Cancelled : BiometricPromptResult

    /** A recognisable finger or face was not presented. The prompt stays open. */
    data object Failed : BiometricPromptResult

    /** Terminal error; [message] is the system's own wording, already user-facing. */
    data class Error(val code: Int, val message: String) : BiometricPromptResult
}

/**
 * Runs `BiometricPrompt` against the hosting activity.
 *
 * The prompt is always given a `CryptoObject`. A biometric result that does not unlock a Keystore
 * key proves nothing — it is a UI gate that any process with the right permissions could bypass, so
 * TrueVault never treats one as authentication.
 */
@Composable
fun rememberBiometricPromptRunner(): (BiometricPromptRequest) -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    return remember(activity) {
        { request ->
            if (activity == null) {
                request.onResult(
                    BiometricPromptResult.Error(
                        code = BiometricPrompt.ERROR_HW_UNAVAILABLE,
                        message = request.unavailableMessage,
                    ),
                )
            } else {
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult,
                        ) {
                            val cipher = result.cryptoObject?.cipher
                            if (cipher == null) {
                                request.onResult(
                                    BiometricPromptResult.Error(
                                        code = BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
                                        message = request.unavailableMessage,
                                    ),
                                )
                            } else {
                                request.onResult(BiometricPromptResult.Succeeded(cipher))
                            }
                        }

                        override fun onAuthenticationError(code: Int, message: CharSequence) {
                            val cancelled = code == BiometricPrompt.ERROR_USER_CANCELED ||
                                code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                code == BiometricPrompt.ERROR_CANCELED
                            request.onResult(
                                if (cancelled) {
                                    BiometricPromptResult.Cancelled
                                } else {
                                    BiometricPromptResult.Error(code, message.toString())
                                },
                            )
                        }

                        override fun onAuthenticationFailed() {
                            request.onResult(BiometricPromptResult.Failed)
                        }
                    },
                )

                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(request.title)
                        .setSubtitle(request.subtitle)
                        .setNegativeButtonText(request.negativeButton)
                        .setAllowedAuthenticators(BIOMETRIC_STRONG)
                        .setConfirmationRequired(false)
                        .build(),
                    BiometricPrompt.CryptoObject(request.cipher),
                )
            }
        }
    }
}

data class BiometricPromptRequest(
    val cipher: Cipher,
    val title: String,
    val subtitle: String,
    val negativeButton: String,
    val unavailableMessage: String,
    val onResult: (BiometricPromptResult) -> Unit,
)

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
