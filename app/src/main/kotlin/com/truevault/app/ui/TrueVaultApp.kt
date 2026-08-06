package com.truevault.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.truevault.app.R
import com.truevault.app.StartDestination
import com.truevault.app.AppShellViewModel
import com.truevault.app.navigation.TopLevelDestination
import com.truevault.app.navigation.TrueVaultNavHost
import com.truevault.core.crypto.session.VaultLockState
import com.truevault.feature.authentication.navigation.UnlockRoute
import com.truevault.core.designsystem.theme.TvMotion
import com.truevault.feature.home.navigation.navigateToHome
import com.truevault.feature.notes.navigation.NotesRoute
import com.truevault.feature.notes.navigation.navigateToNotes
import com.truevault.feature.importfiles.navigation.ImportSourceRoute
import com.truevault.feature.importfiles.navigation.navigateToImport
import com.truevault.feature.scanner.navigation.navigateToScanner
import com.truevault.feature.settings.navigation.navigateToSettings
import com.truevault.feature.vault.navigation.navigateToVault

/**
 * App shell: bottom navigation, the single "Add to Vault" action, and the navigation host.
 *
 * The bar and the action only appear on the four top-level destinations. Deeper screens — import,
 * viewer, backup — take the full window, because those flows need the user's whole attention.
 */
@Composable
fun TrueVaultApp(
    startDestination: StartDestination,
    lockState: VaultLockState,
    onExitApp: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier,
    shellViewModel: AppShellViewModel = hiltViewModel(),
) {
    val pendingShares = shellViewModel.hasPendingShare
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val currentTopLevel = remember(currentDestination) {
        TopLevelDestination.entries.firstOrNull { destination ->
            currentDestination?.hasRouteOf(destination) == true
        }
    }

    val isUnlocked = lockState == VaultLockState.Unlocked

    /**
     * While locked, the app is a notes app.
     *
     * The vault tabs and the "Add to Vault" button are not merely disabled — they are absent. A
     * greyed-out Vault tab would tell anyone holding the phone exactly what this app is, which is
     * the one thing the cover exists to prevent.
     */
    val visibleDestinations = remember(isUnlocked) {
        if (isUnlocked) {
            TopLevelDestination.entries.toList()
        } else {
            listOf(TopLevelDestination.NOTES, TopLevelDestination.SETTINGS)
        }
    }

    val showsChrome = currentTopLevel != null
    val showsVaultAction = showsChrome && isUnlocked

    // The vault can lock at any moment — the app going to the background, the screen turning off, a
    // grace period expiring. Whenever that happens the whole back stack is replaced by the unlock
    // screen, so no already-rendered vault content can be reached with the back gesture.
    LaunchedEffect(lockState) {
        // Auto-lock returns to Notes, not to an unlock screen. A phone that times out and then
        // displays "Enter your vault password" tells whoever is holding it that there is a vault —
        // which is the one thing the cover exists to avoid. The user reaches the vault again the
        // same way they did the first time.
        val onCoverAlready = navController.currentDestination?.hasRoute(NotesRoute::class) == true
        val onUnlockScreen = navController.currentDestination?.hasRoute(UnlockRoute::class) == true

        if (lockState == VaultLockState.Locked &&
            !onCoverAlready && !onUnlockScreen &&
            startDestination != StartDestination.ONBOARDING &&
            startDestination != StartDestination.CREATE_LOCK &&
            startDestination != StartDestination.LEGAL
        ) {
            navController.navigate(NotesRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // A share that arrived while the vault was locked waits in memory; the moment there is a key to
    // encrypt with, the import flow opens on its own. Requiring the user to unlock and then go find
    // "Add to Vault" would make the share sheet a slower route than not using it.
    val hasPendingShare by pendingShares.collectAsStateWithLifecycle()
    LaunchedEffect(hasPendingShare, lockState) {
        if (hasPendingShare &&
            lockState == VaultLockState.Unlocked &&
            navController.currentDestination?.hasRoute(ImportSourceRoute::class) != true
        ) {
            navController.navigateToImport()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            AnimatedVisibility(
                visible = showsChrome,
                enter = fadeIn(TvMotion.standardSpec()),
                exit = fadeOut(TvMotion.exitSpec()),
            ) {
                TrueVaultBottomBar(
                    destinations = visibleDestinations,
                    currentTopLevel = currentTopLevel,
                    onSelect = { destination ->
                        navController.navigateToTopLevel(destination)
                    },
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showsVaultAction,
                enter = scaleIn(TvMotion.standardSpec()) + fadeIn(TvMotion.standardSpec()),
                exit = scaleOut(TvMotion.exitSpec()) + fadeOut(TvMotion.exitSpec()),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigateToImport() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add_to_vault)) },
                )
            }
        },
    ) { innerPadding ->
        TrueVaultNavHost(
            navController = navController,
            startDestination = startDestination,
            onExitApp = onExitApp,
            onOpenUrl = onOpenUrl,
            onContactSupport = onContactSupport,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun TrueVaultBottomBar(
    destinations: List<TopLevelDestination>,
    currentTopLevel: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        destinations.forEach { destination ->
            val selected = destination == currentTopLevel
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = stringResource(destination.contentDescriptionRes),
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun NavDestination.hasRouteOf(destination: TopLevelDestination): Boolean =
    hierarchyContains(destination)

private fun NavDestination.hierarchyContains(destination: TopLevelDestination): Boolean =
    hierarchy.any { it.hasRoute(destination.route) }

private val NavDestination.hierarchy: Sequence<NavDestination>
    get() = generateSequence(this) { it.parent }

/**
 * Top-level navigation semantics: single-top, restore the destination's own back stack, and pop
 * back to the graph's start so the bar never builds an ever-growing stack.
 */
private fun androidx.navigation.NavHostController.navigateToTopLevel(
    destination: TopLevelDestination,
) {
    val options = androidx.navigation.navOptions {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    when (destination) {
        TopLevelDestination.NOTES -> navigateToNotes(options)
        TopLevelDestination.HOME -> navigateToHome(options)
        TopLevelDestination.VAULT -> navigateToVault(options)
        TopLevelDestination.SCAN -> navigateToScanner(options)
        TopLevelDestination.SETTINGS -> navigateToSettings(options)
    }
}
