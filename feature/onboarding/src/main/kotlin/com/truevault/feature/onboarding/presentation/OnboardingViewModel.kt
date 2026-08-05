package com.truevault.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.capabilities.DeviceCapabilityDetector
import com.truevault.core.capabilities.model.TrueVaultProductMode
import com.truevault.core.datastore.UserPreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: UserPreferencesDataSource,
    capabilityDetector: DeviceCapabilityDetector,
) : ViewModel() {

    /**
     * The introduction itself is identical on every device — encryption, Copy or Move, remaining
     * copies, recovery. Only the final screen differs, and it differs by what was detected rather
     * than by SDK level alone.
     */
    val productMode: StateFlow<TrueVaultProductMode> = capabilityDetector.observeCapabilities()
        .map { it.productMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TrueVaultProductMode.CORE,
        )

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
