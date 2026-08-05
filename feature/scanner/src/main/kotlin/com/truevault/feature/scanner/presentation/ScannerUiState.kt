package com.truevault.feature.scanner.presentation

import android.content.IntentSender
import androidx.compose.runtime.Immutable
import com.truevault.core.data.ScanFinding
import com.truevault.core.data.ScanReport
import com.truevault.core.model.DeletionOutcome

@Immutable
sealed interface ScanStage {
    data object Idle : ScanStage
    data class Enumerating(val filesFound: Int) : ScanStage
    data class Comparing(val checked: Int, val total: Int) : ScanStage
    data class Results(val report: ScanReport) : ScanStage
}

@Immutable
data class ScannerUiState(
    val stage: ScanStage = ScanStage.Idle,
    val lastDeletionOutcome: DeletionOutcome = DeletionOutcome.NOT_ATTEMPTED,
    val resolvedFindingIds: Set<String> = emptySet(),
    val awaitingConfirmation: Boolean = false,
) {
    val isRunning: Boolean
        get() = stage is ScanStage.Enumerating || stage is ScanStage.Comparing
}

sealed interface ScannerAction {
    data class ScopeChosen(val treeUriToken: String?) : ScannerAction
    data class RemoveMatchRequested(val finding: ScanFinding) : ScannerAction
    data class RemoveGroupRequested(val findings: List<ScanFinding>) : ScannerAction
    data class DeletionResultReceived(val approved: Boolean) : ScannerAction
    data class KeepMatch(val finding: ScanFinding) : ScannerAction
    data object Reset : ScannerAction
}

sealed interface ScannerEffect {
    data class RequestDeletion(val intentSender: IntentSender) : ScannerEffect
}
