package com.truevault.feature.importfiles.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.data.ActivityKind
import com.truevault.core.data.ActivityRepository
import com.truevault.core.data.ImportCoordinator
import com.truevault.core.data.ImportReviewBuilder
import com.truevault.core.data.ImportSessionStore
import com.truevault.core.data.ImportStep
import com.truevault.core.data.OriginalDeletionRequest
import com.truevault.core.data.SecureImportEngine
import com.truevault.core.data.model.ImportOutcome
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.core.model.DeletionOutcome
import com.truevault.core.model.ImportMode
import com.truevault.core.model.ImportModePreference
import com.truevault.core.model.MimeCategory
import com.truevault.core.data.PendingShareBuffer
import com.truevault.core.model.SelectedSource
import com.truevault.core.model.VaultError
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the whole import flow.
 *
 * The sequence it enforces is the product requirement, not an implementation detail: pick, review,
 * choose Copy or Move, encrypt and verify, and only then ask about the original. There is no path
 * through this class where a deletion is requested before a container has been verified and
 * committed.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val pendingShares: PendingShareBuffer,
    private val sessionStore: ImportSessionStore,
    private val coordinator: ImportCoordinator,
    private val reviewBuilder: ImportReviewBuilder,
    private val importEngine: SecureImportEngine,
    private val preferences: UserPreferencesDataSource,
    private val activityRepository: ActivityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ImportEffect>(
        replay = 0,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<ImportEffect> = _effects.asSharedFlow()

    private var importJob: Job? = null

    init {
        // Files shared into TrueVault from another app skip the picker entirely — the user already
        // chose them, in the gallery or wherever they came from, and asking again would be asking
        // the same question twice. They are consumed here so a second visit to this screen does not
        // re-import the same share.
        viewModelScope.launch {
            val shared = pendingShares.consume()
            if (shared.isNotEmpty()) {
                onSourcesPicked(shared, fromPhotoPicker = false)
            }
        }
    }

    fun onAction(action: ImportAction) {
        when (action) {
            is ImportAction.SourcesPicked -> onSourcesPicked(action.uriTokens, action.fromPhotoPicker)
            ImportAction.PickCancelled -> emit(ImportEffect.Close)
            ImportAction.ReviewConfirmed -> onReviewConfirmed()
            ImportAction.CancelImport -> cancelImport()
            ImportAction.Done -> {
                (uiState.value.stage as? ImportStage.Finished)?.result?.sessionId
                    ?.let(sessionStore::discard)
                emit(ImportEffect.Close)
            }

            ImportAction.ErrorDismissed -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun onSourcesPicked(uriTokens: List<String>, fromPhotoPicker: Boolean) {
        if (uriTokens.isEmpty()) {
            emit(ImportEffect.Close)
            return
        }

        _uiState.update { it.copy(isBusy = true, error = null) }

        viewModelScope.launch {
            val sources = coordinator.describeSources(uriTokens, fromPhotoPicker)

            if (sources.isEmpty()) {
                _uiState.update { it.copy(isBusy = false, error = VaultError.SourceNotFound) }
                return@launch
            }

            val session = sessionStore.create(sources)
            val review = reviewBuilder.build(sources)

            _uiState.update {
                it.copy(
                    isBusy = false,
                    dominantCategory = dominantCategory(sources),
                    stage = ImportStage.Reviewing(session.sessionId, review),
                )
            }
        }
    }

    private fun onReviewConfirmed() {
        val reviewing = uiState.value.stage as? ImportStage.Reviewing ?: return

        // Refuse before encryption begins. Starting and failing halfway would leave the user with a
        // partly full disk and nothing secured.
        if (!reviewing.review.hasEnoughSpace) {
            _uiState.update {
                it.copy(
                    error = VaultError.InsufficientStorage(
                        requiredBytes = reviewing.review.requiredBytes,
                        availableBytes = reviewing.review.availableBytes,
                    ),
                )
            }
            return
        }

        // One way in, and it never deletes anything.
        //
        // There used to be a choice here — Secure Copy or Secure Move — and Move asked Android to
        // delete the original afterwards. Two names for what a user thinks of as "add this file"
        // meant a question in the middle of the flow, and the answer they might pick in a hurry was
        // the one that destroys a file. The app now always copies: your original stays exactly
        // where it is, and nothing in this flow can remove it.
        startImport(reviewing.sessionId, ImportMode.SECURE_COPY)
    }

    private fun startImport(sessionId: String, mode: ImportMode) {
        val session = sessionStore.setMode(sessionId, mode) ?: return

        importJob?.cancel()
        importJob = viewModelScope.launch {
            importEngine.import(sessionId, session.sources, mode).collect { step ->
                when (step) {
                    is ImportStep.Progress ->
                        _uiState.update { it.copy(stage = ImportStage.Running(step.progress)) }

                    is ImportStep.Finished -> onImportFinished(step)
                }
            }
        }
    }

    private suspend fun onImportFinished(step: ImportStep.Finished) {
        // Every import is a copy, so there is never an original awaiting a decision. The whole
        // deletion conversation — plan it, show the system dialog, re-check the URIs, record what
        // actually happened — is gone with the feature that needed it.
        _uiState.update { it.copy(stage = ImportStage.Finished(step.result)) }
    }

    private fun cancelImport() {
        importJob?.cancel()
        importJob = null
        viewModelScope.launch { activityRepository.record(ActivityKind.IMPORT_FAILED, 0) }
        emit(ImportEffect.Close)
    }

    private fun emit(effect: ImportEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private fun dominantCategory(sources: List<SelectedSource>): MimeCategory =
        sources.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key
            ?: MimeCategory.OTHER

    override fun onCleared() {
        importJob?.cancel()
    }
}
