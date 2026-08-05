package com.truevault.feature.scanner.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.data.ImportCoordinator
import com.truevault.core.data.OriginalDeletionRequest
import com.truevault.core.data.PrivacyScanEngine
import com.truevault.core.data.ScanFinding
import com.truevault.core.data.ScanStep
import com.truevault.core.model.DeletionOutcome
import com.truevault.core.model.ScanMatchType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The scan screen.
 *
 * Nothing found by a scan is ever removed automatically. Every deletion goes through the platform's
 * own confirmation, and the outcome recorded is the one that actually happened.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val scanEngine: PrivacyScanEngine,
    private val coordinator: ImportCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ScannerEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<ScannerEffect> = _effects.asSharedFlow()

    private var scanJob: Job? = null
    private var pendingFindings: List<ScanFinding> = emptyList()

    fun onAction(action: ScannerAction) {
        when (action) {
            is ScannerAction.ScopeChosen -> action.treeUriToken?.let(::startScan)
            is ScannerAction.RemoveMatchRequested -> requestRemoval(listOf(action.finding))
            is ScannerAction.RemoveGroupRequested -> requestRemoval(action.findings)
            is ScannerAction.DeletionResultReceived -> onDeletionResult(action.approved)
            is ScannerAction.KeepMatch -> keep(action.finding)
            ScannerAction.Reset -> _uiState.value = ScannerUiState()
        }
    }

    private fun startScan(treeUriToken: String) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            scanEngine.scan(treeUriToken).collect { step ->
                _uiState.update { state ->
                    state.copy(
                        stage = when (step) {
                            is ScanStep.Enumerating -> ScanStage.Enumerating(step.filesFound)
                            is ScanStep.Comparing -> ScanStage.Comparing(step.checked, step.total)
                            is ScanStep.Finished -> ScanStage.Results(step.report)
                        },
                    )
                }
            }
        }
    }

    private fun requestRemoval(findings: List<ScanFinding>) {
        if (findings.isEmpty()) return
        pendingFindings = findings

        viewModelScope.launch {
            when (val request = coordinator.planOriginalDeletion(findings.map { it.matchedUriToken })) {
                is OriginalDeletionRequest.NeedsUserConfirmation -> {
                    _uiState.update { it.copy(awaitingConfirmation = true) }
                    _effects.emit(ScannerEffect.RequestDeletion(request.intentSender))
                }

                is OriginalDeletionRequest.Resolved -> applyOutcome(request.outcome)
            }
        }
    }

    private fun onDeletionResult(approved: Boolean) {
        viewModelScope.launch {
            val outcome = coordinator.confirmDeletion(pendingFindings.map { it.matchedUriToken }, approved)
            applyOutcome(outcome)
        }
    }

    private suspend fun applyOutcome(outcome: DeletionOutcome) {
        val removed = outcome == DeletionOutcome.DELETED || outcome == DeletionOutcome.ALREADY_MISSING

        if (removed) {
            pendingFindings.forEach { finding ->
                scanEngine.markResolved(finding.id)
                // Only a confirmed removal of the original promotes an item to SECURED.
                if (finding.matchType == ScanMatchType.ORIGINAL_REMAINS) {
                    scanEngine.onOriginalRemoved(finding.vaultItemId)
                }
            }
        }

        _uiState.update { state ->
            state.copy(
                lastDeletionOutcome = outcome,
                awaitingConfirmation = false,
                resolvedFindingIds = if (removed) {
                    state.resolvedFindingIds + pendingFindings.map { it.id }
                } else {
                    state.resolvedFindingIds
                },
            )
        }
        pendingFindings = emptyList()
    }

    private fun keep(finding: ScanFinding) {
        viewModelScope.launch {
            scanEngine.markResolved(finding.id)
            _uiState.update { it.copy(resolvedFindingIds = it.resolvedFindingIds + finding.id) }
        }
    }
}
