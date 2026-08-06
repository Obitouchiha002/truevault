package com.truevault.feature.legal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.legal.LegalRepository
import com.truevault.core.model.LegalAcceptanceStatus
import com.truevault.core.model.LegalDocumentVersions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LegalGateUiState(
    val isLoading: Boolean = true,
    /** Both start **off**. Nothing about this screen may pre-tick them. */
    val termsAgreed: Boolean = false,
    val privacyAcknowledged: Boolean = false,
    val versions: LegalDocumentVersions? = null,
    val status: LegalAcceptanceStatus = LegalAcceptanceStatus.Missing,
    val showingDeclineConfirmation: Boolean = false,
    val unresolvedPlaceholders: List<String> = emptyList(),
    val isRecording: Boolean = false,
    val failed: Boolean = false,
) {
    /**
     * The only condition under which the primary button becomes usable.
     *
     * Both boxes, ticked by hand. Not scrolling to the bottom, not a timer, not one box that
     * silently covers both documents — each of those is a way of collecting a signature the user did
     * not knowingly give.
     */
    val canContinue: Boolean get() = termsAgreed && privacyAcknowledged && !isRecording

    val isReacceptance: Boolean get() = status is LegalAcceptanceStatus.ReacceptanceRequired

    val previousTermsVersion: String?
        get() = (status as? LegalAcceptanceStatus.ReacceptanceRequired)?.record?.termsVersion

    val previousPrivacyVersion: String?
        get() = (status as? LegalAcceptanceStatus.ReacceptanceRequired)?.record?.privacyPolicyVersion
}

sealed interface LegalGateAction {
    data class TermsToggled(val agreed: Boolean) : LegalGateAction
    data class PrivacyToggled(val acknowledged: Boolean) : LegalGateAction
    data object AcceptClicked : LegalGateAction
    data object DeclineClicked : LegalGateAction
    data object DeclineDismissed : LegalGateAction
    data object DeclineConfirmed : LegalGateAction
}

sealed interface LegalGateEffect {
    /** Acceptance recorded. The app may now start onboarding. */
    data object Accepted : LegalGateEffect

    /** The user declined. Nothing was created, and the app closes. */
    data object Exit : LegalGateEffect
}

/**
 * The gate in front of everything else.
 *
 * Nothing downstream runs until this reports [LegalGateEffect.Accepted]: no onboarding, no
 * permission request, no vault creation, no file access, and no optional SDK initialisation — the
 * last of which is trivially true today, because the app contains none.
 */
@HiltViewModel
class LegalGateViewModel @Inject constructor(
    private val repository: LegalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LegalGateUiState())
    val uiState: StateFlow<LegalGateUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<LegalGateEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<LegalGateEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val versions = repository.versions()
                val status = repository.currentStatus()
                val placeholders = repository.placeholders()
                Triple(versions, status, placeholders)
            }.onSuccess { (versions, status, placeholders) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        versions = versions,
                        status = status,
                        unresolvedPlaceholders = placeholders.placeholders,
                    )
                }
            }.onFailure {
                // The documents are bundled, so this should be impossible. If it happens, the gate
                // stays closed: shipping past a legal screen that could not load its own documents
                // would be recording consent to nothing.
                _uiState.update { it.copy(isLoading = false, failed = true) }
            }
        }
    }

    fun onAction(action: LegalGateAction) {
        when (action) {
            is LegalGateAction.TermsToggled ->
                _uiState.update { it.copy(termsAgreed = action.agreed) }

            is LegalGateAction.PrivacyToggled ->
                _uiState.update { it.copy(privacyAcknowledged = action.acknowledged) }

            LegalGateAction.AcceptClicked -> accept()

            LegalGateAction.DeclineClicked ->
                _uiState.update { it.copy(showingDeclineConfirmation = true) }

            LegalGateAction.DeclineDismissed ->
                _uiState.update { it.copy(showingDeclineConfirmation = false) }

            LegalGateAction.DeclineConfirmed -> viewModelScope.launch {
                // Nothing to undo: no vault was created, no permission requested, no file read.
                _effects.emit(LegalGateEffect.Exit)
            }
        }
    }

    private fun accept() {
        val state = _uiState.value
        if (!state.canContinue) return

        _uiState.update { it.copy(isRecording = true) }

        viewModelScope.launch {
            runCatching { repository.recordAcceptance() }
                .onSuccess {
                    _uiState.update { it.copy(isRecording = false) }
                    _effects.emit(LegalGateEffect.Accepted)
                }
                .onFailure {
                    // Storage refused the write. Proceeding anyway would leave the app believing the
                    // user accepted while nothing on the device records it.
                    _uiState.update { it.copy(isRecording = false, failed = true) }
                }
        }
    }
}
