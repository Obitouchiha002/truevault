package com.truevault.core.capabilities.privateapps

import com.truevault.core.capabilities.model.CapabilityActionResult
import com.truevault.core.capabilities.model.LauncherAppEntry
import com.truevault.core.capabilities.model.PrivateAppId
import com.truevault.core.capabilities.model.PrivateSpaceState
import kotlinx.coroutines.flow.Flow

/**
 * The one interface the UI talks to about private apps.
 *
 * Two implementations exist: one that reports "unsupported" for everything on Android 8–14, and one
 * guarded by `@RequiresApi(35)` for Android 15+. Dependency injection picks the right one at
 * runtime, so the API 35 class is never even loaded on an older device.
 */
interface PrivateAppsController {

    fun observeState(): Flow<PrivateSpaceState>

    suspend fun refresh()

    /** Opens the platform's own Private Space setup. TrueVault never creates a profile itself. */
    suspend fun openSetup(): CapabilityActionResult

    suspend fun requestLauncherRole(): CapabilityActionResult

    suspend fun openPrivateApp(appId: PrivateAppId): CapabilityActionResult

    /**
     * Apps visible in the profiles TrueVault is allowed to see.
     *
     * Returns empty whenever the private profile is locked — a locked profile's app names must not
     * reach the UI, a cache, or a log.
     */
    suspend fun listApps(): List<LauncherAppEntry>

    /**
     * Whether a private copy of [packageName] has been observed through supported APIs.
     *
     * This is what gates "Remove main copy". It returns false when TrueVault cannot see the profile,
     * and the UI then requires an explicit manual confirmation instead of claiming a verification
     * that never happened.
     */
    suspend fun isInstalledInPrivateProfile(packageName: String): Boolean
}
