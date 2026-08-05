package com.truevault.feature.home.presentation

import androidx.compose.runtime.Immutable
import com.truevault.core.capabilities.model.DeviceCapabilities
import com.truevault.core.capabilities.model.PrivateSpaceState
import com.truevault.core.model.MimeCategory
import com.truevault.core.model.PrivacyScore

/**
 * Immutable state for the dashboard.
 *
 * State flows down, actions flow up, effects are one-shot. Every screen in TrueVault follows this
 * same triple so that adding a screen never means inventing a new pattern.
 */
@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val greeting: Greeting = Greeting.AFTERNOON,
    val vaultIsEmpty: Boolean = true,
    val totalItems: Int = 0,
    val categoryCounts: Map<MimeCategory, Int> = emptyMap(),
    /** Null until there is enough vault data for a score to mean anything. */
    val privacyScore: PrivacyScore? = null,
    val recentActivity: List<HomeActivityItem> = emptyList(),
    val capabilities: DeviceCapabilities = DeviceCapabilities.Unknown,
    val privateSpaceState: PrivateSpaceState = PrivateSpaceState.Unsupported,
) {
    /**
     * Modern devices get a Private Apps quick action; Core devices get Security Settings in its
     * place. An unavailable button is never shown greyed out in the main flow — the specification
     * calls for it not to be there at all.
     */
    val showsPrivateAppsAction: Boolean get() = capabilities.showsPrivateAppsDestination

    fun countFor(category: MimeCategory): Int = categoryCounts[category] ?: 0
}

enum class Greeting { MORNING, AFTERNOON, EVENING }

/**
 * A privacy-safe activity entry.
 *
 * Deliberately holds no file name and no path — only what happened, to how many items, and when.
 */
@Immutable
data class HomeActivityItem(
    val id: String,
    val kind: Kind,
    val itemCount: Int,
    val timestampMillis: Long,
) {
    enum class Kind {
        FILES_SECURED,
        ORIGINAL_DELETED,
        DUPLICATE_DETECTED,
        BACKUP_COMPLETED,
        IMPORT_FAILED,
    }
}

sealed interface HomeAction {
    data object Refresh : HomeAction
    data object AddFilesClicked : HomeAction
    data object RunScanClicked : HomeAction
    data object PrivateAppsClicked : HomeAction
    data object BackupClicked : HomeAction
    data object SecuritySettingsClicked : HomeAction
    data object OpenVaultClicked : HomeAction
    data class CategoryClicked(val category: MimeCategory) : HomeAction
}

sealed interface HomeEffect {
    data object NavigateToImport : HomeEffect
    data object NavigateToScanner : HomeEffect
    data object NavigateToPrivateApps : HomeEffect
    data object NavigateToBackup : HomeEffect
    data object NavigateToSecuritySettings : HomeEffect
    data object NavigateToVault : HomeEffect
    data class NavigateToCategory(val category: MimeCategory) : HomeEffect
}
