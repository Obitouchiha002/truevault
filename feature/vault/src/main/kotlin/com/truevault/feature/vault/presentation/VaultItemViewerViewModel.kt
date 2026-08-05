package com.truevault.feature.vault.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.common.result.Outcome
import com.truevault.core.data.VaultRepository
import com.truevault.core.data.model.VaultItem
import com.truevault.core.model.MimeCategory
import com.truevault.core.model.VaultError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the viewer can actually render for this file. */
@Immutable
sealed interface ViewerContent {
    data class Image(val file: File) : ViewerContent
    data class Text(val preview: String, val truncated: Boolean) : ViewerContent

    /** A secured file this build cannot preview. Stated, never faked with a blank frame. */
    data object Unsupported : ViewerContent
}

@Immutable
data class VaultItemViewerUiState(
    val isLoading: Boolean = true,
    val item: VaultItem? = null,
    val content: ViewerContent? = null,
    val error: VaultError? = null,
)

/** Text files are previewed up to this many characters; beyond that the viewer says it truncated. */
private const val TEXT_PREVIEW_LIMIT = 20_000

@HiltViewModel
class VaultItemViewerViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultItemViewerUiState())
    val uiState: StateFlow<VaultItemViewerUiState> = _uiState.asStateFlow()

    private var plaintextFile: File? = null

    fun open(vaultItemId: String) {
        viewModelScope.launch {
            val item = vaultRepository.findItem(vaultItemId)
            if (item == null) {
                _uiState.value = VaultItemViewerUiState(
                    isLoading = false,
                    error = VaultError.SourceNotFound,
                )
                return@launch
            }

            _uiState.update { it.copy(item = item) }

            when (val outcome = vaultRepository.materialiseForViewing(vaultItemId)) {
                is Outcome.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = outcome.error)
                }

                is Outcome.Success -> {
                    plaintextFile = outcome.value
                    _uiState.update {
                        it.copy(isLoading = false, content = contentFor(item, outcome.value))
                    }
                }
            }
        }
    }

    /**
     * Removes the temporary plaintext.
     *
     * Called from `onDispose`, so leaving the screen by any route — back, a lock, process death
     * followed by startup recovery — ends with the plaintext gone.
     */
    fun close() {
        plaintextFile?.let(vaultRepository::discardPlaintext)
        plaintextFile = null
        _uiState.value = VaultItemViewerUiState()
    }

    override fun onCleared() {
        close()
    }

    private fun contentFor(item: VaultItem, file: File): ViewerContent = when {
        item.category == MimeCategory.PHOTO -> ViewerContent.Image(file)

        item.mimeType?.startsWith("text/") == true -> {
            val bytes = file.readBytes()
            val text = String(bytes.copyOf(minOf(bytes.size, TEXT_PREVIEW_LIMIT)))
            ViewerContent.Text(preview = text, truncated = bytes.size > TEXT_PREVIEW_LIMIT)
        }

        // Video and PDF rendering are not in this build. Saying so is better than an empty frame
        // that leaves the user wondering whether their file survived.
        else -> ViewerContent.Unsupported
    }
}
