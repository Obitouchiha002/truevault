package com.truevault.feature.scanner.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.truevault.feature.scanner.presentation.ScannerScreen
import kotlinx.serialization.Serializable

@Serializable
data object ScannerRoute

/** Results of one scan run, addressed by the scan's identifier. */
@Serializable
data class ScannerResultRoute(val scanId: String)

fun NavController.navigateToScanner(navOptions: NavOptions? = null) = navigate(ScannerRoute, navOptions)

fun NavGraphBuilder.scannerScreen() {
    composable<ScannerRoute> {
        ScannerScreen()
    }
}
