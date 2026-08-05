package com.truevault.core.capabilities.provider

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import com.truevault.core.common.log.SecureLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LauncherRole"

/**
 * Whether TrueVault is the device's Home app.
 *
 * The Home role is never requested during onboarding and never implied. Secure Launcher Mode is the
 * only thing that needs it, it lives behind Settings → Advanced Privacy, and the user is told
 * exactly what changes before the system dialog appears.
 */
@Singleton
class LauncherRoleProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * The role API arrived in Android 10. Below that there is no supported way to ask, so every
     * entry point reports "no" rather than reaching for a hidden API.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    private fun isRoleApiAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** True when the Home role exists on this device at all. */
    fun isRoleAvailable(): Boolean {
        if (!isRoleApiAvailable()) return false
        return try {
            roleManager()?.isRoleAvailable(RoleManager.ROLE_HOME) == true
        } catch (e: Exception) {
            SecureLog.w(TAG, "Home role availability unknown (${e.javaClass.simpleName})")
            false
        }
    }

    fun isDefaultLauncher(): Boolean {
        if (!isRoleApiAvailable()) return false
        return try {
            roleManager()?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } catch (e: Exception) {
            SecureLog.w(TAG, "Home role state unknown (${e.javaClass.simpleName})")
            false
        }
    }

    /** The system dialog that asks the user to make TrueVault the Home app, or null. */
    fun requestRoleIntent(): Intent? {
        if (!isRoleApiAvailable()) return null
        return try {
            val manager = roleManager() ?: return null
            if (!manager.isRoleAvailable(RoleManager.ROLE_HOME)) return null
            manager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } catch (e: Exception) {
            SecureLog.w(TAG, "Home role request unavailable (${e.javaClass.simpleName})")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun roleManager(): RoleManager? = context.getSystemService(RoleManager::class.java)
}
