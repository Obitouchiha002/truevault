package com.truevault.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.datastore.UserPreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: UserPreferencesDataSource,
) : ViewModel() {

    /**
     * Records that the introduction has been seen and hands control to the caller.
     *
     * Skipping and finishing are the same action deliberately: a user who skips has still decided,
     * and re-showing the introduction on the next launch would be nagging.
     */
    fun onFinished(navigateOn: () -> Unit) {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
            navigateOn()
        }
    }
}
