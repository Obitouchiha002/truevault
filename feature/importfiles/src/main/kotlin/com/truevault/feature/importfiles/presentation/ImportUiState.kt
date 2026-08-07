package com.truevault.feature.importfiles.presentation

import androidx.compose.runtime.Immutable
import com.truevault.core.data.model.ImportProgress
import com.truevault.core.data.model.ImportResult
import com.truevault.core.data.model.ImportReview
import com.truevault.core.model.DeletionOutcome
import com.truevault.core.model.ImportMode
import com.truevault.core.model.ImportModePreference
import com.truevault.core.model.MimeCategory
import com.truevault.core.model.VaultError

/** Where the import flow currently is. One state machine, one screen per state. */
@Immutable
sealed interface ImportStage {

    /** Nothing picked yet. */
    data object ChoosingSource : ImportStage

    data class Reviewing(val sessionId: String, val review: ImportReview) : ImportStage

    data class Running(val progress: ImportProgress) : ImportStage

    data class Finished(val result: ImportResult) : ImportStage
}

@Immutable
data class ImportUiState(
    val stage: ImportStage = ImportStage.ChoosingSource,
    val isBusy: Boolean = false,
    val error: VaultError? = null,
    val dominantCategory: MimeCategory = MimeCategory.OTHER,
)

sealed interface ImportAction {
    data class SourcesPicked(val uriTokens: List<String>, val fromPhotoPicker: Boolean) : ImportAction
    data object PickCancelled : ImportAction
    data object ReviewConfirmed : ImportAction
    data object CancelImport : ImportAction
    data object Done : ImportAction
    data object ErrorDismissed : ImportAction
}

sealed interface ImportEffect {
    data object Close : ImportEffect
}
