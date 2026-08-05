package com.truevault.core.capabilities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.truevault.core.capabilities.model.DeviceCapabilities
import com.truevault.core.capabilities.model.PrivateAppsSupport
import com.truevault.core.capabilities.model.PrivateSpaceState
import com.truevault.core.capabilities.model.SecureHardwareCapability
import com.truevault.core.capabilities.model.TrueVaultProductMode
import com.truevault.core.capabilities.provider.BiometricCapabilityProvider
import com.truevault.core.capabilities.provider.DocumentDeleteCapabilityProvider
import com.truevault.core.capabilities.provider.LauncherRoleProvider
import com.truevault.core.capabilities.provider.ManagedProfileProvider
import com.truevault.core.capabilities.provider.MediaPickerCapabilityProvider
import com.truevault.core.capabilities.provider.OemSettingsCapabilityProvider
import com.truevault.core.capabilities.provider.PrivateSpaceCapabilityProvider
import com.truevault.core.common.dispatcher.ApplicationScope
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.common.log.SecureLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "Capabilities"

/**
 * Probes the device and keeps the answer current.
 *
 * Two rules shape everything here:
 *
 *  1. **Never infer a capability from the SDK level alone.** Android 15 means private profiles
 *     *exist as a platform feature*, not that this build, this policy and this launcher
 *     configuration allow them. Every value is observed.
 *  2. **Unknown means unavailable.** A screen that offers something the device cannot do is worse
 *     than one that appears a moment late.
 */
@Singleton
class DeviceCapabilityDetectorImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val privateSpaceProvider: PrivateSpaceCapabilityProvider,
    private val launcherRoleProvider: LauncherRoleProvider,
    private val managedProfileProvider: ManagedProfileProvider,
    private val biometricProvider: BiometricCapabilityProvider,
    private val mediaPickerProvider: MediaPickerCapabilityProvider,
    private val documentDeleteProvider: DocumentDeleteCapabilityProvider,
    private val oemSettingsProvider: OemSettingsCapabilityProvider,
    private val secureHardwareReporter: SecureHardwareReporter,
    @param:Dispatcher(TrueVaultDispatcher.Default) private val defaultDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : DeviceCapabilityDetector {

    private val state = MutableStateFlow(DeviceCapabilities.Unknown)

    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Private Space locking and unlocking arrives here. The UI must update without a
            // restart, so a broadcast simply re-probes.
            SecureLog.d(TAG, "Profile broadcast received; re-detecting")
            refresh()
        }
    }

    /** Registered once from the application. Unregistering is the process's business, not ours. */
    fun start() {
        refresh()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val filter = IntentFilter().apply {
                // Added in Android 14: profiles appearing and disappearing.
                addAction(Intent.ACTION_PROFILE_ADDED)
                addAction(Intent.ACTION_PROFILE_REMOVED)
                addAction(Intent.ACTION_PROFILE_ACCESSIBLE)
                addAction(Intent.ACTION_PROFILE_INACCESSIBLE)

                // Added in Android 15: Private Space locking and unlocking specifically.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    addAction(Intent.ACTION_PROFILE_AVAILABLE)
                    addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
                }
            }
            runCatching {
                ContextCompat.registerReceiver(
                    context,
                    profileReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }.onFailure {
                SecureLog.w(TAG, "Profile broadcasts unavailable on this device")
            }
        }
    }

    override fun observeCapabilities(): Flow<DeviceCapabilities> = state.asStateFlow()

    override fun refresh() {
        applicationScope.launch { state.value = detectCapabilities() }
    }

    override suspend fun detectCapabilities(): DeviceCapabilities = withContext(defaultDispatcher) {
        val sdkInt = Build.VERSION.SDK_INT
        val productMode = if (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            TrueVaultProductMode.MODERN
        } else {
            TrueVaultProductMode.CORE
        }

        val privateSpaceState = privateSpaceProvider.currentState()
        val isDefaultLauncher = launcherRoleProvider.isDefaultLauncher()
        val oemAvailable = oemSettingsProvider.isAvailable()

        DeviceCapabilities(
            sdkInt = sdkInt,
            productMode = productMode,
            privateAppsSupport = privateAppsSupportFor(
                state = privateSpaceState,
                isDefaultLauncher = isDefaultLauncher,
                oemAvailable = oemAvailable,
            ),
            privateSpaceAvailable = privateSpaceProvider.isPlatformSupported() &&
                privateSpaceState != PrivateSpaceState.Unsupported &&
                privateSpaceState != PrivateSpaceState.RestrictedByPolicy,
            privateSpaceConfigured = when (privateSpaceState) {
                PrivateSpaceState.ConfiguredLocked, PrivateSpaceState.ConfiguredUnlocked -> true
                PrivateSpaceState.NotConfigured -> false
                // Genuinely unknown without the Home role, and reported as such rather than guessed.
                else -> null
            },
            privateSpaceUnlocked = when (privateSpaceState) {
                PrivateSpaceState.ConfiguredUnlocked -> true
                PrivateSpaceState.ConfiguredLocked -> false
                else -> null
            },
            isDefaultLauncher = isDefaultLauncher,
            isManagedDevice = managedProfileProvider.isManagedDevice(),
            hasWorkProfile = managedProfileProvider.hasWorkProfile(),
            biometricCapability = biometricProvider.capability(),
            secureHardwareCapability = secureHardwareReporter.capability(),
            mediaPickerCapability = mediaPickerProvider.capability(),
            documentDeleteCapability = documentDeleteProvider.capability(),
            oemPrivacySettingsAvailable = oemAvailable,
        )
    }

    private fun privateAppsSupportFor(
        state: PrivateSpaceState,
        isDefaultLauncher: Boolean,
        oemAvailable: Boolean,
    ): PrivateAppsSupport = when (state) {
        PrivateSpaceState.Unsupported ->
            if (oemAvailable) PrivateAppsSupport.OEM_PRIVATE_SPACE_ONLY else PrivateAppsSupport.NOT_SUPPORTED

        PrivateSpaceState.RestrictedByPolicy -> PrivateAppsSupport.DEVICE_POLICY_BLOCKED
        PrivateSpaceState.NotConfigured -> PrivateAppsSupport.GUIDED_PRIVATE_SPACE_SETUP
        PrivateSpaceState.ConfiguredLocked -> PrivateAppsSupport.PRIVATE_SPACE_LOCKED

        PrivateSpaceState.ConfiguredUnlocked -> if (isDefaultLauncher) {
            PrivateAppsSupport.FULL_LAUNCHER_INTEGRATION
        } else {
            PrivateAppsSupport.PRIVATE_SPACE_ALREADY_CONFIGURED
        }

        PrivateSpaceState.HomeRoleRequired -> PrivateAppsSupport.HOME_ROLE_REQUIRED
        PrivateSpaceState.PermissionRequired -> PrivateAppsSupport.PERMISSION_REQUIRED
        is PrivateSpaceState.Error -> PrivateAppsSupport.UNKNOWN
    }
}

/**
 * Reports whether Keystore keys sit in secure hardware.
 *
 * Implemented in `:core:crypto` and bound here, so the capability layer does not have to reach into
 * key management and the crypto layer does not have to know about capability models.
 */
interface SecureHardwareReporter {
    fun capability(): SecureHardwareCapability
}
