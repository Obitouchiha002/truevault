package com.truevault.feature.legal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truevault.core.legal.LegalRepository
import com.truevault.core.model.LegalDocument
import com.truevault.core.model.LegalDocumentKind
import com.truevault.core.model.LegalSection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Text scale, applied on top of the system font scale rather than replacing it. */
private val TEXT_SCALES = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)
private const val DEFAULT_SCALE_INDEX = 1

data class LegalDocumentUiState(
    val isLoading: Boolean = true,
    val document: LegalDocument? = null,
    val query: String = "",
    val isSearching: Boolean = false,
    val textScaleIndex: Int = DEFAULT_SCALE_INDEX,
    val onlineUrl: String? = null,
    val failed: Boolean = false,
) {
    val textScale: Float get() = TEXT_SCALES[textScaleIndex.coerceIn(TEXT_SCALES.indices)]
    val canEnlarge: Boolean get() = textScaleIndex < TEXT_SCALES.lastIndex
    val canReduce: Boolean get() = textScaleIndex > 0
    val textScalePercent: Int get() = (textScale * 100).toInt()

    /**
     * Sections matching the query, or all of them when the query is blank.
     *
     * Filtering rather than jumping to a hit: in a document this long, showing only the parts that
     * mention "delete" is more useful than scrolling someone to the first of forty occurrences.
     */
    val visibleSections: List<LegalSection>
        get() {
            val doc = document ?: return emptyList()
            if (query.isBlank()) return doc.sections
            return doc.sections.filter { it.matches(query) }
        }

    val hasNoMatches: Boolean
        get() = query.isNotBlank() && visibleSections.isEmpty()
}

sealed interface LegalDocumentAction {
    data class QueryChanged(val query: String) : LegalDocumentAction
    data object SearchOpened : LegalDocumentAction
    data object SearchClosed : LegalDocumentAction
    data object TextLarger : LegalDocumentAction
    data object TextSmaller : LegalDocumentAction
}

@HiltViewModel
class LegalDocumentViewModel @Inject constructor(
    private val repository: LegalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LegalDocumentUiState())
    val uiState: StateFlow<LegalDocumentUiState> = _uiState.asStateFlow()

    private var loadedKind: LegalDocumentKind? = null

    fun load(kind: LegalDocumentKind) {
        if (loadedKind == kind) return
        loadedKind = kind

        viewModelScope.launch {
            runCatching { repository.document(kind) }
                .onSuccess { document ->
                    _uiState.update {
                        it.copy(isLoading = false, document = document, failed = false)
                    }
                }
                .onFailure {
                    // The bundled copy is the only guaranteed one. If it cannot be read the screen
                    // says so plainly rather than showing an empty page that looks like a document
                    // with nothing in it.
                    _uiState.update { it.copy(isLoading = false, failed = true) }
                }
        }
    }

    fun onAction(action: LegalDocumentAction) {
        when (action) {
            is LegalDocumentAction.QueryChanged ->
                _uiState.update { it.copy(query = action.query) }

            LegalDocumentAction.SearchOpened ->
                _uiState.update { it.copy(isSearching = true) }

            LegalDocumentAction.SearchClosed ->
                _uiState.update { it.copy(isSearching = false, query = "") }

            LegalDocumentAction.TextLarger -> _uiState.update {
                it.copy(textScaleIndex = (it.textScaleIndex + 1).coerceAtMost(TEXT_SCALES.lastIndex))
            }

            LegalDocumentAction.TextSmaller -> _uiState.update {
                it.copy(textScaleIndex = (it.textScaleIndex - 1).coerceAtLeast(0))
            }
        }
    }
}

private fun LegalSection.matches(query: String): Boolean {
    if (heading?.contains(query, ignoreCase = true) == true) return true
    return blocks.any { block -> block.containsText(query) }
}

private fun com.truevault.core.model.LegalBlock.containsText(query: String): Boolean = when (this) {
    is com.truevault.core.model.LegalBlock.Paragraph -> text.contains(query, ignoreCase = true)
    is com.truevault.core.model.LegalBlock.Bullets -> items.any { it.contains(query, true) }
    is com.truevault.core.model.LegalBlock.Numbered -> items.any { it.contains(query, true) }
    is com.truevault.core.model.LegalBlock.Quote -> text.contains(query, ignoreCase = true)
    is com.truevault.core.model.LegalBlock.Table ->
        header?.any { it.contains(query, true) } == true ||
            rows.any { row -> row.any { it.contains(query, true) } }
    com.truevault.core.model.LegalBlock.Divider -> false
}
