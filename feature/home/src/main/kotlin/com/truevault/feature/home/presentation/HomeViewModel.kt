package com.truevault.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.common.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Dashboard view model.
 *
 * Phase 0 scope: the screen contract, the greeting, and the genuinely-empty initial state of a
 * fresh install. Vault counts, the privacy score and recent activity are wired to their
 * repositories in Phase 2 and Phase 3 — until then this reports an empty vault, which is exactly
 * what a fresh install has. No sample data and no placeholder score is ever shown.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<HomeEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<HomeEffect> = _effects.asSharedFlow()

    init {
        refresh()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> refresh()
            HomeAction.AddFilesClicked -> emit(HomeEffect.NavigateToImport)
            HomeAction.RunScanClicked -> emit(HomeEffect.NavigateToScanner)
            HomeAction.PrivateAppsClicked -> emit(HomeEffect.NavigateToPrivateApps)
            HomeAction.BackupClicked -> emit(HomeEffect.NavigateToBackup)
            HomeAction.OpenVaultClicked -> emit(HomeEffect.NavigateToVault)
            is HomeAction.CategoryClicked -> emit(HomeEffect.NavigateToCategory(action.category))
        }
    }

    private fun refresh() {
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                greeting = greetingFor(timeProvider.currentTimeMillis()),
            )
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
