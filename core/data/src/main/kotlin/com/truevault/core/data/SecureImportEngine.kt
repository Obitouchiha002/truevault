package com.truevault.core.data

import androidx.core.net.toUri
import androidx.room.withTransaction
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.common.log.SecureLog
import com.truevault.core.common.time.TimeProvider
import com.truevault.core.crypto.file.CancellationSignal
import com.truevault.core.crypto.file.VaultContainer
import com.truevault.core.crypto.file.VaultStreamCancelledException
import com.truevault.core.crypto.vault.VaultCryptoService
import com.truevault.core.crypto.vault.VaultLockedException
import com.truevault.core.data.model.ImportOutcome
import com.truevault.core.data.model.ImportProgress
import com.truevault.core.data.model.ImportResult
import com.truevault.core.data.model.VaultItemMetadata
import com.truevault.core.database.TrueVaultDatabase
import com.truevault.core.database.dao.ImportTransactionDao
import com.truevault.core.database.dao.VaultItemDao
import com.truevault.core.database.entity.ImportTransactionEntity
import com.truevault.core.database.entity.VaultItemEntity
import com.truevault.core.model.DeletionOutcome
import com.truevault.core.model.ImportMode
import com.truevault.core.model.ImportTransactionState
import com.truevault.core.model.MimeCategory
import com.truevault.core.model.OriginalDeletionState
import com.truevault.core.model.PrivacyStatus
import com.truevault.core.model.SelectedSource
import com.truevault.core.model.VaultError
import com.truevault.core.model.VerificationStatus
import com.truevault.core.storage.ContentHasher
import com.truevault.core.storage.SourceResolver
import com.truevault.core.storage.SourceUnavailableException
import com.truevault.core.storage.StorageEstimate
import com.truevault.core.storage.ThumbnailFactory
import com.truevault.core.storage.VaultFileSystem
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val TAG = "Import"

/** UI progress updates are throttled to this, independent of how fast chunks complete. */
private const val PROGRESS_EMIT_INTERVAL_BYTES = 512L * 1024L

/**
 * Secure Copy and Secure Move.
 *
 * The order below is the whole point of this class, and it never varies:
 *
 * ```
 *  1. validate source access          8. commit the vault-item row
 *  2. check available storage         9. mark the item secured
 *  3. create a random vault item id  10. hand the deletion question to the caller
 *  4. open a .vault.part destination 11. record what the deletion actually did
 *  5. stream through encryption      12. report
 *  6. close every stream and fsync
 *  7. verify the encrypted output, then atomically rename .part → .vault
 * ```
 *
 * It is never "copy, then delete". Verification happens *before* the rename, and the rename happens
 * before the user is even asked about the original — so a crash, a cancellation or a full disk at
 * any point leaves the original exactly where it was.
 *
 * Deletion itself is not performed here. It needs an Activity to show the platform's confirmation
 * dialog, so the engine reports which items are awaiting an answer and the caller brings back the
 * real outcome through [recordDeletionOutcome].
 */
