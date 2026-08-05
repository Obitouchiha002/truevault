package com.truevault.app

import android.app.Application
import com.truevault.core.common.log.SecureLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrueVaultApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Logging is enabled only for debuggable builds. Release builds emit nothing, so no
        // metadata about a user's files can reach logcat on a shipped device.
        SecureLog.configure(enabled = BuildConfig.DEBUG)
    }
}
