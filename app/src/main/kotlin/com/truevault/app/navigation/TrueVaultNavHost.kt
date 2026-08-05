package com.truevault.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.navOptions
import com.truevault.app.StartDestination
import com.truevault.core.designsystem.theme.LocalReducedMotion
import com.truevault.core.designsystem.theme.TvMotion
import com.truevault.feature.authentication.navigation.CreateVaultLockRoute
import com.truevault.feature.authentication.navigation.UnlockRoute
import com.truevault.feature.authentication.navigation.authenticationScreens
import com.truevault.feature.authentication.navigation.navigateToSecuritySettings
import com.truevault.feature.backup.navigation.backupScreen
import com.truevault.feature.backup.navigation.navigateToBackup
import com.truevault.feature.home.navigation.HomeRoute
import com.truevault.feature.home.navigation.homeScreen
import com.truevault.feature.home.navigation.navigateToHome
import com.truevault.feature.importfiles.navigation.importScreens
import com.truevault.feature.importfiles.navigation.navigateToImport
import com.truevault.feature.onboarding.navigation.OnboardingRoute
import com.truevault.feature.onboarding.navigation.onboardingScreen
import com.truevault.feature.privateapps.navigation.navigateToPrivateApps
import com.truevault.feature.privateapps.navigation.privateAppsScreen
import com.truevault.feature.scanner.navigation.navigateToScanner
import com.truevault.feature.scanner.navigation.scannerScreen
import com.truevault.feature.settings.navigation.settingsScreen
import com.truevault.feature.vault.navigation.navigateToVault
import com.truevault.feature.vault.navigation.navigateToVaultItem
import com.truevault.feature.vault.navigation.vaultScreen

/**
 * The app's navigation graph.
 *
 * Transitions are a lateral slide plus a fade — enough to show direction, short enough not to slow
 * the app down. Under the system's reduced-motion setting every duration collapses to zero, which
 * turns the transitions into instant cuts rather than removing them case by case.
 *
 * Note how the authentication destinations are entered: whenever the app moves past onboarding, the
 * lock screens, or into the vault, the previous destination is popped inclusively. That is what
 * stops the back gesture from returning to an unlocked screen after the vault has locked.
 */
@Composable
fun TrueVaultNavHost(
    navController: NavHostController,
    startDestination: StartDestination,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val enterMillis = if (reducedMotion) 0 else TvMotion.DURATION_MEDIUM
    val exitMillis = if (reducedMotion) 0 else TvMotion.DURATION_SHORT
    val slideOffset = 48

    NavHost(
        navController = navController,
        startDestination = startDestination.toRoute(),
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(enterMillis, easing = TvMotion.Emphasized),
                initialOffsetX = { slideOffset },
            ) + fadeIn(tween(enterMillis))
        },
        exitTransition = {
            fadeOut(tween(exitMillis))
        },
        popEnterTransition = {
            fadeIn(tween(enterMillis))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(exitMillis, easing = TvMotion.Accelerate),
                targetOffsetX = { slideOffset },
            ) + fadeOut(tween(exitMillis))
        },
    ) {
        onboardingScreen(
            onFinished = {
                navController.navigate(
                    CreateVaultLockRoute,
                    navOptions { popUpTo(OnboardingRoute) { inclusive = true } },
                )
            },
        )

        authenticationScreens(
            onVaultCreated = {
                navController.navigateToHome(
                    navOptions { popUpTo(CreateVaultLockRoute) { inclusive = true } },
                )
            },
            onUnlocked = {
                navController.navigateToHome(
                    navOptions { popUpTo(UnlockRoute) { inclusive = true } },
                )
            },
            onNavigateBack = { navController.popBackStack() },
            // Locking from settings must not leave the settings screen underneath the lock screen.
            onLocked = {
                navController.navigate(
                    UnlockRoute,
                    navOptions { popUpTo(navController.graph.id) { inclusive = true } },
                )
            },
        )

        homeScreen(
            onAddFiles = { navController.navigateToImport() },
            onRunScan = { navController.navigateToScanner() },
            onOpenPrivateApps = { navController.navigateToPrivateApps() },
            onOpenBackup = { navController.navigateToBackup() },
            onOpenVault = { navController.navigateToVault() },
        )

        vaultScreen(
            onAddFiles = { navController.navigateToImport() },
            onOpenItem = { itemId -> navController.navigateToVaultItem(itemId) },
            onNavigateBack = { navController.popBackStack() },
        )

        scannerScreen()

        settingsScreen(
            onOpenSecuritySettings = { navController.navigateToSecuritySettings() },
            onOpenAboutSecurity = { /* Security explainer arrives with Phase 6 documentation. */ },
        )

        importScreens(
            onClose = { navController.popBackStack() },
        )

        privateAppsScreen(
            onNavigateBack = { navController.popBackStack() },
        )

        backupScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }
}

private fun StartDestination.toRoute(): Any = when (this) {
    StartDestination.ONBOARDING -> OnboardingRoute
    StartDestination.CREATE_LOCK -> CreateVaultLockRoute
    StartDestination.UNLOCK -> UnlockRoute
    StartDestination.HOME -> HomeRoute
}
