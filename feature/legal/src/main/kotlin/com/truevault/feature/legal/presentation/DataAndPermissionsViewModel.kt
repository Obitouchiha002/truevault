package com.truevault.feature.legal.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.capabilities.DeviceCapabilityDetector
import com.truevault.core.capabilities.model.BiometricCapability
import com.truevault.core.capabilities.model.PrivateAppsSupport
import com.truevault.feature.legal.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DataAndPermissionsUiState(
    val biometricsAvailable: Boolean = false,
    val privateAppsAvailable: Boolean = false,
    val onDeviceProcessing: List<String> = emptyList(),
)

/**
 * Builds the dashboard from what this device actually reports.
 *
 * The alternative — a hardcoded list — would tell a user on Android 13 that TrueVault reads
 * private-profile app information, which on their device it cannot and does not. A disclosure that
 * over-claims is as misleading as one that under-claims; it just fails in the direction that looks
 * responsible.
 */
@HiltViewModel
class DataAndPermissionsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val capabilityDetector: DeviceCapabilityDetector,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DataAndPermissionsUiState(
            onDeviceProcessing = listOf(
                context.getString(R.string.legal_data_encryption),
                context.getString(R.string.legal_data_hashing),
                context.getString(R.string.legal_data_thumbnails),
                context.getString(R.string.legal_data_privacy_score),
            ),
        ),
    )
    val uiState: StateFlow<DataAndPermissionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            capabilityDetector.observeCapabilities().collect { capabilities ->
                _uiState.update {
                    it.copy(
                        biometricsAvailable =
                            capabilities.biometricCapability == BiometricCapability.AVAILABLE,
                        privateAppsAvailable = capabilities.privateAppsSupport in AVAILABLE_SUPPORT,
                    )
                }
            }
        }
    }

    private companion object {
        /**
         * The states in which TrueVault genuinely can read private-profile app information.
         *
         * `GUIDED_PRIVATE_SPACE_SETUP` is excluded on purpose: the platform supports it, but nothing
         * has been set up, so nothing is read yet.
         */
        val AVAILABLE_SUPPORT = setOf(
            PrivateAppsSupport.FULL_LAUNCHER_INTEGRATION,
            PrivateAppsSupport.PRIVATE_SPACE_ALREADY_CONFIGURED,
            PrivateAppsSupport.PRIVATE_SPACE_LOCKED,
        )
    }
}
