package com.truevault.core.capabilities

import com.truevault.core.capabilities.model.DeviceCapabilities
import kotlinx.coroutines.flow.Flow

/**
 * The single source of truth for what this device can do.
 *
 * Capabilities are not constants. Private Space can be created or locked, the Home role can be
 * granted or taken away, a biometric can be enrolled, and a device policy can arrive — all while
 * TrueVault is running. Everything that depends on a capability observes this flow rather than
 * reading a value once at startup.
 */
interface DeviceCapabilityDetector {

    suspend fun detectCapabilities(): DeviceCapabilities

    fun observeCapabilities(): Flow<DeviceCapabilities>

    /** Re-probes. Called on resume, on return from system settings, and on profile broadcasts. */
    fun refresh()
}
