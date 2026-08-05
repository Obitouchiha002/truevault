package com.truevault.feature.privateapps.presentation

import androidx.compose.runtime.Immutable
import com.truevault.core.capabilities.model.CapabilityActionResult
import com.truevault.core.capabilities.model.DeviceCapabilities
import com.truevault.core.capabilities.model.PrivateSpaceState

@Immutable
data class PrivateAppsUiState(
    val capabilities: DeviceCapabilities = DeviceCapabilities.Unknown,
    val privateSpaceState: PrivateSpaceState = PrivateSpaceState.Unsupported,
    /** The user has read and acknowledged the separate-installation warnings. */
    val warningsAcknowledged: Boolean = false,
    val showingWarnings: Boolean = false,
    /** Package the user is considering removing from the main space, if any. */
    val removalCandidate: RemovalCandidate? = null,
    val lastActionResult: CapabilityActionResult? = null,
)

/**
 * A main-space app the user is thinking about removing after installing it privately.
 *
 * [privateCopyVerified] is set **only** when a private copy was observed through supported APIs.
 * [manuallyConfirmed] is the user saying they checked themselves. The removal button needs one of
 * the two, and the screen never describes a manual confirmation as an automatic verification.
 */
@Immutable
data class RemovalCandidate(
    val packageName: String,
    val label: String,
    val privateCopyVerified: Boolean,
    val manuallyConfirmed: Boolean,
) {
    val canRemove: Boolean get() = privateCopyVerified || manuallyConfirmed
}

sealed interface PrivateAppsAction {
    data object Refresh : PrivateAppsAction
    data object SetupRequested : PrivateAppsAction
    data object WarningsRequested : PrivateAppsAction
    data object WarningsDismissed : PrivateAppsAction
    data object WarningsAcknowledged : PrivateAppsAction
    data object OpenOemSettings : PrivateAppsAction
    data class RemovalConsidered(val packageName: String, val label: String) : PrivateAppsAction
    data object ManualVerificationConfirmed : PrivateAppsAction
    data object RemovalDismissed : PrivateAppsAction
    data object RemoveMainCopy : PrivateAppsAction
    data object OpenPrivateCopy : PrivateAppsAction
    data object ResultDismissed : PrivateAppsAction
}

sealed interface PrivateAppsEffect {
    /** The system's own uninstall confirmation. TrueVault never removes a package itself. */
    data class LaunchUninstall(val packageName: String) : PrivateAppsEffect
}
