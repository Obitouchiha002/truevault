package com.truevault.core.capabilities.model

/**
 * The private profile's state, as the UI must react to it.
 *
 * Separate from [PrivateAppsSupport] on purpose: support answers "can this device do it at all",
 * state answers "what is true right now". The second changes while the app is open — a user can
 * lock Private Space from the notification shade — and every change has to reach the UI.
 */
sealed interface PrivateSpaceState {

    /** This Android version or device has no private profile. */
    data object Unsupported : PrivateSpaceState

    /** The platform supports it; the user has not set it up. */
    data object NotConfigured : PrivateSpaceState

    /** Set up, currently locked. Its apps are not running and must not be listed. */
    data object ConfiguredLocked : PrivateSpaceState

    /** Set up and unlocked. */
    data object ConfiguredUnlocked : PrivateSpaceState

    /** A device policy forbids it. */
    data object RestrictedByPolicy : PrivateSpaceState

    /** Listing and launching need the Home role, which TrueVault does not hold. */
    data object HomeRoleRequired : PrivateSpaceState

    data object PermissionRequired : PrivateSpaceState

    /** [safeReason] is pre-sanitised and safe to display. Never a raw exception message. */
    data class Error(val safeReason: String) : PrivateSpaceState
}

/** One app inside a profile, identified by package and profile. */
data class PrivateAppId(
    val packageName: String,
    val componentName: String,
    val userSerialNumber: Int,
)

/** An app entry for the launcher grid. */
data class LauncherAppEntry(
    val id: PrivateAppId,
    val label: String,
    val isPrivateProfile: Boolean,
    val isWorkProfile: Boolean,
)

/**
 * Every outcome a capability action can have.
 *
 * Android exceptions never reach the UI. Each of these maps to a different sentence a user can act
 * on, which is the entire reason for having nine of them instead of a boolean.
 */
sealed interface CapabilityActionResult {
    data object Success : CapabilityActionResult
    data object UserCancelled : CapabilityActionResult
    data object Unsupported : CapabilityActionResult
    data object RoleRequired : CapabilityActionResult
    data object PermissionRequired : CapabilityActionResult
    data object RestrictedByPolicy : CapabilityActionResult
    data object SettingsUnavailable : CapabilityActionResult
    data class Failure(val safeMessage: String) : CapabilityActionResult
}
