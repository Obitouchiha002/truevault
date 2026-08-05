package com.truevault.feature.importfiles.presentation

import android.content.IntentSender
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

    data class ChoosingMode(
        val sessionId: String,
        val review: ImportReview,
        val defaultMode: ImportMode?,
        val rememberChoice: Boolean = false,
    ) : ImportStage

    data class Running(val progress: ImportProgress) : ImportStage

    data class Finished(
        val result: ImportResult,
        val deletionOutcome: DeletionOutcome = DeletionOutcome.NOT_ATTEMPTED,
        val awaitingDeletionConfirmation: Boolean = false,
    ) : ImportStage
}

@Immutable
data class ImportUiState(
    val stage: ImportStage = ImportStage.ChoosingSource,
    val isBusy: Boolean = false,
    val error: VaultError? = null,
    val modePreference: ImportModePreference = ImportModePreference.ALWAYS_ASK,
    val dominantCategory: MimeCategory = MimeCategory.OTHER,
)

sealed interface ImportAction {
    data class SourcesPicked(val uriTokens: List<String>, val fromPhotoPicker: Boolean) : ImportAction
    data object PickCancelled : ImportAction
    data object ReviewConfirmed : ImportAction
    data class ModeChosen(val mode: ImportMode, val remember: Boolean) : ImportAction
    data class RememberToggled(val remember: Boolean) : ImportAction
    data object CancelImport : ImportAction
    data class DeletionResultReceived(val approved: Boolean) : ImportAction
    data object SkipDeletion : ImportAction
    data object Done : ImportAction
    data object ErrorDismissed : ImportAction
}

sealed interface ImportEffect {
    /** Ask the Activity to show the platform's own delete confirmation. */
    data class RequestOriginalDeletion(val intentSender: IntentSender) : ImportEffect

    data object Close : ImportEffect
}
