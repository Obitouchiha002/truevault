package com.truevault.core.capabilities.provider

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.UserManager
import com.truevault.core.common.log.SecureLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ManagedProfile"

/**
 * Work-profile and managed-device awareness.
 *
 * A work profile is an enterprise-managed environment, not a consumer Private Space. TrueVault
 * detects one, behaves correctly inside one, and respects administrator restrictions — but it never
 * provisions one, never asks for device-owner rights, and never presents a work profile as
 * user-owned private space.
 *
 * Each profile is a separate Android user, so it already gets its own app storage, its own Room
 * database, its own Keystore keys and its own vault session. Encryption keys are never shared
 * across profiles, because the platform gives each profile a different Keystore.
 */
@Singleton
class ManagedProfileProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val userManager: UserManager?
        get() = context.getSystemService(UserManager::class.java)

    private val devicePolicyManager: DevicePolicyManager?
        get() = context.getSystemService(DevicePolicyManager::class.java)

    /**
     * True when TrueVault itself is running inside a work profile.
     *
     * The no-argument `isManagedProfile()` only became callable by non-admin apps in Android 11.
     * Below that the answer is unknown, and unknown is reported as "no" rather than guessed.
     */
    fun isRunningInWorkProfile(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            userManager?.isManagedProfile == true
        } catch (e: Exception) {
            false
        }
    }

    /** True when a work profile exists alongside this user. */
    fun hasWorkProfile(): Boolean = try {
        val manager = userManager ?: return false
        // Not every non-main profile is a work profile — clone and private profiles exist too — so
        // the check is made against the profile's actual type where the platform exposes it.
        manager.userProfiles.any { profile ->
            profile != android.os.Process.myUserHandle() && isManagedProfile(profile)
        }
    } catch (e: Exception) {
        SecureLog.w(TAG, "Profile enumeration unavailable (${e.javaClass.simpleName})")
        false
    }

    /**
     * True when a device policy is in force that restricts what TrueVault may offer.
     *
     * Detected through actual restrictions rather than through the presence of an admin: a device
     * can have an administrator that restricts nothing relevant.
     */
    fun isManagedDevice(): Boolean = try {
        val policy = devicePolicyManager
        val restricted = userManager?.hasUserRestriction(UserManager.DISALLOW_ADD_USER) == true
        val hasOwner = policy?.isDeviceOwnerApp(context.packageName) == true ||
            policy?.isProfileOwnerApp(context.packageName) == true
        restricted || hasOwner || isRunningInWorkProfile()
    } catch (e: Exception) {
        false
    }

    /** True when the platform forbids creating additional profiles at all. */
    fun profileCreationBlocked(): Boolean = try {
        userManager?.hasUserRestriction(UserManager.DISALLOW_ADD_USER) == true
    } catch (e: Exception) {
        false
    }

    private fun isManagedProfile(profile: android.os.UserHandle): Boolean = try {
        val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && launcherApps != null) {
            launcherApps.getLauncherUserInfo(profile)?.userType == UserManager.USER_TYPE_PROFILE_MANAGED
        } else {
            // Before API 35 there is no per-profile type to read, so any secondary profile on a
            // device with a policy is treated as managed. Over-reporting here is safe: it only makes
            // TrueVault more conservative.
            devicePolicyManager != null
        }
    } catch (e: Exception) {
        false
    }
}
