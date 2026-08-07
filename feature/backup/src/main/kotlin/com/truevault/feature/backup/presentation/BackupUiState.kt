package com.truevault.feature.backup.presentation

import androidx.compose.runtime.Immutable
import com.truevault.core.crypto.backup.BackupManifest
import com.truevault.core.data.RestoreReport
import com.truevault.core.model.VaultError

@Immutable
sealed interface BackupStage {
    data object Overview : BackupStage
    /**
     * These two hold the recovery key — the whole of it, and one group of it — as `String`s, and a
     * data class prints every field. This stage also sits in a `StateFlow` for as long as the
     * screen is open, so its `toString` is reachable from a debugger frame, a log line, or any
     * future diagnostic that stringifies UI state. Redacted at the source rather than trusting
     * every future caller to remember.
     */
    data class RecoveryKeyShown(val key: String, val groups: List<String>) : BackupStage {
        override fun toString(): String = "RecoveryKeyShown(key=<redacted>, groups=${groups.size})"
    }

    data class RecoveryKeyConfirm(val groupIndex: Int, val expected: String) : BackupStage {
        override fun toString(): String =
            "RecoveryKeyConfirm(groupIndex=$groupIndex, expected=<redacted>)"
    }
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
    /** What the user typed while confirming a recovery-key group — the key itself, in pieces. */
    data class ConfirmRecoveryGroup(val entry: String) : BackupAction {
        override fun toString(): String = "ConfirmRecoveryGroup(entry=<redacted>)"
    }
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
