package com.truevault.core.data.model

import com.truevault.core.model.DeletionOutcome
import com.truevault.core.model.ImportMode
import com.truevault.core.model.SelectedSource
import com.truevault.core.model.VaultError

/** A batch the user selected, kept in memory and addressed by [sessionId] from navigation routes. */
data class ImportSession(
    val sessionId: String,
    val sources: List<SelectedSource>,
    val mode: ImportMode? = null,
) {
    val totalBytes: Long get() = sources.sumOf { it.sizeBytes ?: 0L }
    val unknownSizeCount: Int get() = sources.count { !it.hasKnownSize }
}

/** What the review screen shows before anything is encrypted. */
data class ImportReview(
    val fileCount: Int,
    val totalBytes: Long,
    val requiredBytes: Long,
    val availableBytes: Long,
    val unknownSizeCount: Int,
    val duplicateOfExistingCount: Int,
    val unsupportedCount: Int,
) {
    val hasEnoughSpace: Boolean get() = availableBytes >= requiredBytes
    val shortfallBytes: Long get() = (requiredBytes - availableBytes).coerceAtLeast(0)
}

/** Progress for one file, throttled by the engine before it reaches the UI. */
data class ImportProgress(
    val sessionId: String,
    val completedFiles: Int,
    val totalFiles: Int,
    val currentFileBytesProcessed: Long,
    val currentFileTotalBytes: Long,
    val isCancelling: Boolean = false,
) {
    val fraction: Float
        get() {
            if (totalFiles == 0) return 0f
            val perFile = 1f / totalFiles
            val currentFraction = if (currentFileTotalBytes > 0) {
                currentFileBytesProcessed.toFloat() / currentFileTotalBytes
            } else {
                0f
            }
            return (completedFiles * perFile + currentFraction * perFile).coerceIn(0f, 1f)
        }
}

/** The outcome for one file. */
sealed interface ImportOutcome {

    val source: SelectedSource

    /**
     * Encrypted, verified and committed.
     *
     * [deletionPending] is true for Secure Move: the vault copy is safe, and the original has not
     * been touched yet. The user is asked separately, and the answer is recorded separately.
     */
    data class Secured(
        override val source: SelectedSource,
        val vaultItemId: String,
        val deletionPending: Boolean,
    ) : ImportOutcome

    /** Nothing outside TrueVault was modified. */
    data class Failed(
        override val source: SelectedSource,
        val error: VaultError,
        val retryAllowed: Boolean,
    ) : ImportOutcome

    data class Cancelled(override val source: SelectedSource) : ImportOutcome
}

/** The complete result of an import batch, as the result screen renders it. */
data class ImportResult(
    val sessionId: String,
    val mode: ImportMode,
    val outcomes: List<ImportOutcome>,
    val deletionOutcome: DeletionOutcome = DeletionOutcome.NOT_ATTEMPTED,
) {
    val securedCount: Int get() = outcomes.count { it is ImportOutcome.Secured }
    val failedCount: Int get() = outcomes.count { it is ImportOutcome.Failed }
    val cancelledCount: Int get() = outcomes.count { it is ImportOutcome.Cancelled }
    val totalCount: Int get() = outcomes.size

    val securedItemIds: List<String>
        get() = outcomes.filterIsInstance<ImportOutcome.Secured>().map { it.vaultItemId }

    val pendingDeletionSources: List<SelectedSource>
        get() = outcomes.filterIsInstance<ImportOutcome.Secured>()
            .filter { it.deletionPending }
            .map { it.source }
}
