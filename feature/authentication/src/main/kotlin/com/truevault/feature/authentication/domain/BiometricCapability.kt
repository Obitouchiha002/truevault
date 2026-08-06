package com.truevault.feature.authentication.domain

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
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

    /**
     * The device has a working sensor, but only a weak (Class 2) one.
     *
     * It unlocks the phone and cannot hold a Keystore key. Saying "no biometric hardware" to
     * someone whose fingerprint reader plainly works is not a limitation, it is wrong — and it
     * makes the whole app look broken.
     */
    ONLY_WEAK_AVAILABLE,

    /** Strong biometrics are blocked until a pending security update is installed. */
    SECURITY_UPDATE_REQUIRED,

    /** No biometric hardware at all. */
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
    fun capability(): BiometricCapability {
        val manager = BiometricManager.from(context)

        return when (manager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
            -> BiometricCapability.TEMPORARILY_UNAVAILABLE

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricCapability.SECURITY_UPDATE_REQUIRED

            // "Not strong here" covers both no sensor and a Class 2 sensor. Which one it is
            // decides whether the user reads a fact or a falsehood, so ask the second question.
            else -> if (manager.canAuthenticate(BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
                BiometricCapability.ONLY_WEAK_AVAILABLE
            } else {
                BiometricCapability.UNSUPPORTED
            }
        }
    }
}
