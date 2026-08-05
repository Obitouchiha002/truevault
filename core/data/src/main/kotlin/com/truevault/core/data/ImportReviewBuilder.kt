package com.truevault.core.data

import androidx.core.net.toUri
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.crypto.file.VaultContainer
import com.truevault.core.data.model.ImportReview
import com.truevault.core.model.MimeCategory
import com.truevault.core.model.SelectedSource
import com.truevault.core.storage.SourceResolver
import com.truevault.core.storage.StorageEstimate
import com.truevault.core.storage.VaultFileSystem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Builds the summary the user sees before anything is encrypted.
 *
 * The point of this screen is that the user finds out about a problem — not enough space, a file
 * that cannot be read, a duplicate of something already secured — *before* committing, not through
 * a failure notification afterwards.
 */
@Singleton
class ImportReviewBuilder @Inject constructor(
    private val sourceResolver: SourceResolver,
    private val fileSystem: VaultFileSystem,
    @param:Dispatcher(TrueVaultDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun build(sources: List<SelectedSource>): ImportReview = withContext(ioDispatcher) {
        val known = sources.mapNotNull { it.sizeBytes?.takeIf { size -> size >= 0 } }
        val unknownSizeCount = sources.count { !it.hasKnownSize }

        val unsupported = sources.count { source ->
            // "Unsupported" here means unreadable, not an unfamiliar type: TrueVault stores any file
            // the user picks. A file it cannot open is the only real blocker.
            !sourceResolver.stillExists(source.uriToken.toUri())
        }

        val thumbnailCount = sources.count {
            it.category == MimeCategory.PHOTO || it.category == MimeCategory.VIDEO
        }

        ImportReview(
            fileCount = sources.size,
            totalBytes = known.sum(),
            requiredBytes = StorageEstimate.requiredBytesForBatch(
                sourceBytes = known,
                chunkSize = VaultContainer.DEFAULT_CHUNK_SIZE,
                thumbnailCount = thumbnailCount,
            ),
            availableBytes = fileSystem.freeSpaceBytes(),
            unknownSizeCount = unknownSizeCount,
            duplicateOfExistingCount = 0,
            unsupportedCount = unsupported,
        )
    }
}
