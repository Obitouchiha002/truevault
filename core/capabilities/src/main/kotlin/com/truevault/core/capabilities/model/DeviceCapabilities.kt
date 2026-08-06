package com.truevault.core.capabilities.model

/**
 * Which product experience this device gets.
 *
 * One codebase, one application id, one encryption format, one database. The mode only decides which
 * *capabilities* are offered — never which vault engine runs.
 */
enum class TrueVaultProductMode {
    /** Android 15+ (API 35+): everything in Core, plus Private Space support. */
    MODERN,

    /** Android 8–14 (API 26–34): the complete file-security product. */
    CORE,
}

/**
 * What this device can actually do for private apps.
 *
 * Deliberately far more granular than a boolean. "Android 15" is not the same as "Private Space is
 * usable here": a managed device can forbid it, the user may not have set it up, the profile may be
 * locked, and some OEM builds ship neither the profile nor the settings screen. Each of those needs
 * different words on screen, so each gets its own value.
 */
enum class PrivateAppsSupport {
    /** TrueVault holds the Home role and can list and launch private-profile apps. */
    FULL_LAUNCHER_INTEGRATION,

    /** Private Space exists on this platform but has not been set up yet. */
    GUIDED_PRIVATE_SPACE_SETUP,

    /** A private profile exists and is currently unlocked. */
    PRIVATE_SPACE_ALREADY_CONFIGURED,

    /** A private profile exists but is locked; its apps are not running. */
    PRIVATE_SPACE_LOCKED,

    /** A device policy forbids additional profiles. */
    DEVICE_POLICY_BLOCKED,

    /** Listing or launching needs the Home role, which TrueVault does not hold. */
    HOME_ROLE_REQUIRED,

    /** A runtime permission is missing. */
    PERMISSION_REQUIRED,

    /** No platform Private Space, but the manufacturer offers something of its own. */
    OEM_PRIVATE_SPACE_ONLY,

    /** Nothing available on this device. */
    NOT_SUPPORTED,

    /** Not probed yet, or probing failed. Treated exactly like NOT_SUPPORTED until proven. */
    UNKNOWN,
}

/**
 * Biometric hardware and enrolment state.
 *
 * Only *strong* (Class 3) biometrics can be bound to a Keystore key, so only those can protect the
 * vault. Plenty of phones ship a Class 2 sensor that unlocks the device perfectly well and cannot
 * hold a key — and telling those users "no biometric hardware" is simply false, which is why
 * [ONLY_WEAK_AVAILABLE] exists as its own value instead of collapsing into [UNSUPPORTED].
 */
enum class BiometricCapability {
    /** Strong biometric, enrolled, ready. */
    AVAILABLE,

    /** Strong hardware is present but nothing is enrolled yet. */
    NOT_ENROLLED,

    /** Hardware exists and is busy or temporarily locked out. */
    TEMPORARILY_UNAVAILABLE,

    /**
     * The device has a working fingerprint or face sensor, but only a weak one.
     *
     * It unlocks the phone; it cannot protect the vault key. The user is told exactly that, rather
     * than being shown a message implying their sensor does not exist.
     */
    ONLY_WEAK_AVAILABLE,

    /** Strong biometrics need a pending security update before they can be used. */
    SECURITY_UPDATE_REQUIRED,

    /** No biometric hardware at all. */
    UNSUPPORTED,
}

/** Whether Keystore keys sit in secure hardware. Reported, never assumed. */
enum class SecureHardwareCapability {
    STRONGBOX,
    TRUSTED_ENVIRONMENT,
    SOFTWARE_ONLY,
    UNKNOWN,
}

/** Which photo picker this device offers. */
enum class MediaPickerCapability {
    /** Platform photo picker, API 33+. */
    PLATFORM_PHOTO_PICKER,

    /** The backported picker provided through Google Play services. */
    BACKPORTED_PHOTO_PICKER,

    /** Neither: fall back to the Storage Access Framework. */
    DOCUMENT_PICKER_ONLY,
}

/** How far the platform will let TrueVault go when removing an original. */
enum class DocumentDeleteCapability {
    /** `MediaStore.createDeleteRequest`, one system dialog for a whole batch. API 30+. */
    SYSTEM_DELETE_REQUEST,

    /** API 29's recoverable-security-exception path, one file at a time. */
    RECOVERABLE_SECURITY_EXCEPTION,

    /** Only documents whose provider advertises delete support. */
    PROVIDER_DELETE_ONLY,
}

/**
 * The complete, observed capability snapshot.
 *
 * Everything here is detected at runtime. Nothing is inferred from [sdkInt] alone, and nothing is
 * inferred from the manufacturer name.
 */
data class DeviceCapabilities(
    val sdkInt: Int,
    val productMode: TrueVaultProductMode,
    val privateAppsSupport: PrivateAppsSupport,
    val privateSpaceAvailable: Boolean,
    /** Null when it cannot be determined without the Home role. */
    val privateSpaceConfigured: Boolean?,
    /** Null when it cannot be determined without the Home role. */
    val privateSpaceUnlocked: Boolean?,
    val isDefaultLauncher: Boolean,
    val isManagedDevice: Boolean,
    val hasWorkProfile: Boolean,
    val biometricCapability: BiometricCapability,
    val secureHardwareCapability: SecureHardwareCapability,
    val mediaPickerCapability: MediaPickerCapability,
    val documentDeleteCapability: DocumentDeleteCapability,
    /** True when a resolvable system settings screen exists for OEM privacy features. */
    val oemPrivacySettingsAvailable: Boolean,
) {
    val isModern: Boolean get() = productMode == TrueVaultProductMode.MODERN

    /**
     * Whether the Private Apps destination appears in navigation.
     *
     * **Always.** This used to be Modern-mode only, and the result was that a user on Android 14
     * went looking for the app-hiding feature and found nothing at all: no entry, no explanation,
     * no way to tell whether the feature did not exist on their device or they had simply failed to
     * find it. Of the available answers, silence is the worst one.
     *
     * The screen behind it reports what *this* device can actually do — full launcher integration,
     * guided Private Space setup, a policy block, a manufacturer alternative, or nothing at all.
     * Each of those is a sentence the user can act on. [privateAppsSupport] decides which one shows.
     */
    val showsPrivateAppsDestination: Boolean
        get() = true

    companion object {
        /**
         * The safe default before detection completes.
         *
         * Everything unknown is reported as unavailable. A capability that briefly claims to exist
         * and then disappears is worse than one that appears a moment late.
         */
        val Unknown = DeviceCapabilities(
            sdkInt = 0,
            productMode = TrueVaultProductMode.CORE,
            privateAppsSupport = PrivateAppsSupport.UNKNOWN,
            privateSpaceAvailable = false,
            privateSpaceConfigured = null,
            privateSpaceUnlocked = null,
            isDefaultLauncher = false,
            isManagedDevice = false,
            hasWorkProfile = false,
            biometricCapability = BiometricCapability.UNSUPPORTED,
            secureHardwareCapability = SecureHardwareCapability.UNKNOWN,
            mediaPickerCapability = MediaPickerCapability.DOCUMENT_PICKER_ONLY,
            documentDeleteCapability = DocumentDeleteCapability.PROVIDER_DELETE_ONLY,
            oemPrivacySettingsAvailable = false,
        )
    }
}
