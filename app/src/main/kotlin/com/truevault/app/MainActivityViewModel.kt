package com.truevault.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.datastore.UserPreferences
import com.truevault.core.datastore.UserPreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Top-level state: which theme to draw, whether screenshots are blocked, and whether the first-run
 * flow still needs to happen.
 */
sealed interface MainActivityUiState {

    data object Loading : MainActivityUiState

    data class Ready(val preferences: UserPreferences) : MainActivityUiState

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
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    userPreferencesDataSource: UserPreferencesDataSource,
) : ViewModel() {

    val uiState: StateFlow<MainActivityUiState> = userPreferencesDataSource.userPreferences
        .map<UserPreferences, MainActivityUiState> { MainActivityUiState.Ready(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MainActivityUiState.Loading,
        )
}
