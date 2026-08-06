package com.truevault.core.capabilities.provider

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import com.truevault.core.capabilities.model.BiometricCapability
import com.truevault.core.capabilities.model.DocumentDeleteCapability
import com.truevault.core.capabilities.model.MediaPickerCapability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Biometric hardware and enrolment.
 *
 * Only [BIOMETRIC_STRONG] is ever *used*. Weak biometrics cannot be bound to a Keystore key, so
 * accepting one would unlock the vault with no cryptographic proof behind it — a login screen, not
 * a lock. That requirement does not change.
 *
 * What changed is the diagnosis. Asking about strong biometrics alone cannot tell "this phone has
 * no sensor" apart from "this phone has a Class 2 sensor that cannot hold a key", and both used to
 * arrive at the user as "no biometric hardware detected". On a phone whose fingerprint reader
 * plainly works, that message is not a limitation — it is wrong, and it makes the whole app look
 * broken.
 *
 * So the weak query is asked too, purely to explain the strong answer.
 */
@Singleton
class BiometricCapabilityProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun capability(): BiometricCapability {
        val manager = BiometricManager.from(context)

        return when (val strong = manager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.AVAILABLE

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
            -> BiometricCapability.TEMPORARILY_UNAVAILABLE

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricCapability.SECURITY_UPDATE_REQUIRED

            // NO_HARDWARE and UNSUPPORTED both mean "not strong here". Whether that is because
            // there is no sensor or because the sensor is Class 2 is the difference between an
            // honest message and a wrong one, so ask.
            else -> if (hasAnyWeakBiometric(manager)) {
                BiometricCapability.ONLY_WEAK_AVAILABLE
            } else {
                BiometricCapability.UNSUPPORTED
            }.also { _ -> strong }
        }
    }

    /** True when the device has usable biometric hardware of any class. */
    private fun hasAnyWeakBiometric(manager: BiometricManager): Boolean =
        when (manager.canAuthenticate(BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS,
            // Not enrolled still means the hardware is there, which is the question being asked.
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> true

            else -> false
        }
}

/** Which picker this device offers, and therefore which one the import flow launches. */
@Singleton
class MediaPickerCapabilityProvider @Inject constructor() {
    fun capability(): MediaPickerCapability = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            MediaPickerCapability.PLATFORM_PHOTO_PICKER

        // AndroidX's PickVisualMedia falls back to the Play-services backport where it exists, and
        // to ACTION_OPEN_DOCUMENT where it does not. Either way the user picks; the app never gains
        // blanket storage access.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            MediaPickerCapability.BACKPORTED_PHOTO_PICKER

        else -> MediaPickerCapability.DOCUMENT_PICKER_ONLY
    }
}

/**
 * How far the platform will let TrueVault go when removing an original.
 *
 * This is a platform fact, not a preference: on API 26–28 there is no way to delete MediaStore
 * content without all-files access, which TrueVault will not request. The capability is reported so
 * the UI can say so before the user chooses Secure Move.
 */
@Singleton
class DocumentDeleteCapabilityProvider @Inject constructor() {
    fun capability(): DocumentDeleteCapability = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> DocumentDeleteCapability.SYSTEM_DELETE_REQUEST
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> DocumentDeleteCapability.RECOVERABLE_SECURITY_EXCEPTION
        else -> DocumentDeleteCapability.PROVIDER_DELETE_ONLY
    }
}
