package com.truevault.core.capabilities.privateapps

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.RequiresApi
import com.truevault.core.capabilities.model.CapabilityActionResult
import com.truevault.core.capabilities.model.LauncherAppEntry
import com.truevault.core.capabilities.model.PrivateAppId
import com.truevault.core.capabilities.model.PrivateSpaceState
import com.truevault.core.capabilities.provider.LauncherRoleProvider
import com.truevault.core.capabilities.provider.PrivateSpaceCapabilityProvider
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.common.log.SecureLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val TAG = "PrivateApps35"

/**
 * Android 15+ private apps, through the platform's supported profile APIs and nothing else.
 *
 * What this class will never do, because none of it is supported and all of it would be a lie:
 * copy APKs into the vault, execute an APK from internal storage, build a virtual container, clone
 * an app with an unofficial framework, install or uninstall silently, copy another app's private
 * data, transfer a login session, use an accessibility service, use root, use Shizuku, use hidden
 * APIs, or take device-owner privileges.
 *
 * What it does: read profile state, list apps the platform lets a launcher see, and start the
 * system's own screens. Installation into Private Space is done by the user, in Android, from
 * inside the profile.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class Android15PrivateAppsController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val privateSpaceProvider: PrivateSpaceCapabilityProvider,
    private val launcherRoleProvider: LauncherRoleProvider,
    @param:Dispatcher(TrueVaultDispatcher.Default) private val defaultDispatcher: CoroutineDispatcher,
) : PrivateAppsController {

    private val state = MutableStateFlow<PrivateSpaceState>(PrivateSpaceState.Unsupported)

    private val launcherApps: LauncherApps?
        get() = context.getSystemService(LauncherApps::class.java)

    override fun observeState(): Flow<PrivateSpaceState> = state.asStateFlow()

    override suspend fun refresh() {
        state.value = withContext(defaultDispatcher) { privateSpaceProvider.currentState() }
    }

    override suspend fun openSetup(): CapabilityActionResult = withContext(defaultDispatcher) {
        when (privateSpaceProvider.currentState()) {
            PrivateSpaceState.RestrictedByPolicy -> CapabilityActionResult.RestrictedByPolicy
            PrivateSpaceState.Unsupported -> CapabilityActionResult.Unsupported
            else -> {
                val intent = privateSpaceProvider.setupSettingsIntent()
                    ?: return@withContext CapabilityActionResult.SettingsUnavailable
                try {
                    context.startActivity(intent)
                    CapabilityActionResult.Success
                } catch (e: SecurityException) {
                    CapabilityActionResult.PermissionRequired
                } catch (e: Exception) {
                    SecureLog.w(TAG, "Settings activity refused (${e.javaClass.simpleName})")
                    CapabilityActionResult.SettingsUnavailable
                }
            }
        }
    }

    override suspend fun requestLauncherRole(): CapabilityActionResult =
        withContext(defaultDispatcher) {
            if (launcherRoleProvider.isDefaultLauncher()) return@withContext CapabilityActionResult.Success
            val intent = launcherRoleProvider.requestRoleIntent()
                ?: return@withContext CapabilityActionResult.Unsupported

            try {
                context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                // Success here means the dialog opened. Whether the role was granted is re-detected
                // on resume; assuming it would be exactly the kind of unverified claim this app
                // exists to avoid.
                CapabilityActionResult.Success
            } catch (e: Exception) {
                CapabilityActionResult.SettingsUnavailable
            }
        }

    override suspend fun openPrivateApp(appId: PrivateAppId): CapabilityActionResult =
        withContext(defaultDispatcher) {
            val apps = launcherApps ?: return@withContext CapabilityActionResult.Unsupported
            val profile = profileFor(appId.userSerialNumber)
                ?: return@withContext CapabilityActionResult.Unsupported

            if (privateSpaceProvider.isProfileLocked(profile)) {
                return@withContext CapabilityActionResult.RestrictedByPolicy
            }

            try {
                apps.startMainActivity(
                    android.content.ComponentName(appId.packageName, appId.componentName),
                    profile,
                    null,
                    null,
                )
                CapabilityActionResult.Success
            } catch (e: SecurityException) {
                CapabilityActionResult.RoleRequired
            } catch (e: IllegalStateException) {
                // The profile went away, or the app was removed while the screen was open.
                CapabilityActionResult.Unsupported
            } catch (e: Exception) {
                SecureLog.w(TAG, "Launch failed (${e.javaClass.simpleName})")
                CapabilityActionResult.Failure("That app could not be opened.")
            }
        }

    override suspend fun listApps(): List<LauncherAppEntry> = withContext(defaultDispatcher) {
        val apps = launcherApps ?: return@withContext emptyList()
        val self = Process.myUserHandle()

        try {
            apps.profiles.flatMap { profile ->
                val userType = runCatching { apps.getLauncherUserInfo(profile)?.userType }.getOrNull()
                val isPrivate = userType == UserManager.USER_TYPE_PROFILE_PRIVATE
                val isWork = userType == UserManager.USER_TYPE_PROFILE_MANAGED

                // A locked private profile contributes nothing. Its app names must not reach the
                // UI, a cache or a log while it is locked.
                if (isPrivate && privateSpaceProvider.isProfileLocked(profile)) {
                    return@flatMap emptyList()
                }

                apps.getActivityList(null, profile).map { info ->
                    LauncherAppEntry(
                        id = PrivateAppId(
                            packageName = info.componentName.packageName,
                            componentName = info.componentName.className,
                            userSerialNumber = serialNumberOf(profile),
                        ),
                        label = info.label.toString(),
                        isPrivateProfile = isPrivate,
                        isWorkProfile = isWork,
                    )
                }
            }.sortedBy { it.label.lowercase() }
        } catch (e: SecurityException) {
            // Without the Home role the platform refuses. That is not an error to report as one.
            SecureLog.d(TAG, "App listing requires the Home role")
            emptyList()
        } catch (e: Exception) {
            SecureLog.w(TAG, "App listing failed (${e.javaClass.simpleName})")
            emptyList()
        }
    }

    override suspend fun isInstalledInPrivateProfile(packageName: String): Boolean =
        withContext(defaultDispatcher) {
            val apps = launcherApps ?: return@withContext false
            val profile = privateSpaceProvider.findPrivateProfile() ?: return@withContext false
            if (privateSpaceProvider.isProfileLocked(profile)) return@withContext false

            try {
                apps.getActivityList(packageName, profile).isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }

    private fun profileFor(serialNumber: Int): UserHandle? = try {
        val userManager = context.getSystemService(UserManager::class.java)
        launcherApps?.profiles?.firstOrNull { profile ->
            userManager?.getSerialNumberForUser(profile)?.toInt() == serialNumber
        }
    } catch (e: Exception) {
        null
    }

    private fun serialNumberOf(profile: UserHandle): Int = try {
        context.getSystemService(UserManager::class.java)
            ?.getSerialNumberForUser(profile)?.toInt() ?: 0
    } catch (e: Exception) {
        0
    }
}
