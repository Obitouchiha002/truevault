package com.truevault.core.capabilities.provider

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.truevault.core.common.log.SecureLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OemSettings"

/**
 * Manufacturer privacy features — Secure Folder, Private Safe, Second Space, App Lock and friends.
 *
 * These are **external** features. TrueVault neither owns nor manages what is inside them, and it
 * never reads or collects the list of apps they contain.
 *
 * Detection is by resolvable intent, never by `Build.MANUFACTURER`. A manufacturer name tells you
 * who made the phone, not whether this particular build, region or Android version ships the
 * feature — and hardcoding undocumented activity class names produces an app that crashes or lies
 * on the next firmware update.
 */
@Singleton
class OemSettingsCapabilityProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * A resolvable system settings screen where device privacy features live, or null.
     *
     * Only documented, public settings actions are used. If none resolves, the UI falls back to
     * manual instructions instead of firing an intent that will fail.
     */
    fun privacySettingsIntent(): Intent? = CANDIDATE_ACTIONS
        .asSequence()
        .map { action -> Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        .firstOrNull { intent -> intent.resolves() }

    fun isAvailable(): Boolean = privacySettingsIntent() != null

    /** Starts the settings screen, reporting failure instead of crashing. */
    fun openPrivacySettings(): Boolean {
        val intent = privacySettingsIntent() ?: return false
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            SecureLog.w(TAG, "Settings activity refused to start (${e.javaClass.simpleName})")
            false
        }
    }

    private fun Intent.resolves(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(this, PackageManager.ResolveInfoFlags.of(0L)) != null
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(this, 0) != null
        }
    } catch (e: Exception) {
        false
    }

    private companion object {
        /** Public, documented settings actions only. No OEM activity class names. */
        val CANDIDATE_ACTIONS = listOf(
            Settings.ACTION_PRIVACY_SETTINGS,
            Settings.ACTION_SECURITY_SETTINGS,
            Settings.ACTION_SETTINGS,
        )
    }
}
