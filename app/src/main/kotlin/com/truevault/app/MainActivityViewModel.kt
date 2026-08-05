package com.truevault.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.crypto.session.VaultLockState
import com.truevault.core.crypto.session.VaultSession
import com.truevault.core.crypto.vault.VaultKeyManager
import com.truevault.core.datastore.UserPreferences
import com.truevault.core.datastore.UserPreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the app opens. Decided once, from state on disk plus the in-memory session. */
enum class StartDestination {
    ONBOARDING,
    CREATE_LOCK,
    UNLOCK,
    HOME,
}

sealed interface MainActivityUiState {

    data object Loading : MainActivityUiState

    data class Ready(
        val preferences: UserPreferences,
        val lockState: VaultLockState,
    ) : MainActivityUiState

    /**
     * Screenshot protection defaults to on while preferences are still loading. Starting protected
     * and relaxing later is safe; starting unprotected would expose the first frame.
     */
    val blockScreenshots: Boolean
        get() = when (this) {
            Loading -> true
            is Ready -> preferences.blockScreenshots
        }

    fun settingsOrDefault(): UserPreferences = when (this) {
        Loading -> UserPreferences()
        is Ready -> preferences
    }

    fun lockState(): VaultLockState = when (this) {
        Loading -> VaultLockState.Locked
        is Ready -> lockState
    }

    fun startDestination(): StartDestination = when (this) {
        Loading -> StartDestination.UNLOCK
        is Ready -> when {
            !preferences.hasCompletedOnboarding -> StartDestination.ONBOARDING
            lockState == VaultLockState.NotConfigured -> StartDestination.CREATE_LOCK
            lockState == VaultLockState.Locked -> StartDestination.UNLOCK
            else -> StartDestination.HOME
        }
    }
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    userPreferencesDataSource: UserPreferencesDataSource,
    private val keyManager: VaultKeyManager,
    private val session: VaultSession,
) : ViewModel() {

    val uiState: StateFlow<MainActivityUiState> = combine(
        userPreferencesDataSource.userPreferences,
        session.state,
    ) { preferences, lockState ->
        MainActivityUiState.Ready(preferences = preferences, lockState = lockState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MainActivityUiState.Loading,
    )

    init {
        // The session starts every process as "not configured" and is corrected here, once, by
        // asking disk whether a lock record exists. It is never seeded from a persisted "unlocked"
        // flag — a flag like that would survive reboots and defeat the lock entirely.
        viewModelScope.launch {
            session.setConfigured(keyManager.isVaultConfigured())
        }
    }
}
