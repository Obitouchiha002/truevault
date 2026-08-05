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
import com.truevault.core.designsystem.theme.LocalReducedMotion
import com.truevault.core.designsystem.theme.TvMotion
import com.truevault.feature.authentication.navigation.authenticationScreens
import com.truevault.feature.backup.navigation.backupScreen
import com.truevault.feature.backup.navigation.navigateToBackup
import com.truevault.feature.home.navigation.HomeRoute
import com.truevault.feature.home.navigation.homeScreen
import com.truevault.feature.home.navigation.navigateToHome
import com.truevault.feature.importfiles.navigation.importScreens
import com.truevault.feature.importfiles.navigation.navigateToImport
import com.truevault.feature.onboarding.navigation.onboardingScreen
import com.truevault.feature.privateapps.navigation.navigateToPrivateApps
import com.truevault.feature.privateapps.navigation.privateAppsScreen
import com.truevault.feature.scanner.navigation.navigateToScanner
import com.truevault.feature.scanner.navigation.scannerScreen
import com.truevault.feature.settings.navigation.settingsScreen
import com.truevault.feature.vault.navigation.navigateToVault
import com.truevault.feature.vault.navigation.vaultScreen

/**
 * The app's navigation graph.
 *
 * Transitions are a lateral slide plus a fade — enough to show direction, short enough not to slow
 * the app down. Under the system's reduced-motion setting every duration collapses to zero, which
 * turns the transitions into instant cuts rather than removing them case by case.
 */
@Composable
fun TrueVaultNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val enterMillis = if (reducedMotion) 0 else TvMotion.DURATION_MEDIUM
    val exitMillis = if (reducedMotion) 0 else TvMotion.DURATION_SHORT
    val slideOffset = 48

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
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
            onFinished = { navController.navigateToHome() },
        )

        authenticationScreens(
            onVaultCreated = { navController.navigateToHome() },
            onUnlocked = { navController.navigateToHome() },
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
        )

        scannerScreen()

        settingsScreen(
            onOpenSecuritySettings = { /* Security settings screen arrives with Phase 1. */ },
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
