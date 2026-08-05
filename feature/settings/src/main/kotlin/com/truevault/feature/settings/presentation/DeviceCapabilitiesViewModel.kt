package com.truevault.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.capabilities.DeviceCapabilityDetector
import com.truevault.core.capabilities.model.DeviceCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The Device Capabilities screen.
 *
 * It reports facts, not marketing. Every line is something that was actually detected, and a
 * capability that could not be determined reads as unavailable rather than as a guess.
 */
@HiltViewModel
class DeviceCapabilitiesViewModel @Inject constructor(
    private val detector: DeviceCapabilityDetector,
) : ViewModel() {

    val capabilities: StateFlow<DeviceCapabilities> = detector.observeCapabilities()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeviceCapabilities.Unknown,
        )

    fun refresh() = detector.refresh()
}
