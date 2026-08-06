package com.truevault.core.capabilities.provider

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.truevault.core.common.log.SecureLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LauncherComponent"

/**
 * Switches TrueVault's home-screen activity on and off.
 *
 * ## Why this exists
 *
 * An activity that declares `category.HOME` becomes a candidate launcher the moment the app is
 * installed. Android then asks "Which app do you want to use as Home?" **every time the user presses
 * the Home button**, until they pick a default — even if they never went near Secure Launcher Mode.
 *
 * That is exactly the behaviour the specification forbids: launcher mode must never be forced during
 * onboarding, and TrueVault must not change how the user's phone behaves without being asked. So the
 * component ships disabled and is enabled only when the user turns Secure Launcher Mode on.
 *
 * Disabling it again removes TrueVault from the home-app chooser entirely. If it was the active home
 * app, Android falls back to the system launcher on its own.
 */
@Singleton
class SecureLauncherComponent @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val component: ComponentName
        get() = ComponentName(context, "com.truevault.app.SecureLauncherActivity")

    val isEnabled: Boolean
        get() = try {
            context.packageManager.getComponentEnabledSetting(component) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } catch (e: IllegalArgumentException) {
            false
        }

    /**
     * @return true when the component now matches [enabled].
     *
     * `DONT_KILL_APP` matters: without it, enabling the component would immediately kill the process
     * that the user is standing in.
     */
    fun setEnabled(enabled: Boolean): Boolean = try {
        context.packageManager.setComponentEnabledSetting(
            component,
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
        SecureLog.i(TAG, "Secure Launcher component ${if (enabled) "enabled" else "disabled"}")
        true
    } catch (e: Exception) {
        SecureLog.w(TAG, "Could not change the launcher component (${e.javaClass.simpleName})")
        false
    }
}
