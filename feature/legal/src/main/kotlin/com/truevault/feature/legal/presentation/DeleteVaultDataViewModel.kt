package com.truevault.feature.legal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.legal.LegalRepository
import com.truevault.core.storage.VaultFileSystemReset
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Typed by hand, exactly. Anything a user could tap through by accident is not a confirmation. */
const val RESET_CONFIRMATION_PHRASE = "DELETE MY LOCAL VAULT"

data class DeleteVaultDataUiState(
    val vaultUsedBytes: Long = 0L,
    val lastBackupAtMillis: Long? = null,
    val typedPhrase: String = "",
    val isDeleting: Boolean = false,
    val finished: Boolean = false,
    val incomplete: Boolean = false,
) {
    val phraseMatches: Boolean get() = typedPhrase.trim() == RESET_CONFIRMATION_PHRASE
    val hasEverBackedUp: Boolean get() = lastBackupAtMillis != null
    val canDelete: Boolean get() = phraseMatches && !isDeleting
}

sealed interface DeleteVaultDataAction {
    data class PhraseChanged(val phrase: String) : DeleteVaultDataAction
    data object DeleteConfirmed : DeleteVaultDataAction
}

/**
 * Removing every trace of TrueVault from this device.
 *
 * What it removes: encrypted containers, thumbnails, temporary files, the database, preferences, the
 * locally wrapped keys and the acceptance record.
 *
 * What it must never touch, and cannot: **originals the user kept outside the vault**, files already
 * shared with other apps, and backups exported elsewhere. The reset only ever walks TrueVault's own
 * private directories, and the screen says so afterwards rather than implying a clean sweep it did
 * not perform.
 *
 * This is not account deletion. There is no account, and calling it that would promise something
 * that does not exist to delete.
 */
@HiltViewModel
class DeleteVaultDataViewModel @Inject constructor(
    private val fileSystemReset: VaultFileSystemReset,
    private val legalRepository: LegalRepository,
    private val preferences: com.truevault.core.datastore.UserPreferencesDataSource,
    private val fileSystem: com.truevault.core.storage.VaultFileSystem,
    private val lockStore: com.truevault.core.datastore.VaultLockDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeleteVaultDataUiState())
    val uiState: StateFlow<DeleteVaultDataUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val current = preferences.userPreferences.first()
            _uiState.update {
                it.copy(
                    vaultUsedBytes = fileSystem.totalVaultBytes(),
                    lastBackupAtMillis = current.lastBackupAtMillis,
                )
            }
        }
    }

    fun onAction(action: DeleteVaultDataAction) {
        when (action) {
            is DeleteVaultDataAction.PhraseChanged ->
                _uiState.update { it.copy(typedPhrase = action.phrase) }

            DeleteVaultDataAction.DeleteConfirmed -> delete()
        }
    }

    private fun delete() {
        if (!_uiState.value.canDelete) return
        _uiState.update { it.copy(isDeleting = true) }

        viewModelScope.launch {
            // Files first, then keys. In the other order a crash between the two would leave
            // containers on disk with no key that can ever open them — data that is neither usable
            // nor gone, occupying space the user thinks they reclaimed.
            val report = fileSystemReset.deleteEverything()

            runCatching { lockStore.clear() }
            runCatching { legalRepository.clearAcceptance() }

            _uiState.update {
                it.copy(
                    isDeleting = false,
                    finished = true,
                    incomplete = !report.isComplete,
                )
            }
        }
    }
}
