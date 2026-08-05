package com.truevault.feature.launcher.presentation

import androidx.compose.runtime.Immutable
import com.truevault.core.capabilities.model.CapabilityActionResult
import com.truevault.core.capabilities.model.LauncherAppEntry
import com.truevault.core.capabilities.model.PrivateSpaceState

@Immutable
data class SecureLauncherUiState(
    val isLoading: Boolean = true,
    val isDefaultLauncher: Boolean = false,
    val query: String = "",
    val mainApps: List<LauncherAppEntry> = emptyList(),
    val privateApps: List<LauncherAppEntry> = emptyList(),
    val workApps: List<LauncherAppEntry> = emptyList(),
    val privateSpaceState: PrivateSpaceState = PrivateSpaceState.Unsupported,
    val visibilityEnabled: Boolean = false,
    val hiddenPackages: Set<String> = emptySet(),
    val editingVisibility: Boolean = false,
    /** Changing which icons are hidden is a vault action and needs an unlocked session. */
    val vaultUnlocked: Boolean = false,
    val lastResult: CapabilityActionResult? = null,
) {
    /** Main-profile apps after Launcher Visibility filtering and the current search. */
    val visibleMainApps: List<LauncherAppEntry>
        get() = mainApps
            .filterNot { visibilityEnabled && !editingVisibility && it.id.packageName in hiddenPackages }
            .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }

    /**
     * Private apps, and only while the profile is unlocked.
     *
     * The list is empty in every other state. Nothing derived from a locked profile — labels, icons,
     * search results — may appear on screen.
     */
    val visiblePrivateApps: List<LauncherAppEntry>
        get() = if (privateSpaceState == PrivateSpaceState.ConfiguredUnlocked) {
            privateApps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        } else {
            emptyList()
        }

    val visibleWorkApps: List<LauncherAppEntry>
        get() = workApps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }

    val canEditVisibility: Boolean get() = visibilityEnabled && vaultUnlocked

    val showsPrivateSection: Boolean
        get() = privateSpaceState != PrivateSpaceState.Unsupported
}

sealed interface SecureLauncherAction {
    data class QueryChanged(val query: String) : SecureLauncherAction
    data class AppClicked(val entry: LauncherAppEntry) : SecureLauncherAction
    data class VisibilityToggled(val packageName: String) : SecureLauncherAction
    data object EditVisibilityToggled : SecureLauncherAction
    data object RestoreAllIcons : SecureLauncherAction
    data object Refresh : SecureLauncherAction
    data object ResultDismissed : SecureLauncherAction
}
