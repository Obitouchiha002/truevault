package com.truevault.feature.launcher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.capabilities.provider.LauncherAppearanceController
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.core.model.AppearanceProfile
import com.truevault.core.model.AppearanceSwitchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppearanceUiState(
    val current: AppearanceProfile = AppearanceProfile.DEFAULT,
    val selected: AppearanceProfile = AppearanceProfile.DEFAULT,
    val isApplying: Boolean = false,
    val lastResult: AppearanceSwitchResult? = null,
) {
    val hasChange: Boolean get() = selected != current
}

sealed interface AppearanceAction {
    data class ProfileSelected(val profile: AppearanceProfile) : AppearanceAction
    data object ApplyRequested : AppearanceAction
    data object ResultDismissed : AppearanceAction
}

/**
 * Choosing what the app looks like in the launcher.
 *
 * The selection is separate from applying it. Switching a launcher alias makes the icon disappear
 * and reappear, and doing that on every tap while someone browses the options would be alarming.
 * They pick, then confirm.
 *
 * The stored preference is written **after** the switch succeeds. Recording an intention the
 * platform refused would leave settings describing an icon the user does not have.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val controller: LauncherAppearanceController,
    private val preferences: UserPreferencesDataSource,
    @param:Dispatcher(TrueVaultDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppearanceUiState())
    val uiState: StateFlow<AppearanceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Read from the package manager, not from preferences: the launcher is the source of
            // truth for what the user actually sees, and a switch that half-failed must show up
            // here rather than being papered over by a stored value.
            val active = withContext(ioDispatcher) { controller.currentProfile() }
            _uiState.update { it.copy(current = active, selected = active) }
        }
    }

    fun onAction(action: AppearanceAction) {
        when (action) {
            is AppearanceAction.ProfileSelected ->
                _uiState.update { it.copy(selected = action.profile, lastResult = null) }

            AppearanceAction.ApplyRequested -> apply()

            AppearanceAction.ResultDismissed ->
                _uiState.update { it.copy(lastResult = null) }
        }
    }

    private fun apply() {
        val target = _uiState.value.selected
        if (_uiState.value.isApplying) return

        _uiState.update { it.copy(isApplying = true) }

        viewModelScope.launch {
            val result = withContext(ioDispatcher) { controller.apply(target) }

            if (result is AppearanceSwitchResult.Applied ||
                result is AppearanceSwitchResult.PartiallyApplied
            ) {
                preferences.setAppearanceProfile(target)
            }

            val active = withContext(ioDispatcher) { controller.currentProfile() }

            _uiState.update {
                it.copy(
                    isApplying = false,
                    current = active,
                    selected = active,
                    lastResult = result,
                )
            }
        }
    }
}
