package com.truevault.app

import android.app.Application
import com.truevault.app.lifecycle.AutoLockController
import com.truevault.core.capabilities.DeviceCapabilityDetectorImpl
import com.truevault.core.common.log.SecureLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TrueVaultApplication : Application() {

    @Inject
    lateinit var autoLockController: AutoLockController

    @Inject
    lateinit var capabilityDetector: DeviceCapabilityDetectorImpl

    override fun onCreate() {
        super.onCreate()

        // Logging is enabled only for debuggable builds. Release builds emit nothing, so no
        // metadata about a user's files can reach logcat on a shipped device.
        SecureLog.configure(enabled = BuildConfig.DEBUG)

        // Auto-lock must be watching before any screen can be shown, not from the first Composable.
        autoLockController.start()

        // Capability detection starts here too, and registers for profile broadcasts, so a Private
        // Space that is locked or unlocked while TrueVault is running updates the UI without a
        // restart.
        capabilityDetector.start()
    }
}
