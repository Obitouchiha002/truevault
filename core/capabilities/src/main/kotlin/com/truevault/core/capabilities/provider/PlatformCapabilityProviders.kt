package com.truevault.core.capabilities.provider

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import com.truevault.core.capabilities.model.BiometricCapability
import com.truevault.core.capabilities.model.DocumentDeleteCapability
import com.truevault.core.capabilities.model.MediaPickerCapability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Biometric hardware and enrolment.
 *
 * Only [BIOMETRIC_STRONG] is ever requested. Weak biometrics cannot be bound to a Keystore key, so
 * accepting them would unlock the vault without any cryptographic proof — a login screen, not a
 * lock.
 */
@Singleton
class BiometricCapabilityProvider @Inject constructor(
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
