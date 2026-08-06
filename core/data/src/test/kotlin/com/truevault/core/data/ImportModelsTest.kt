package com.truevault.core.data

import com.google.common.truth.Truth.assertThat
import com.truevault.core.data.model.ImportOutcome
import com.truevault.core.data.model.ImportProgress
import com.truevault.core.data.model.ImportResult
import com.truevault.core.data.model.ImportReview
import com.truevault.core.data.model.ImportSession
import com.truevault.core.model.ImportMode
import com.truevault.core.model.SelectedSource
import com.truevault.core.model.VaultError
import org.junit.Test

/**
 * The numbers and lists the import screens render.
 *
 * The engine itself needs a database, a content provider and a device; these types do not, and they
 * are what decides whether the result screen says "3 secured" or offers to delete an original that
 * was never encrypted. Getting `pendingDeletionSources` wrong is how an app deletes a file it did
 * not actually secure, so it is checked against failures, cancellations and Secure Copy alike.
 */
class ImportModelsTest {

    private fun source(name: String, size: Long? = 1_000L) = SelectedSource(
        uriToken = "content://test/$name",
        displayName = name,
        sizeBytes = size,
        mimeType = "image/jpeg",
        isFromPhotoPicker = true,
    )

    @Test
    fun `a session totals only the sizes it actually knows`() {
        val session = ImportSession(
            sessionId = "s",
            sources = listOf(source("a", 100), source("b", null), source("c", 250)),
        )

        assertThat(session.totalBytes).isEqualTo(350)
        // The unknown one is counted separately rather than guessed at, because the review screen
        // has to say "plus 1 file of unknown size" instead of quietly under-reporting.
        assertThat(session.unknownSizeCount).isEqualTo(1)
    }

    @Test
    fun `a review with less free space than required reports the exact shortfall`() {
        val review = ImportReview(
            fileCount = 2,
            totalBytes = 900,
            requiredBytes = 1_000,
            availableBytes = 400,
            unknownSizeCount = 0,
            duplicateOfExistingCount = 0,
            unsupportedCount = 0,
        )

        assertThat(review.hasEnoughSpace).isFalse()
        assertThat(review.shortfallBytes).isEqualTo(600)
    }

    @Test
    fun `a review with enough space reports no shortfall rather than a negative one`() {
        val review = ImportReview(
            fileCount = 1,
            totalBytes = 100,
            requiredBytes = 150,
            availableBytes = 10_000,
            unknownSizeCount = 0,
            duplicateOfExistingCount = 0,
            unsupportedCount = 0,
        )

        assertThat(review.hasEnoughSpace).isTrue()
        assertThat(review.shortfallBytes).isEqualTo(0)
    }

    @Test
    fun `only Secure Move outcomes are offered for deletion`() {
        val moved = source("moved.jpg")
        val copied = source("copied.jpg")
        val failed = source("failed.jpg")
        val cancelled = source("cancelled.jpg")

        val result = ImportResult(
            sessionId = "s",
            mode = ImportMode.SECURE_MOVE,
            outcomes = listOf(
                ImportOutcome.Secured(moved, "id-moved", deletionPending = true),
                ImportOutcome.Secured(copied, "id-copied", deletionPending = false),
                ImportOutcome.Failed(failed, VaultError.EncryptionFailed, retryAllowed = true),
                ImportOutcome.Cancelled(cancelled),
            ),
        )

        // The failed and cancelled sources must not appear here under any circumstances: their
        // content was never committed to the vault, so deleting them would destroy the only copy.
        assertThat(result.pendingDeletionSources).containsExactly(moved)
        assertThat(result.securedItemIds).containsExactly("id-moved", "id-copied").inOrder()
        assertThat(result.securedCount).isEqualTo(2)
        assertThat(result.failedCount).isEqualTo(1)
        assertThat(result.cancelledCount).isEqualTo(1)
        assertThat(result.totalCount).isEqualTo(4)
    }

    @Test
    fun `a batch where everything failed offers nothing for deletion`() {
        val result = ImportResult(
            sessionId = "s",
            mode = ImportMode.SECURE_MOVE,
            outcomes = listOf(
                ImportOutcome.Failed(source("a"), VaultError.InsufficientStorage(10, 1), true),
                ImportOutcome.Failed(source("b"), VaultError.SourceNotFound, true),
            ),
        )

        assertThat(result.pendingDeletionSources).isEmpty()
        assertThat(result.securedItemIds).isEmpty()
        assertThat(result.securedCount).isEqualTo(0)
    }

    @Test
    fun `progress never exceeds one and never runs backwards across a file boundary`() {
        val atStart = ImportProgress("s", 0, 4, 0, 1_000)
        val midFirst = ImportProgress("s", 0, 4, 500, 1_000)
        val firstDone = ImportProgress("s", 1, 4, 0, 1_000)
        val allDone = ImportProgress("s", 4, 4, 0, 0)

        assertThat(atStart.fraction).isEqualTo(0f)
        assertThat(midFirst.fraction).isWithin(1e-4f).of(0.125f)
        assertThat(firstDone.fraction).isWithin(1e-4f).of(0.25f)
        assertThat(midFirst.fraction).isLessThan(firstDone.fraction)
        assertThat(allDone.fraction).isEqualTo(1f)
    }

    @Test
    fun `progress on an empty batch is zero rather than a divide by zero`() {
        val progress = ImportProgress("s", 0, 0, 0, 0)

        assertThat(progress.fraction).isEqualTo(0f)
    }

    @Test
    fun `a file of unknown size contributes no partial progress instead of jumping`() {
        // currentFileTotalBytes == 0 means "size unknown", not "already finished". Treating it as
        // finished would let the bar leap to the next step and then appear to stall.
        val progress = ImportProgress("s", 1, 2, 4_096, 0)

        assertThat(progress.fraction).isWithin(1e-4f).of(0.5f)
    }
}
