package com.truevault.feature.backup.presentation

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.common.result.Outcome
import com.truevault.core.crypto.kdf.wipe
import com.truevault.core.crypto.recovery.RecoveryKey
import com.truevault.core.crypto.vault.VaultKeyManager
import com.truevault.core.data.BackupRepository
import com.truevault.core.data.BackupStep
import com.truevault.core.data.VaultRepository
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.core.model.VaultError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backup, restore and the recovery key.
 *
 * The recovery key is shown exactly once and confirmed by asking the user to type one group back.
 * That confirmation is not ceremony: a user who has not actually written it down will fail it, which
 * is the last moment anyone can tell them before it matters.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val keyManager: VaultKeyManager,
    private val backupRepository: BackupRepository,
    private val vaultRepository: VaultRepository,
    private val preferences: UserPreferencesDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BackupEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<BackupEffect> = _effects.asSharedFlow()

    private var generatedKey: CharArray? = null

    init {
        preferences.userPreferences
            .onEach { prefs ->
                _uiState.update {
                    it.copy(
                        recoveryKeyConfigured = prefs.recoveryKeyConfigured,
                        lastBackupAtMillis = prefs.lastBackupAtMillis,
                        cloudFolderUri = prefs.cloudBackupFolderUri,
                    )
                }
            }
            .launchIn(viewModelScope)

        vaultRepository.observeItemCount()
            .onEach { count -> _uiState.update { it.copy(vaultItemCount = count) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: BackupAction) {
        when (action) {
            BackupAction.GenerateRecoveryKey -> generateRecoveryKey()
            is BackupAction.ConfirmRecoveryGroup -> confirmGroup(action.entry)
            BackupAction.RecoveryKeyAcknowledged -> finishRecoveryFlow()
            is BackupAction.ExportDestinationChosen -> export(action.uriToken, action.passphrase)
            is BackupAction.RestoreSourceChosen -> inspect(action.uriToken)
            is BackupAction.RestoreConfirmed -> restore(action.passphrase)
            BackupAction.Dismiss -> _uiState.update {
                it.copy(stage = BackupStage.Overview, error = null, errorDetail = null)
            }
        }
    }

    fun requestExport() {
        viewModelScope.launch { _effects.emit(BackupEffect.CreateArchive(suggestedFileName())) }
    }

    fun requestRestore() {
        viewModelScope.launch { _effects.emit(BackupEffect.OpenArchive) }
    }

    /** Opens the folder picker so the user can choose (or change) their cloud backup folder. */
    fun requestConnectCloudFolder() {
        viewModelScope.launch { _effects.emit(BackupEffect.PickCloudFolder) }
    }

    /** Remembers the folder the user picked. The screen has already taken the persistable grant. */
    fun onCloudFolderChosen(treeUriToken: String?) {
        if (treeUriToken == null) return
        viewModelScope.launch { preferences.setCloudBackupFolder(treeUriToken) }
    }

    /** Writes a fresh encrypted archive into the connected cloud folder. */
    fun backupToCloud(passphrase: CharArray) {
        val folder = _uiState.value.cloudFolderUri
        if (folder == null) {
            passphrase.wipe()
            viewModelScope.launch { _effects.emit(BackupEffect.PickCloudFolder) }
            return
        }
        viewModelScope.launch {
            try {
                backupRepository.backupToFolder(folder.toUri(), passphrase).collect { step ->
                    _uiState.update { state -> state.applyBackupStep(step) }
                }
            } finally {
                passphrase.wipe()
            }
        }
    }

    private fun generateRecoveryKey() {
        _uiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val outcome = keyManager.generateRecoveryKey()) {
                is Outcome.Success -> {
                    generatedKey = outcome.value
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            stage = BackupStage.RecoveryKeyShown(
                                key = RecoveryKey.format(outcome.value),
                                groups = RecoveryKey.groups(outcome.value),
                            ),
                        )
                    }
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isBusy = false, error = outcome.error)
                }
            }
        }
    }

    /** Moves from "here is your key" to "type group N back to me". */
    fun startConfirmation() {
        val key = generatedKey ?: return
        val groups = RecoveryKey.groups(key)
        // A fixed group would be learnable; picking from the key's own bytes keeps it unpredictable
        // without needing a random source at this point in the flow.
        val index = (key.first().code + key.last().code) % groups.size
        _uiState.update {
            it.copy(stage = BackupStage.RecoveryKeyConfirm(index, groups[index]))
        }
    }

    private fun confirmGroup(entry: String) {
        val stage = _uiState.value.stage as? BackupStage.RecoveryKeyConfirm ?: return
        if (entry.trim().uppercase() == stage.expected) {
            viewModelScope.launch {
                preferences.setRecoveryKeyConfigured(true)
                finishRecoveryFlow()
            }
        } else {
            _uiState.update {
                it.copy(error = VaultError.Unknown("That group did not match. Check your copy."))
            }
        }
    }

    private fun finishRecoveryFlow() {
        generatedKey?.wipe()
        generatedKey = null
        _uiState.update { it.copy(stage = BackupStage.Overview, error = null) }
    }

    private fun export(uriToken: String?, passphrase: CharArray) {
        if (uriToken == null) {
            passphrase.wipe()
            return
        }

        viewModelScope.launch {
            try {
                backupRepository.export(uriToken.toUri(), passphrase).collect { step ->
                    _uiState.update { state -> state.applyBackupStep(step) }
                }
            } finally {
                passphrase.wipe()
            }
        }
    }

    /** Shared export/cloud-backup step handling, so both paths report progress identically. */
    private fun BackupUiState.applyBackupStep(step: BackupStep): BackupUiState = when (step) {
        is BackupStep.Progress -> copy(stage = BackupStage.Exporting(step.completed, step.total))
        is BackupStep.ExportFinished -> copy(stage = BackupStage.ExportFinished(step.itemCount))
        is BackupStep.Failed ->
            copy(stage = BackupStage.Overview, error = step.error, errorDetail = step.detail)
        is BackupStep.RestoreFinished -> this
    }

    private fun inspect(uriToken: String?) {
        if (uriToken == null) return
        _uiState.update { it.copy(isBusy = true, error = null) }

        viewModelScope.launch {
            when (val outcome = backupRepository.inspect(uriToken.toUri())) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        isBusy = false,
                        stage = BackupStage.RestorePreview(outcome.value, uriToken),
                    )
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isBusy = false, error = outcome.error)
                }
            }
        }
    }

    private fun restore(passphrase: CharArray) {
        val stage = _uiState.value.stage as? BackupStage.RestorePreview ?: run {
            passphrase.wipe()
            return
        }

        viewModelScope.launch {
            try {
                backupRepository.restore(stage.sourceToken.toUri(), passphrase).collect { step ->
                    _uiState.update { state ->
                        when (step) {
                            is BackupStep.Progress ->
                                state.copy(stage = BackupStage.Restoring(step.completed, step.total))

                            is BackupStep.RestoreFinished ->
                                state.copy(stage = BackupStage.RestoreFinished(step.report))

                            is BackupStep.Failed ->
                                state.copy(
                                    stage = BackupStage.Overview,
                                    error = step.error,
                                    errorDetail = step.detail,
                                )

                            is BackupStep.ExportFinished -> state
                        }
                    }
                }
            } finally {
                passphrase.wipe()
            }
        }
    }

    private suspend fun suggestedFileName(): String {
        val count = vaultRepository.observeItemCount().first()
        return "truevault-$count-items.tvbackup"
    }

    override fun onCleared() {
        generatedKey?.wipe()
        generatedKey = null
    }
}
