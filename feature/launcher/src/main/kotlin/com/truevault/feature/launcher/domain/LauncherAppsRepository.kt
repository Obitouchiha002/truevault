package com.truevault.feature.launcher.domain

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import com.truevault.core.capabilities.model.LauncherAppEntry
import com.truevault.core.capabilities.model.PrivateAppId
import com.truevault.core.capabilities.privateapps.PrivateAppsController
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.common.log.SecureLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

private const val TAG = "LauncherRepo"

/**
 * The apps TrueVault's launcher shows.
 *
 * Package visibility is deliberately narrow: `LauncherApps.getActivityList` returns launchable
 * activities in profiles the platform already allows a Home app to see. TrueVault does not declare
 * `QUERY_ALL_PACKAGES`, does not enumerate non-launchable packages, and never sends installed-app
 * information anywhere — there is no analytics in this app at all.
 */
@Singleton
class LauncherAppsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val privateAppsController: PrivateAppsController,
    @param:Dispatcher(TrueVaultDispatcher.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    private val launcherApps: LauncherApps?
        get() = context.getSystemService(LauncherApps::class.java)

    suspend fun mainProfileApps(): List<LauncherAppEntry> = withContext(defaultDispatcher) {
        val apps = launcherApps ?: return@withContext emptyList()
        val self = Process.myUserHandle()

        try {
            apps.getActivityList(null, self).map { info ->
                LauncherAppEntry(
                    id = PrivateAppId(
                        packageName = info.componentName.packageName,
                        componentName = info.componentName.className,
                        userSerialNumber = 0,
                    ),
                    label = info.label.toString(),
                    isPrivateProfile = false,
                    isWorkProfile = false,
                )
            }.sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            SecureLog.w(TAG, "Main profile listing failed (${e.javaClass.simpleName})")
            emptyList()
        }
    }

    /** Private and work profile apps. Empty while a private profile is locked, by construction. */
    suspend fun otherProfileApps(): List<LauncherAppEntry> = privateAppsController.listApps()

    suspend fun launch(id: PrivateAppId, isMainProfile: Boolean) = withContext(defaultDispatcher) {
        if (!isMainProfile) return@withContext privateAppsController.openPrivateApp(id)

        val apps = launcherApps
            ?: return@withContext com.truevault.core.capabilities.model.CapabilityActionResult.Unsupported
        try {
            apps.startMainActivity(
                android.content.ComponentName(id.packageName, id.componentName),
                Process.myUserHandle(),
                null,
                null,
            )
            com.truevault.core.capabilities.model.CapabilityActionResult.Success
        } catch (e: Exception) {
            // The app can be uninstalled while this screen is open.
            com.truevault.core.capabilities.model.CapabilityActionResult.Unsupported
        }
    }

    /**
     * Emits whenever the installed set or a profile's availability changes.
     *
     * Package changes and profile locking both have to reach the grid without a restart, which is
     * why this is a callback flow rather than a one-shot read.
     */
    fun observeChanges(): Flow<Unit> = callbackFlow {
        val apps = launcherApps
        if (apps == null) {
            close()
            return@callbackFlow
        }

        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: android.os.UserHandle?) {
                trySend(Unit)
            }

            override fun onPackageChanged(packageName: String?, user: android.os.UserHandle?) {
                trySend(Unit)
            }

            override fun onPackageRemoved(packageName: String?, user: android.os.UserHandle?) {
                trySend(Unit)
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>?,
                user: android.os.UserHandle?,
                replacing: Boolean,
            ) {
                trySend(Unit)
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?,
                user: android.os.UserHandle?,
                replacing: Boolean,
            ) {
                // This is what fires when a private profile locks. The grid must drop those entries.
                trySend(Unit)
            }
        }

        runCatching { apps.registerCallback(callback) }
        awaitClose { runCatching { apps.unregisterCallback(callback) } }
    }
}
