package com.truevault.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import com.truevault.app.R
import com.truevault.feature.home.navigation.HomeRoute
import com.truevault.feature.notes.navigation.NotesRoute
import com.truevault.feature.scanner.navigation.ScannerRoute
import com.truevault.feature.settings.navigation.SettingsRoute
import com.truevault.feature.vault.navigation.VaultRoute
import kotlin.reflect.KClass

/**
 * The four destinations reachable from the bottom bar.
 *
 * Each carries its own route type so selection is decided by comparing the current back-stack
 * entry's destination against [route], rather than by string matching.
 */
enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val labelRes: Int,
    val contentDescriptionRes: Int,
    val route: KClass<*>,
) {
    /**
     * Notes come first because the app opens here.
     *
     * That is the point of the cover: what is on screen when someone hands the phone over is a
     * notes app, and the vault is one tap away behind authentication rather than the first thing
     * anyone sees.
     */
    NOTES(
        selectedIcon = Icons.AutoMirrored.Filled.EventNote,
        unselectedIcon = Icons.AutoMirrored.Outlined.EventNote,
        labelRes = R.string.nav_notes,
        contentDescriptionRes = R.string.nav_notes_description,
        route = NotesRoute::class,
    ),
    HOME(
        selectedIcon = Icons.Filled.Shield,
        unselectedIcon = Icons.Outlined.Shield,
        labelRes = R.string.nav_home,
        contentDescriptionRes = R.string.nav_home_description,
        route = HomeRoute::class,
    ),
    VAULT(
        selectedIcon = Icons.Filled.Lock,
        unselectedIcon = Icons.Outlined.Lock,
        labelRes = R.string.nav_vault,
        contentDescriptionRes = R.string.nav_vault_description,
        route = VaultRoute::class,
    ),
    SCAN(
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar,
        labelRes = R.string.nav_scan,
        contentDescriptionRes = R.string.nav_scan_description,
        route = ScannerRoute::class,
    ),
    SETTINGS(
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        labelRes = R.string.nav_settings,
        contentDescriptionRes = R.string.nav_settings_description,
        route = SettingsRoute::class,
    ),
}
