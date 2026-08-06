package com.truevault.core.capabilities.provider

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.truevault.core.common.log.SecureLog
import com.truevault.core.model.AppearanceProfile
import com.truevault.core.model.AppearanceSwitchResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Appearance"

/**
 * Switches which launcher entry the app presents.
 *
 * Implemented with manifest-declared `<activity-alias>` components and
 * `PackageManager.setComponentEnabledSetting`. That is the documented mechanism; nothing here uses
 * a hidden API, downloads an icon, or accepts an icon from an untrusted source.
 *
 * The ordering is the whole point. Enabling the new alias comes **first**, and only after it is
 * confirmed enabled is the previous one disabled. In the other order, a process death between the
 * two calls would leave the app with no launcher entry at all — and a user cannot reopen an app to
 * fix a setting that removed the way to open it.
 *
 * [repair] exists for the case where that happened anyway.
 */
@Singleton
class LauncherAppearanceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val packageManager: PackageManager get() = context.packageManager

    /** The alias that is currently enabled, or the default when the state is unreadable. */
    fun currentProfile(): AppearanceProfile =
        AppearanceProfile.entries.firstOrNull { isEnabled(it) } ?: AppearanceProfile.DEFAULT

    fun apply(profile: AppearanceProfile): AppearanceSwitchResult {
        val current = currentProfile()
        if (current == profile && isEnabled(profile)) return AppearanceSwitchResult.NoChange

        // 1. Enable the new entry.
        val enabled = runCatching { setEnabled(profile, true) }.isSuccess
        if (!enabled || !isEnabled(profile)) {
            SecureLog.w(TAG, "New launcher alias could not be enabled; leaving the old one in place")
            return AppearanceSwitchResult.Failed("The launcher entry could not be changed.")
        }

        // 2. Only now disable everything else. A failure here leaves two icons, which is untidy and
        //    recoverable; the reverse leaves none, which is not.
        var allDisabled = true
        AppearanceProfile.entries
            .filter { it != profile }
            .forEach { other ->
                val ok = runCatching { setEnabled(other, false) }.isSuccess
                if (!ok) allDisabled = false
            }

        SecureLog.i(TAG, "Launcher appearance applied (complete=$allDisabled)")

        return if (allDisabled) {
            AppearanceSwitchResult.Applied(profile)
        } else {
            AppearanceSwitchResult.PartiallyApplied(profile)
        }
    }

    /**
     * Restores a launcher entry when none is enabled.
     *
     * Called at startup. If a switch was interrupted at exactly the wrong moment, the app is still
     * running — it was opened from Recents, or by a share — and this is the one chance to put an
     * icon back before the user closes it and cannot find it again.
     */
    fun repair(): Boolean {
        if (AppearanceProfile.entries.any { isEnabled(it) }) return false

        SecureLog.w(TAG, "No launcher alias enabled; restoring the default")
        return runCatching { setEnabled(AppearanceProfile.DEFAULT, true) }.isSuccess
    }

    private fun isEnabled(profile: AppearanceProfile): Boolean =
        when (packageManager.getComponentEnabledSetting(componentFor(profile))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            // DEFAULT means "whatever the manifest says". Only the default profile ships enabled.
            else -> profile == AppearanceProfile.DEFAULT
        }

    private fun setEnabled(profile: AppearanceProfile, enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            componentFor(profile),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            // The app must keep running through the switch — killing it here would look like a
            // crash to anyone who just changed a setting.
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun componentFor(profile: AppearanceProfile): ComponentName {
        val alias = when (profile) {
            AppearanceProfile.TRUE_VAULT -> "com.truevault.app.launcher.TrueVaultAlias"
            AppearanceProfile.NEXA -> "com.truevault.app.launcher.NexaAlias"
            AppearanceProfile.NEXA_NOTES -> "com.truevault.app.launcher.NexaNotesAlias"
            AppearanceProfile.NEXA_FILES -> "com.truevault.app.launcher.NexaFilesAlias"
        }
        return ComponentName(context.packageName, alias)
    }
}
