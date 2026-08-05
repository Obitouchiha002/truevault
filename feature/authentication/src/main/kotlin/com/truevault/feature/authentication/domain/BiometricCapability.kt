package com.truevault.feature.authentication.domain

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What this device can actually do for biometric unlock.
 *
 * Reported honestly rather than reduced to a boolean, because the reasons lead to different advice:
 * "no hardware" is permanent, "nothing enrolled" is one settings visit away, and "hardware
 * unavailable" is usually temporary.
 */
enum class BiometricCapability {
    /** Strong biometrics are present and enrolled. */
    AVAILABLE,

    /** Hardware exists, but the user has not enrolled a fingerprint or face. */
    NOT_ENROLLED,

    /** Hardware exists but is currently unusable, e.g. locked out after failed attempts. */
    TEMPORARILY_UNAVAILABLE,

    /** No strong biometric hardware, or a security update is required to trust it. */
    UNSUPPORTED,
}

/**
 * Only [BIOMETRIC_STRONG] is ever requested.
 *
 * Weak biometrics cannot be bound to a Keystore key, so accepting them would mean unlocking the
 * vault without any cryptographic proof — a login screen rather than a lock.
 */
@Singleton
class BiometricCapabilityChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun capability(): BiometricCapability =
        when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapability.TEMPORARILY_UNAVAILABLE
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> BiometricCapability.TEMPORARILY_UNAVAILABLE
            else -> BiometricCapability.UNSUPPORTED
        }
}
