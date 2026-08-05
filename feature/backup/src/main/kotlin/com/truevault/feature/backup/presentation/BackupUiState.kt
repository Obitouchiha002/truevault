package com.truevault.feature.backup.presentation

import androidx.compose.runtime.Immutable
import com.truevault.core.crypto.backup.BackupManifest
import com.truevault.core.data.RestoreReport
import com.truevault.core.model.VaultError

@Immutable
sealed interface BackupStage {
    data object Overview : BackupStage
    data class RecoveryKeyShown(val key: String, val groups: List<String>) : BackupStage
    data class RecoveryKeyConfirm(val groupIndex: Int, val expected: String) : BackupStage
    data class Exporting(val completed: Int, val total: Int) : BackupStage
    data class ExportFinished(val itemCount: Int) : BackupStage
    data class RestorePreview(val manifest: BackupManifest, val sourceToken: String) : BackupStage
    data class Restoring(val completed: Int, val total: Int) : BackupStage
    data class RestoreFinished(val report: RestoreReport) : BackupStage
}

@Immutable
data class BackupUiState(
    val stage: BackupStage = BackupStage.Overview,
    val recoveryKeyConfigured: Boolean = false,
    val lastBackupAtMillis: Long? = null,
    val vaultItemCount: Int = 0,
    val error: VaultError? = null,
    val errorDetail: String? = null,
    val isBusy: Boolean = false,
)

sealed interface BackupAction {
    data object GenerateRecoveryKey : BackupAction
    data class ConfirmRecoveryGroup(val entry: String) : BackupAction
    data object RecoveryKeyAcknowledged : BackupAction
    data class ExportDestinationChosen(val uriToken: String?, val passphrase: CharArray) : BackupAction {
        override fun equals(other: Any?): Boolean = other is ExportDestinationChosen &&
            uriToken == other.uriToken && passphrase.contentEquals(other.passphrase)

        override fun hashCode(): Int = 31 * (uriToken?.hashCode() ?: 0) + passphrase.contentHashCode()
    }

    data class RestoreSourceChosen(val uriToken: String?) : BackupAction
    data class RestoreConfirmed(val passphrase: CharArray) : BackupAction {
        override fun equals(other: Any?): Boolean =
            other is RestoreConfirmed && passphrase.contentEquals(other.passphrase)

        override fun hashCode(): Int = passphrase.contentHashCode()
    }

    data object Dismiss : BackupAction
}

sealed interface BackupEffect {
    /** Ask the UI to open the system file creator with this suggested name. */
    data class CreateArchive(val suggestedName: String) : BackupEffect
    data object OpenArchive : BackupEffect
}
