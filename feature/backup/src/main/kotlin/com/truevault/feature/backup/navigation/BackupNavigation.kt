package com.truevault.feature.backup.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.truevault.feature.backup.presentation.BackupScreen
import kotlinx.serialization.Serializable

@Serializable
data object BackupRoute

@Serializable
data object RecoveryRoute

fun NavController.navigateToBackup(navOptions: NavOptions? = null) = navigate(BackupRoute, navOptions)

fun NavGraphBuilder.backupScreen(onNavigateBack: () -> Unit) {
    composable<BackupRoute> {
        BackupScreen(onNavigateBack = onNavigateBack)
    }
}
