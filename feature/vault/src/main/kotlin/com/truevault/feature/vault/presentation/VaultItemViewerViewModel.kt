package com.truevault.feature.vault.presentation

import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.common.result.Outcome
import com.truevault.core.data.SecureExportCoordinator
import com.truevault.core.data.SecureShare
import com.truevault.core.data.SecureShareCoordinator
import com.truevault.core.data.VaultRepository
import com.truevault.core.data.model.VaultItem
import com.truevault.core.model.MimeCategory
import com.truevault.core.model.VaultError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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

/** What the viewer can actually render for this file. */
@Immutable
sealed interface ViewerContent {
    data class Image(val file: File) : ViewerContent
    data class Video(val file: File, val mimeType: String?, val fileName: String?) : ViewerContent
    data class Pdf(val file: File, val pageCount: Int) : ViewerContent
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
    /** True while a "save a copy" / "unhide" export is running, so the UI can show progress. */
    val isExporting: Boolean = false,
)

/** One-shot outcomes of a save/unhide, delivered to the screen for a toast and navigation. */
sealed interface ViewerEffect {
    /** The file cannot go straight to the gallery: ask the user where to create it. */
    data class PickSaveLocation(val suggestedName: String) : ViewerEffect

    /** A copy was written and the file is still in the vault. */
    data object Saved : ViewerEffect

    /** A copy was written and the file was removed from the vault (moved to the trash). */
    data object Unhidden : ViewerEffect

    /** The export failed; nothing left the vault. */
    data object ExportFailed : ViewerEffect
}

/** Text files are previewed up to this many characters; beyond that the viewer says it truncated. */
private const val TEXT_PREVIEW_LIMIT = 20_000

@HiltViewModel
class VaultItemViewerViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val shareCoordinator: SecureShareCoordinator,
    private val exportCoordinator: SecureExportCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultItemViewerUiState())
    val uiState: StateFlow<VaultItemViewerUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ViewerEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<ViewerEffect> = _effects.asSharedFlow()

    private var plaintextFile: File? = null
    private var activeShare: SecureShare? = null

    /** Set while waiting for the file-location picker, so its result knows whether to also remove. */
    private var pendingRemoveAfterSave: Boolean = false

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
        releaseShare()
        plaintextFile?.let(vaultRepository::discardPlaintext)
        plaintextFile = null
        _uiState.value = VaultItemViewerUiState()
    }

    override fun onCleared() {
        close()
    }

    private fun contentFor(item: VaultItem, file: File): ViewerContent = when {
        item.category == MimeCategory.PHOTO -> ViewerContent.Image(file)

        // Audio plays through the same ExoPlayer as video — it handles audio-only files fine and
        // PlayerView shows transport controls. Routing it here rather than to Unsupported means an
        // .mp3 or .m4a in the vault is playable, not just stored.
        item.category == MimeCategory.VIDEO || item.category == MimeCategory.AUDIO ->
            ViewerContent.Video(file, mimeType = item.mimeType, fileName = item.displayName)

        item.mimeType == "application/pdf" -> pdfOrUnsupported(file)

        item.mimeType?.startsWith("text/") == true -> {
            val bytes = file.readBytes()
            val text = String(bytes.copyOf(minOf(bytes.size, TEXT_PREVIEW_LIMIT)))
            ViewerContent.Text(preview = text, truncated = bytes.size > TEXT_PREVIEW_LIMIT)
        }

        // Everything else is stored and encrypted correctly but cannot be rendered here. Saying so
        // is better than an empty frame that leaves the user wondering whether the file survived.
        else -> ViewerContent.Unsupported
    }

    /**
     * Reads a PDF's page count without rendering anything yet.
     *
     * A password-protected or malformed PDF throws here; that is reported as "cannot preview"
     * rather than as a vault failure, because the stored bytes are fine either way.
     */
    private fun pdfOrUnsupported(file: File): ViewerContent = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                ViewerContent.Pdf(file = file, pageCount = renderer.pageCount)
            }
        }
    } catch (e: Exception) {
        ViewerContent.Unsupported
    }

    /** Prepares a one-shot share. The caller must call [releaseShare] when the sheet closes. */
    fun share(onReady: (SecureShare) -> Unit) {
        val id = _uiState.value.item?.id ?: return
        viewModelScope.launch {
            when (val outcome = shareCoordinator.prepare(id)) {
                is Outcome.Success -> {
                    activeShare = outcome.value
                    onReady(outcome.value)
                }

                is Outcome.Failure -> _uiState.update { it.copy(error = outcome.error) }
            }
        }
    }

    fun releaseShare() {
        activeShare?.let(shareCoordinator::release)
        activeShare = null
    }

    /**
     * "Save a copy" ([remove] = false) or "Unhide & remove" ([remove] = true).
     *
     * Photos and videos on API 29+ go straight to the gallery. Everything else needs a location, so
     * this emits [ViewerEffect.PickSaveLocation] and finishes in [onSaveLocationChosen].
     */
    fun save(remove: Boolean) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            if (exportCoordinator.canSaveToGallery(item)) {
                runExport(item, remove) { exportCoordinator.saveToGallery(item) }
            } else {
                pendingRemoveAfterSave = remove
                _effects.emit(ViewerEffect.PickSaveLocation(item.displayName))
            }
        }
    }

    /** Result of the file-location picker. A null [target] means the user cancelled. */
    fun onSaveLocationChosen(target: Uri?) {
        val item = _uiState.value.item ?: return
        val remove = pendingRemoveAfterSave
        pendingRemoveAfterSave = false
        if (target == null) return
        viewModelScope.launch {
            runExport(item, remove) { exportCoordinator.saveToDocument(item, target) }
        }
    }

    private suspend fun runExport(
        item: VaultItem,
        remove: Boolean,
        write: suspend () -> Outcome<Unit>,
    ) {
        _uiState.update { it.copy(isExporting = true) }
        val outcome = write()
        when (outcome) {
            is Outcome.Success -> {
                if (remove) {
                    // Move to the trash rather than erasing: the copy is now on the device, but a
                    // 30-day safety net still lets a mistaken unhide be undone.
                    vaultRepository.deleteItem(item.id)
                    _effects.emit(ViewerEffect.Unhidden)
                } else {
                    _uiState.update { it.copy(isExporting = false) }
                    _effects.emit(ViewerEffect.Saved)
                }
            }

            is Outcome.Failure -> {
                _uiState.update { it.copy(isExporting = false) }
                _effects.emit(ViewerEffect.ExportFailed)
            }
        }
    }
}
