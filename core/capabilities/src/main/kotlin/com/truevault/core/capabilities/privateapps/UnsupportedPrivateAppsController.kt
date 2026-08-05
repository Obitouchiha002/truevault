package com.truevault.core.capabilities.privateapps

import com.truevault.core.capabilities.model.CapabilityActionResult
import com.truevault.core.capabilities.model.LauncherAppEntry
import com.truevault.core.capabilities.model.PrivateAppId
import com.truevault.core.capabilities.model.PrivateSpaceState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Android 8–14.
 *
 * Every operation reports `Unsupported`. That is the entire implementation, and it is deliberate:
 * there is no supported way to isolate an app on these versions, so there is nothing here to
 * approximate. The Private Apps screen shows the truth and points at the manufacturer's own
 * feature if one exists; the file vault is unaffected.
 */
class UnsupportedPrivateAppsController @Inject constructor() : PrivateAppsController {

    override fun observeState(): Flow<PrivateSpaceState> = flowOf(PrivateSpaceState.Unsupported)

    override suspend fun refresh() = Unit

    override suspend fun openSetup(): CapabilityActionResult = CapabilityActionResult.Unsupported

    override suspend fun requestLauncherRole(): CapabilityActionResult =
        CapabilityActionResult.Unsupported

    override suspend fun openPrivateApp(appId: PrivateAppId): CapabilityActionResult =
        CapabilityActionResult.Unsupported

    override suspend fun listApps(): List<LauncherAppEntry> = emptyList()

    override suspend fun isInstalledInPrivateProfile(packageName: String): Boolean = false
}