@Singleton
class SecureImportEngine @Inject constructor(
    private val sourceResolver: SourceResolver,
    private val fileSystem: VaultFileSystem,
    private val cryptoService: VaultCryptoService,
    private val hasher: ContentHasher,
    private val thumbnailFactory: ThumbnailFactory,
    private val database: TrueVaultDatabase,
    private val vaultItemDao: VaultItemDao,
    private val transactionDao: ImportTransactionDao,
    private val activityRepository: ActivityRepository,
    private val timeProvider: TimeProvider,
    @param:Dispatcher(TrueVaultDispatcher.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /**
     * Imports a batch, emitting progress as it goes and the result last.
     *
     * Files are processed one at a time. Two concurrent large encryptions would double peak memory
     * and compete for the same storage bandwidth, so the queue is deliberately serial and the limit
     * lives here rather than in a caller that might forget it.
     *
     * Cancelling the collector cancels the import: the current file's `.part` is removed, its
     * transaction row is marked cancelled, and every original is left untouched.
     */
    fun import(
        sessionId: String,
        sources: List<SelectedSource>,
        mode: ImportMode,
    ): Flow<ImportStep> = channelFlow {
        val outcomes = mutableListOf<ImportOutcome>()

        trySendBlocking(
            ImportStep.Progress(
                ImportProgress(
                    sessionId = sessionId,
                    completedFiles = 0,
                    totalFiles = sources.size,
                    currentFileBytesProcessed = 0,
                    currentFileTotalBytes = sources.firstOrNull()?.sizeBytes ?: 0,
                ),
            ),
        )

        sources.forEachIndexed { index, source ->
            currentCoroutineContext().ensureActive()

            val outcome = importOne(source, mode) { processed, total ->
                // trySendBlocking is safe to call from the encryption thread and drops nothing that
                // matters: progress is a monotonically increasing value, so a lost update is
                // immediately corrected by the next one.
                trySendBlocking(
                    ImportStep.Progress(
                        ImportProgress(
                            sessionId = sessionId,
                            completedFiles = index,
                            totalFiles = sources.size,
                            currentFileBytesProcessed = processed,
                            currentFileTotalBytes = total,
                        ),
                    ),
                )
            }
            outcomes += outcome
        }

        val secured = outcomes.count { it is ImportOutcome.Secured }
        if (secured > 0) activityRepository.record(ActivityKind.FILES_SECURED, secured)

        val failed = outcomes.count { it is ImportOutcome.Failed }
        if (failed > 0) activityRepository.record(ActivityKind.IMPORT_FAILED, failed)

        trySendBlocking(
            ImportStep.Finished(
                ImportResult(sessionId = sessionId, mode = mode, outcomes = outcomes),
            ),
        )

        awaitClose { }
    }

    private suspend fun importOne(
        source: SelectedSource,
        mode: ImportMode,
        onProgress: (Long, Long) -> Unit,
    ): ImportOutcome = withContext(defaultDispatcher) {
        val uri = runCatching { source.uriToken.toUri() }.getOrNull()
            ?: return@withContext ImportOutcome.Failed(source, VaultError.SourceNotFound, false)

        // Step 1: the source must still be readable. Between picking and importing a file can be
        // deleted, a grant can expire, or a cloud document can go offline.
        val sizeBytes = source.sizeBytes?.takeIf { it >= 0 } ?: sourceResolver.measure(uri)
        if (sizeBytes == null) {
            return@withContext ImportOutcome.Failed(source, VaultError.SourceNotFound, true)
        }

        // Step 2: refuse before anything is written, not halfway through.
        val required = StorageEstimate.requiredBytes(
            sourceBytes = sizeBytes,
            chunkSize = VaultContainer.DEFAULT_CHUNK_SIZE,
            includesThumbnail = source.category.hasThumbnail(),
        )
        val available = fileSystem.freeSpaceBytes()
        if (available < required) {
            return@withContext ImportOutcome.Failed(
                source = source,
                error = VaultError.InsufficientStorage(required, available),
                retryAllowed = true,
            )
        }

        // Step 3: a random id, derived from nothing. It appears in file names on disk, so deriving
        // it from the source name or URI would leak exactly what the vault is meant to hide.
        val vaultItemId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        val now = timeProvider.currentTimeMillis()

        val fileKey = try {
            cryptoService.generateFileKey()
        } catch (e: VaultLockedException) {
            return@withContext ImportOutcome.Failed(source, VaultError.AuthenticationRequired, true)
        }

        val transaction = try {
            ImportTransactionEntity(
                transactionId = transactionId,
                vaultItemId = vaultItemId,
                encryptedSourceToken = cryptoService.sealDatabaseField(source.uriToken.toByteArray()),
                state = ImportTransactionState.VALIDATED.name,
                importMode = mode.name,
                bytesProcessed = 0,
                totalBytes = sizeBytes,
                createdAt = now,
                updatedAt = now,
                failureCode = null,
                retryAllowed = true,
            )
        } catch (e: VaultLockedException) {
            return@withContext ImportOutcome.Failed(source, VaultError.AuthenticationRequired, true)
        }

        transactionDao.insert(transaction)

        val partFile = fileSystem.partFile(vaultItemId)
        val job = currentCoroutineContext()[Job]
        var lastEmittedBytes = 0L

        try {
            val wrappedFileKey = cryptoService.wrapFileKey(fileKey)
            val metadata = VaultItemMetadata(
                displayName = source.displayName ?: "file",
                mimeType = source.mimeType,
                // The URI is retained only for Secure Move, and only until deletion is resolved.
                originalUriToken = if (mode == ImportMode.SECURE_MOVE) source.uriToken else null,
                contentHashHex = null,
            )
            val sealedContainerMetadata = cryptoService.sealMetadata(
                json.encodeToString(VaultItemMetadata.serializer(), metadata).toByteArray(),
                fileKey,
            )

            // The transaction row records *state*, not byte-accurate progress: recovery needs to
            // know which phase was interrupted, and writing a row per megabyte would be a write
            // storm for no recovery benefit.
            transactionDao.updateProgress(
                transactionId,
                ImportTransactionState.ENCRYPTING.name,
                0,
                timeProvider.currentTimeMillis(),
            )

            // Steps 4–6: stream through encryption into the .part file, then close and fsync.
            sourceResolver.openInputStream(uri).use { input ->
                FileOutputStream(partFile).use { rawOutput ->
                    val buffered = BufferedOutputStream(rawOutput)
                    cryptoService.encryptFile(
                        source = input,
                        destination = buffered,
                        fileKey = fileKey,
                        wrappedFileKey = wrappedFileKey,
                        sealedMetadata = sealedContainerMetadata,
                        plaintextSize = sizeBytes,
                        cancellationSignal = CancellationSignal { job?.isActive == false },
                        progressListener = { processed, total ->
                            if (processed - lastEmittedBytes >= PROGRESS_EMIT_INTERVAL_BYTES ||
                                processed == total
                            ) {
                                lastEmittedBytes = processed
                                onProgress(processed, total)
                            }
                        },
                    )
                    buffered.flush()
                    // Force the bytes out of the page cache before anything calls this verified.
                    rawOutput.fd.sync()
                }
            }

            currentCoroutineContext().ensureActive()

            // Step 7: read the container back and check every authentication tag. A container that
            // cannot be opened must never be committed, and no original may be deleted for one.
            transactionDao.updateProgress(
                transactionId,
                ImportTransactionState.VERIFYING.name,
                sizeBytes,
                timeProvider.currentTimeMillis(),
            )
            partFile.inputStream().use { cryptoService.verifyFile(it) }

            val contentHash = sourceResolver.openInputStream(uri).use { hasher.sha256(it) }
            val fingerprint = contentHash?.let(cryptoService::fingerprint)

            val thumbnailBytes = if (source.category.hasThumbnail()) {
                thumbnailFactory.createThumbnail(uri, source.category)
            } else {
                null
            }

            // The rename is atomic, so from this instant the container either exists complete under
            // its final name or does not exist at all.
            if (!fileSystem.commit(vaultItemId)) {
                fileSystem.discardPart(vaultItemId)
                markTerminal(transactionId, ImportTransactionState.FAILED, "COMMIT_FAILED", true)
                return@withContext ImportOutcome.Failed(
                    source,
                    VaultError.DatabaseTransactionFailed,
                    true,
                )
            }

            if (thumbnailBytes != null) {
                runCatching {
                    fileSystem.thumbnailFile(vaultItemId)
                        .writeBytes(cryptoService.sealThumbnail(thumbnailBytes, fileKey))
                }.onFailure {
                    // A thumbnail is a convenience. Losing one must never fail a secured import.
                    SecureLog.w(TAG, "Thumbnail could not be written")
                }
            }

            // Step 8: the row and the transaction state move together, so the vault index can never
            // record an item while still believing the import is in flight.
            val entity = VaultItemEntity(
                id = vaultItemId,
                fileRelativePath = "$vaultItemId${VaultContainer.FINAL_EXTENSION}",
                thumbnailRelativePath = if (thumbnailBytes != null) "$vaultItemId.thumb" else null,
                encryptedMetadata = cryptoService.sealDatabaseField(
                    json.encodeToString(
                        VaultItemMetadata.serializer(),
                        metadata.copy(contentHashHex = contentHash?.let(hasher::toHex)),
                    ).toByteArray(),
                ),
                mimeCategory = source.category.name,
                encryptedSize = fileSystem.itemFile(vaultItemId).length(),
                originalSize = sizeBytes,
                createdAt = now,
                updatedAt = timeProvider.currentTimeMillis(),
                importMode = mode.name,
                // Secure Copy is honest from the first moment: the original is still there.
                privacyStatus = when (mode) {
                    ImportMode.SECURE_COPY -> PrivacyStatus.ORIGINAL_REMAINS
                    ImportMode.SECURE_MOVE -> PrivacyStatus.DELETE_PENDING
                }.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                keyVersion = 1,
                fileFormatVersion = VaultContainer.CURRENT_FORMAT_VERSION,
                originalDeletionState = when (mode) {
                    ImportMode.SECURE_COPY -> OriginalDeletionState.NOT_REQUESTED
                    ImportMode.SECURE_MOVE -> OriginalDeletionState.REQUESTED
                }.name,
                contentFingerprint = fingerprint,
                // The authoritative copy. The header holds the same bytes today, but only the row's
                // copy can be re-wrapped when this item is restored into a different vault.
                wrappedFileKey = wrappedFileKey,
                lastIntegrityCheckAt = timeProvider.currentTimeMillis(),
            )

            try {
                database.withTransaction {
                    vaultItemDao.insert(entity)
                    transactionDao.updateProgress(
                        transactionId,
                        ImportTransactionState.COMMITTED.name,
                        sizeBytes,
                        timeProvider.currentTimeMillis(),
                    )
                }
            } catch (e: Exception) {
                // The row failed after the file was renamed. Remove the file so the vault never
                // holds a container nothing points at.
                SecureLog.e(TAG, "Vault row insert failed after commit", e)
                fileSystem.deleteItem(vaultItemId)
                markTerminal(transactionId, ImportTransactionState.FAILED, "DB_INSERT", true)
                return@withContext ImportOutcome.Failed(
                    source,
                    VaultError.DatabaseTransactionFailed,
                    true,
                )
            }

            SecureLog.i(TAG, "Item secured (${source.describeSafely()})")

            ImportOutcome.Secured(
                source = source,
                vaultItemId = vaultItemId,
                deletionPending = mode == ImportMode.SECURE_MOVE,
            )
        } catch (e: VaultStreamCancelledException) {
            cleanUpAfterFailure(vaultItemId, transactionId, ImportTransactionState.CANCELLED, null)
            ImportOutcome.Cancelled(source)
        } catch (e: CancellationException) {
            cleanUpAfterFailure(vaultItemId, transactionId, ImportTransactionState.CANCELLED, null)
            throw e
        } catch (e: VaultLockedException) {
            cleanUpAfterFailure(vaultItemId, transactionId, ImportTransactionState.FAILED, "LOCKED")
            ImportOutcome.Failed(source, VaultError.AuthenticationRequired, true)
        } catch (e: SourceUnavailableException) {
            cleanUpAfterFailure(vaultItemId, transactionId, ImportTransactionState.FAILED, "SOURCE_LOST")
            ImportOutcome.Failed(source, VaultError.SourceNotFound, true)
        } catch (e: IOException) {
            SecureLog.e(TAG, "Import failed while writing", e)
            cleanUpAfterFailure(vaultItemId, transactionId, ImportTransactionState.FAILED, "IO")
            ImportOutcome.Failed(source, VaultError.EncryptionFailed, true)
        } catch (e: Exception) {
            SecureLog.e(TAG, "Import failed", e)
            cleanUpAfterFailure(vaultItemId, transactionId, ImportTransactionState.FAILED, "UNKNOWN")
            ImportOutcome.Failed(source, VaultError.EncryptionFailed, true)
        }
    }

    /**
     * Records what actually happened to an original file.
     *
     * Called only with an outcome the platform reported. The privacy status follows the truth: a
     * declined or failed deletion leaves the item as `ORIGINAL_REMAINS`, never `SECURED`.
     */
    suspend fun recordDeletionOutcome(
        vaultItemIds: List<String>,
        outcome: DeletionOutcome,
    ): Unit = withContext(defaultDispatcher) {
        val now = timeProvider.currentTimeMillis()

        val (deletionState, privacyStatus) = when (outcome) {
            DeletionOutcome.DELETED,
            DeletionOutcome.ALREADY_MISSING,
            -> OriginalDeletionState.CONFIRMED_DELETED to PrivacyStatus.SECURED

            DeletionOutcome.USER_CANCELLED ->
                OriginalDeletionState.DECLINED_BY_USER to PrivacyStatus.ORIGINAL_REMAINS

            DeletionOutcome.PROVIDER_NOT_SUPPORTED,
            DeletionOutcome.PERMISSION_LOST,
            DeletionOutcome.FAILED,
            -> OriginalDeletionState.FAILED to PrivacyStatus.ORIGINAL_REMAINS

            DeletionOutcome.NOT_ATTEMPTED ->
                OriginalDeletionState.NOT_REQUESTED to PrivacyStatus.ORIGINAL_REMAINS
        }

        vaultItemIds.forEach { id ->
            vaultItemDao.updateDeletionState(id, deletionState.name, privacyStatus.name, now)
        }

        if (deletionState == OriginalDeletionState.CONFIRMED_DELETED && vaultItemIds.isNotEmpty()) {
            activityRepository.record(ActivityKind.ORIGINAL_DELETED, vaultItemIds.size)
        }
    }

    /**
     * Crash recovery, run once at startup.
     *
     * Rules, in order of importance:
     *  - **No original is ever deleted here.** Recovery only cleans up TrueVault's own temporary
     *    files; a half-finished import must never take a user's file with it.
     *  - A `.part` file whose transaction is not terminal is an interrupted import: the row becomes
     *    failed-but-retryable and the temporary file is removed.
     *  - A `.part` file with no transaction row at all is unambiguously abandoned, and is removed.
     *  - Temporary plaintext left by a viewer that was killed is cleared.
     */
    suspend fun recoverInterruptedImports(): RecoveryReport = withContext(defaultDispatcher) {
        val inFlightStates = listOf(
            ImportTransactionState.PENDING.name,
            ImportTransactionState.VALIDATED.name,
            ImportTransactionState.ENCRYPTING.name,
            ImportTransactionState.VERIFYING.name,
        )

        val interrupted = transactionDao.findByStates(inFlightStates)
        interrupted.forEach { transaction ->
            fileSystem.discardPart(transaction.vaultItemId)
            markTerminal(
                transaction.transactionId,
                ImportTransactionState.FAILED,
                "INTERRUPTED",
                retryAllowed = true,
            )
        }

        val knownIds = interrupted.map { it.vaultItemId }.toSet()
        val orphans = fileSystem.findOrphanedParts().filterNot { it in knownIds }
        orphans.forEach(fileSystem::discardPart)

        fileSystem.clearPlaintextCache()

        RecoveryReport(
            interruptedImports = interrupted.size,
            orphanedTemporaryFiles = orphans.size,
        )
    }

    private suspend fun cleanUpAfterFailure(
        vaultItemId: String,
        transactionId: String,
        state: ImportTransactionState,
        failureCode: String?,
    ) {
        fileSystem.discardPart(vaultItemId)
        markTerminal(
            transactionId,
            state,
            failureCode,
            retryAllowed = state != ImportTransactionState.CANCELLED,
        )
    }

    private suspend fun markTerminal(
        transactionId: String,
        state: ImportTransactionState,
        failureCode: String?,
        retryAllowed: Boolean,
    ) {
        transactionDao.markTerminal(
            id = transactionId,
            state = state.name,
            failureCode = failureCode,
            retryAllowed = retryAllowed,
            updatedAt = timeProvider.currentTimeMillis(),
        )
    }
}

/** What the engine emits while a batch runs. */
sealed interface ImportStep {
    data class Progress(val progress: ImportProgress) : ImportStep
    data class Finished(val result: ImportResult) : ImportStep
}

data class RecoveryReport(
    val interruptedImports: Int,
    val orphanedTemporaryFiles: Int,
) {
    val hadWorkToDo: Boolean get() = interruptedImports > 0 || orphanedTemporaryFiles > 0
}

internal fun MimeCategory.hasThumbnail(): Boolean =
    this == MimeCategory.PHOTO || this == MimeCategory.VIDEO
