package com.truevault.core.capabilities.provider

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import com.truevault.core.capabilities.model.PrivateSpaceState
import com.truevault.core.common.log.SecureLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PrivateSpace"

/**
 * Everything TrueVault knows about the platform's private profile.
 *
 * Every API 35 call in here is behind an SDK guard and a `@RequiresApi` method, so nothing on this
 * class path is even reachable on API 34 and below. `NoSuchMethodError` is never used as version
 * detection — that would mean discovering the platform by crashing into it.
 *
 * Unknown is always treated as unavailable. A capability that briefly claims to exist and then
 * vanishes is worse than one that appears a moment late.
 */
@Singleton
class PrivateSpaceCapabilityProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val managedProfileProvider: ManagedProfileProvider,
    private val launcherRoleProvider: LauncherRoleProvider,
) {

    private val launcherApps: LauncherApps?
        get() = context.getSystemService(LauncherApps::class.java)

    private val userManager: UserManager?
        get() = context.getSystemService(UserManager::class.java)

    /** True when this Android version has private profiles at all. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun isPlatformSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    /**
     * The current state.
     *
     * Note what this does *not* do: it never reports `NotConfigured` merely because the profile
     * cannot be seen. Without the Home role the platform hides private profiles from us entirely, so
     * "I cannot see one" and "there is not one" are indistinguishable — and saying the wrong one
     * would either hide a working feature or promise a broken one.
     */
    fun currentState(): PrivateSpaceState {
        if (!isPlatformSupported()) return PrivateSpaceState.Unsupported
        if (managedProfileProvider.profileCreationBlocked()) return PrivateSpaceState.RestrictedByPolicy

        return try {
            val profile = findPrivateProfile()
            when {
                profile == null && !launcherRoleProvider.isDefaultLauncher() ->
                    // Could be either. The UI offers guided setup, which is correct in both cases.
                    PrivateSpaceState.NotConfigured

                profile == null -> PrivateSpaceState.NotConfigured

                isProfileLocked(profile) -> PrivateSpaceState.ConfiguredLocked

                else -> PrivateSpaceState.ConfiguredUnlocked
            }
        } catch (e: SecurityException) {
            PrivateSpaceState.PermissionRequired
        } catch (e: Exception) {
            SecureLog.w(TAG, "Private profile state unavailable (${e.javaClass.simpleName})")
            PrivateSpaceState.Error("This device did not report its private space state.")
        }
    }

    /** The private profile handle, or null when there is none or it is not visible to us. */
    fun findPrivateProfile(): UserHandle? {
        if (!isPlatformSupported()) return null
        return try {
            findPrivateProfileApi35()
        } catch (e: Exception) {
            SecureLog.w(TAG, "Profile lookup failed (${e.javaClass.simpleName})")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun findPrivateProfileApi35(): UserHandle? {
        val apps = launcherApps ?: return null
        val self = Process.myUserHandle()

        return apps.profiles.firstOrNull { profile ->
            profile != self &&
                apps.getLauncherUserInfo(profile)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
        }
    }

    /** Private Space uses quiet mode when locked; its apps are stopped in that state. */
    fun isProfileLocked(profile: UserHandle): Boolean = try {
        userManager?.isQuietModeEnabled(profile) ?: true
    } catch (e: Exception) {
        // Unknown means locked: refusing to list apps is the safe failure.
        true
    }

    /** Work profiles, so the launcher can badge them separately and never confuse the two. */
    fun findWorkProfiles(): List<UserHandle> = try {
        val apps = launcherApps ?: return emptyList()
        val self = Process.myUserHandle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            apps.profiles.filter { profile ->
                profile != self &&
                    apps.getLauncherUserInfo(profile)?.userType == UserManager.USER_TYPE_PROFILE_MANAGED
            }
        } else {
            userManager?.userProfiles?.filter { it != self }.orEmpty()
        }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * A resolvable settings screen for setting Private Space up, or null.
     *
     * There is no public action that opens Private Space setup directly, so this lands on the
     * privacy settings screen and the UI tells the user what to tap. That is honest; inventing an
     * activity class name would work on one build and break on the next.
     */
    fun setupSettingsIntent(): Intent? {
        if (!isPlatformSupported()) return null
        val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { it.resolves() }
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
}
