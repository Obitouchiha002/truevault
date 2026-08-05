package com.truevault.feature.privateapps.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import com.truevault.core.model.PrivateAppsCapability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What this device can actually do for Private Apps.
 *
 * TrueVault does not clone apps, virtualise APKs, install packages silently, run an accessibility
 * service, or take device-admin rights. None of those are supported ways to hide an app, and every
 * app that claims to do them either requires root, breaks on the next Android release, or is simply
 * lying about what it achieved.
 *
 * What Android does offer, from Android 15, is Private Space: a separate user profile with its own
 * apps and data, locked behind its own credential and hidden from the launcher. TrueVault can detect
 * it and walk the user into the system's own setup. It cannot create or manage one — no public API
 * exists for that, and pretending otherwise would be the fake functionality this app refuses to
 * build.
 */
@Singleton
class PrivateAppsCapabilityDetector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun detect(): PrivateAppsCapability {
        val userManager = context.getSystemService(UserManager::class.java)
            ?: return PrivateAppsCapability.UNKNOWN

        // A work profile or a managed device can forbid extra profiles outright. Saying "not
        // supported" there is accurate; sending the user to a settings screen that will refuse them
        // is not.
        if (userManager.hasUserRestriction(UserManager.DISALLOW_ADD_USER)) {
            return PrivateAppsCapability.MANAGED_DEVICE_RESTRICTED
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return PrivateAppsCapability.NOT_SUPPORTED
        }

        // Detected by whether the system actually has a screen to send the user to, rather than by
        // assuming an API level implies a feature. OEM builds vary.
        return if (privacySettingsIntent().resolveActivityCompat() != null) {
            PrivateAppsCapability.SUPPORTED_GUIDED_SETUP
        } else {
            PrivateAppsCapability.NOT_SUPPORTED
        }
    }

    /** The system screen where Private Space lives. Null when this device has no such screen. */
    fun settingsIntentOrNull(): Intent? =
        privacySettingsIntent().takeIf { it.resolveActivityCompat() != null }

    private fun privacySettingsIntent() = Intent(Settings.ACTION_PRIVACY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun Intent.resolveActivityCompat() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(
                this,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(this, 0)
        }
}
