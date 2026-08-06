package com.truevault.core.capabilities

import com.google.common.truth.Truth.assertThat
import com.truevault.core.capabilities.model.PrivateAppsSupport
import com.truevault.core.capabilities.model.PrivateSpaceState
import com.truevault.core.capabilities.model.TrueVaultProductMode
import org.junit.Test

/**
 * Version-mode and private-apps resolution.
 *
 * This is the one place in TrueVault where an SDK level decides anything, so it is worth pinning
 * exactly. Everything else about a device is observed at runtime, and the second half of this class
 * checks that the observation is never rounded up into a promise: an unknown or failed probe has to
 * come out as "not available", never as "probably fine".
 */
class CapabilityResolutionTest {

    @Test
    fun `every supported sdk level below 35 gets Core mode`() {
        // 26 is minSdk. Walking the whole range costs nothing here and covers the levels this
        // machine has no system image for.
        (26..34).forEach { sdk ->
            assertThat(productModeFor(sdk)).isEqualTo(TrueVaultProductMode.CORE)
        }
    }

    @Test
    fun `sdk 35 and above gets Modern mode`() {
        (35..40).forEach { sdk ->
            assertThat(productModeFor(sdk)).isEqualTo(TrueVaultProductMode.MODERN)
        }
    }

    @Test
    fun `an unconfigured private space is offered as guided setup rather than claimed as working`() {
        val support = privateAppsSupportFor(
            state = PrivateSpaceState.NotConfigured,
            isDefaultLauncher = false,
            oemAvailable = false,
        )

        assertThat(support).isEqualTo(PrivateAppsSupport.GUIDED_PRIVATE_SPACE_SETUP)
    }

    @Test
    fun `a locked private space is reported as locked, not as available`() {
        val support = privateAppsSupportFor(
            state = PrivateSpaceState.ConfiguredLocked,
            isDefaultLauncher = true,
            oemAvailable = false,
        )

        assertThat(support).isEqualTo(PrivateAppsSupport.PRIVATE_SPACE_LOCKED)
    }

    @Test
    fun `full launcher integration requires the Home role`() {
        val withRole = privateAppsSupportFor(
            state = PrivateSpaceState.ConfiguredUnlocked,
            isDefaultLauncher = true,
            oemAvailable = false,
        )
        val withoutRole = privateAppsSupportFor(
            state = PrivateSpaceState.ConfiguredUnlocked,
            isDefaultLauncher = false,
            oemAvailable = false,
        )

        assertThat(withRole).isEqualTo(PrivateAppsSupport.FULL_LAUNCHER_INTEGRATION)
        // Without the role the profile is still configured — TrueVault just cannot list its apps,
        // and says so instead of showing an empty grid.
        assertThat(withoutRole).isEqualTo(PrivateAppsSupport.PRIVATE_SPACE_ALREADY_CONFIGURED)
    }

    @Test
    fun `a device policy block is never softened into a setup prompt`() {
        val support = privateAppsSupportFor(
            state = PrivateSpaceState.RestrictedByPolicy,
            isDefaultLauncher = true,
            oemAvailable = true,
        )

        // Even with an OEM alternative present: policy said no, and offering a workaround to a
        // managed user would be the app arguing with their employer's admin.
        assertThat(support).isEqualTo(PrivateAppsSupport.DEVICE_POLICY_BLOCKED)
    }

    @Test
    fun `an unsupported platform falls back to the OEM route only when one actually resolves`() {
        val withOem = privateAppsSupportFor(
            state = PrivateSpaceState.Unsupported,
            isDefaultLauncher = false,
            oemAvailable = true,
        )
        val withoutOem = privateAppsSupportFor(
            state = PrivateSpaceState.Unsupported,
            isDefaultLauncher = false,
            oemAvailable = false,
        )

        assertThat(withOem).isEqualTo(PrivateAppsSupport.OEM_PRIVATE_SPACE_ONLY)
        assertThat(withoutOem).isEqualTo(PrivateAppsSupport.NOT_SUPPORTED)
    }

    @Test
    fun `a failed probe reports UNKNOWN and never a working capability`() {
        val support = privateAppsSupportFor(
            state = PrivateSpaceState.Error("profile query refused"),
            isDefaultLauncher = true,
            oemAvailable = true,
        )

        assertThat(support).isEqualTo(PrivateAppsSupport.UNKNOWN)
    }

    @Test
    fun `missing role and missing permission stay distinguishable`() {
        // They lead to different buttons — one opens the Home-app chooser, the other a permission
        // request — so collapsing them into one value would dead-end whichever user got the wrong
        // one.
        assertThat(
            privateAppsSupportFor(PrivateSpaceState.HomeRoleRequired, false, false),
        ).isEqualTo(PrivateAppsSupport.HOME_ROLE_REQUIRED)

        assertThat(
            privateAppsSupportFor(PrivateSpaceState.PermissionRequired, false, false),
        ).isEqualTo(PrivateAppsSupport.PERMISSION_REQUIRED)
    }
}
