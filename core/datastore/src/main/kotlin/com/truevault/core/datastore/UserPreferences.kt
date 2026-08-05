package com.truevault.core.datastore

import com.truevault.core.model.AutoLockDuration
import com.truevault.core.model.ImportModePreference
import com.truevault.core.model.ThemePreference
import com.truevault.core.model.VaultLayout
import com.truevault.core.model.VaultSortOrder

/**
 * Non-sensitive, user-visible preferences.
 *
 * Nothing here is secret. Passwords, PINs, key material, recovery phrases and URI grants are never
 * written to DataStore — they live in the Android Keystore or in encrypted database columns.
 */
data class UserPreferences(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val useDynamicColor: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    val autoLockDuration: AutoLockDuration = AutoLockDuration.IMMEDIATE,
    val lockOnScreenOff: Boolean = true,
    val blockScreenshots: Boolean = true,
    val biometricUnlockEnabled: Boolean = false,
    val importModePreference: ImportModePreference = ImportModePreference.ALWAYS_ASK,
    val vaultLayout: VaultLayout = VaultLayout.GRID,
    val vaultSortOrder: VaultSortOrder = VaultSortOrder.DATE_ADDED_DESC,
    val recoveryKeyConfigured: Boolean = false,
    val lastBackupAtMillis: Long? = null,
)
