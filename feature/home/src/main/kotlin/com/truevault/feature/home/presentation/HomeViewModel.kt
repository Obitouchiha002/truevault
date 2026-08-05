package com.truevault.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.capabilities.DeviceCapabilityDetector
import com.truevault.core.capabilities.model.DeviceCapabilities
import com.truevault.core.capabilities.model.PrivateSpaceState
import com.truevault.core.capabilities.privateapps.PrivateAppsController
import com.truevault.core.common.time.TimeProvider
import com.truevault.core.data.ActivityEvent
import com.truevault.core.data.ActivityKind
import com.truevault.core.data.ActivityRepository
import com.truevault.core.data.VaultRepository
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.core.model.PrivacyScoreInputs
import com.truevault.core.model.calculatePrivacyScore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Dashboard state.
 *
 * The privacy score appears only once there is something to score. Showing "100%" for an empty
 * vault would be a reassuring number that means nothing, which is the failure mode this app exists
 * to avoid.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val timeProvider: TimeProvider,
    vaultRepository: VaultRepository,
    activityRepository: ActivityRepository,
    preferences: UserPreferencesDataSource,
    capabilityDetector: DeviceCapabilityDetector,
    privateAppsController: PrivateAppsController,
) : ViewModel() {

    private val deviceState = combine(
        capabilityDetector.observeCapabilities(),
        privateAppsController.observeState(),
    ) { capabilities, privateSpaceState -> capabilities to privateSpaceState }

    val uiState: StateFlow<HomeUiState> = combine(
        vaultRepository.observeItemCount(),
        vaultRepository.observeCategoryCounts(),
        vaultRepository.observeCountWithOriginalRemaining(),
        activityRepository.observeRecent(limit = 5),
        preferences.userPreferences,
        deviceState,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val totalItems = values[0] as Int
        @Suppress("UNCHECKED_CAST")
        val categoryCounts = values[1] as Map<com.truevault.core.model.MimeCategory, Int>
        val originalsRemaining = values[2] as Int
        @Suppress("UNCHECKED_CAST")
        val activity = values[3] as List<ActivityEvent>
        val prefs = values[4] as com.truevault.core.datastore.UserPreferences
        @Suppress("UNCHECKED_CAST")
        val device = values[5] as Pair<DeviceCapabilities, PrivateSpaceState>

        HomeUiState(
            isLoading = false,
            greeting = greetingFor(timeProvider.currentTimeMillis()),
            vaultIsEmpty = totalItems == 0,
            totalItems = totalItems,
            categoryCounts = categoryCounts,
            privacyScore = if (totalItems == 0) {
                null
            } else {
                calculatePrivacyScore(
                    PrivacyScoreInputs(
                        itemsWithOriginalRemaining = originalsRemaining,
                        backupConfigured = prefs.lastBackupAtMillis != null,
                        recoveryKeyConfigured = prefs.recoveryKeyConfigured,
                    ),
                )
            },
            recentActivity = activity.map(ActivityEvent::toHomeItem),
            capabilities = device.first,
            privateSpaceState = device.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private val _effects = MutableSharedFlow<HomeEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<HomeEffect> = _effects.asSharedFlow()

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> Unit
            HomeAction.AddFilesClicked -> emit(HomeEffect.NavigateToImport)
            HomeAction.RunScanClicked -> emit(HomeEffect.NavigateToScanner)
            HomeAction.PrivateAppsClicked -> emit(HomeEffect.NavigateToPrivateApps)
            HomeAction.BackupClicked -> emit(HomeEffect.NavigateToBackup)
            HomeAction.SecuritySettingsClicked -> emit(HomeEffect.NavigateToSecuritySettings)
            HomeAction.OpenVaultClicked -> emit(HomeEffect.NavigateToVault)
            is HomeAction.CategoryClicked -> emit(HomeEffect.NavigateToCategory(action.category))
        }
    }

    private fun emit(effect: HomeEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private fun greetingFor(timeMillis: Long): Greeting {
        val hour = Calendar.getInstance().apply { timeInMillis = timeMillis }.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> Greeting.MORNING
            in 12..16 -> Greeting.AFTERNOON
            else -> Greeting.EVENING
        }
    }
}

private fun ActivityEvent.toHomeItem() = HomeActivityItem(
    id = id,
    kind = when (kind) {
        ActivityKind.FILES_SECURED -> HomeActivityItem.Kind.FILES_SECURED
        ActivityKind.ORIGINAL_DELETED -> HomeActivityItem.Kind.ORIGINAL_DELETED
        ActivityKind.DUPLICATE_DETECTED -> HomeActivityItem.Kind.DUPLICATE_DETECTED
        ActivityKind.BACKUP_COMPLETED -> HomeActivityItem.Kind.BACKUP_COMPLETED
        ActivityKind.IMPORT_FAILED -> HomeActivityItem.Kind.IMPORT_FAILED
    },
    itemCount = itemCount,
    timestampMillis = timestampMillis,
)
