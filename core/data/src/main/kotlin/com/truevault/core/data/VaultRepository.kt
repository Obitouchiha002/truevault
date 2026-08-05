package com.truevault.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.common.log.SecureLog
import com.truevault.core.common.result.Outcome
import com.truevault.core.common.result.asFailure
import com.truevault.core.common.result.asSuccess
import com.truevault.core.common.time.TimeProvider
import com.truevault.core.crypto.file.VaultContainerException
import com.truevault.core.crypto.vault.VaultCryptoService
import com.truevault.core.crypto.vault.VaultLockedException
import com.truevault.core.data.model.VaultItem
import com.truevault.core.data.model.VaultItemMetadata
import com.truevault.core.database.dao.VaultItemDao
import com.truevault.core.database.entity.VaultItemEntity
import com.truevault.core.model.ImportMode
import com.truevault.core.model.MimeCategory
import com.truevault.core.model.OriginalDeletionState
import com.truevault.core.model.PrivacyStatus
import com.truevault.core.model.VaultError
import com.truevault.core.model.VaultSortOrder
import com.truevault.core.model.VerificationStatus
import com.truevault.core.storage.VaultFileSystem
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val TAG = "VaultRepo"
private const val PAGE_SIZE = 40

/**
 * Reading, searching and removing vault items.
 *
 * Two decisions shape this class:
 *
 *  - **Paging, not loading.** A vault with 10,000 items pages 40 rows at a time and decrypts only
 *    the metadata for the rows actually on screen.
 *  - **Search happens in memory, not in SQL.** File names are encrypted, so `WHERE name LIKE ?` is
 *    impossible by design. Instead a lightweight index of (id, name) is built once per unlocked
 *    session and filtered there. It costs one pass over the metadata column — a few tens of
 *    milliseconds for 10,000 items — and it is the price of not storing names in plaintext.
 */
@Singleton
class VaultRepository @Inject constructor(
    private val vaultItemDao: VaultItemDao,
    private val cryptoService: VaultCryptoService,
    private val fileSystem: VaultFileSystem,
    private val activityRepository: ActivityRepository,
    private val timeProvider: TimeProvider,
    @param:Dispatcher(TrueVaultDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** (id → lowercase display name), rebuilt whenever the vault changes or the session reopens. */
    @Volatile
    private var nameIndex: Map<String, String>? = null

    fun observeItemCount(): Flow<Int> = vaultItemDao.observeCount()

    fun observeCategoryCounts(): Flow<Map<MimeCategory, Int>> =
        vaultItemDao.observeCategoryCounts().map { rows ->
            rows.mapNotNull { row ->
                runCatching { MimeCategory.valueOf(row.category) }.getOrNull()?.to(row.count)
            }.toMap()
        }

    fun observeCountWithOriginalRemaining(): Flow<Int> =
        vaultItemDao.observeCountByStatus(PrivacyStatus.ORIGINAL_REMAINS.name)

    fun observeTotalOriginalBytes(): Flow<Long> = vaultItemDao.observeTotalOriginalBytes()

    /**
     * Paged items for the vault grid.
     *
     * A row whose metadata cannot be decrypted is not dropped: it is surfaced as
     * [PrivacyStatus.CORRUPTED] so the user can see that something is wrong with it. Silently
     * hiding it would tell the user their file is gone when it is merely unreadable.
     */
    fun pagedItems(
        sortOrder: VaultSortOrder,
        category: MimeCategory?,
    ): Flow<PagingData<VaultItem>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            prefetchDistance = PAGE_SIZE / 2,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = {
            val categoryName = category?.name
            when (sortOrder) {
                VaultSortOrder.DATE_ADDED_DESC -> vaultItemDao.pagingSourceByNewest(categoryName)
                VaultSortOrder.DATE_ADDED_ASC -> vaultItemDao.pagingSourceByOldest(categoryName)
                VaultSortOrder.SIZE_DESC -> vaultItemDao.pagingSourceByLargest(categoryName)
                VaultSortOrder.SIZE_ASC -> vaultItemDao.pagingSourceBySmallest(categoryName)
                VaultSortOrder.TYPE -> vaultItemDao.pagingSourceByType(categoryName)
                // Name ordering is resolved against the in-memory index by the caller, which then
                // asks for those ids in order.
                VaultSortOrder.NAME_ASC, VaultSortOrder.NAME_DESC ->
                    vaultItemDao.pagingSourceByNewest(categoryName)
            }
        },
    ).flow.map { paging -> paging.map(::toDomain) }

    /** Ids whose display name contains [query], newest first. Empty query returns null. */
    suspend fun searchIds(query: String): List<String>? = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext null
        val index = ensureNameIndex() ?: return@withContext emptyList()
        val needle = query.trim().lowercase()
        index.filterValues { it.contains(needle) }.keys.toList()
    }

    /** Ids sorted by display name, for the two name-based sort orders. */
    suspend fun idsSortedByName(descending: Boolean): List<String> = withContext(ioDispatcher) {
        val index = ensureNameIndex() ?: return@withContext emptyList()
        val sorted = index.entries.sortedBy { it.value }
        (if (descending) sorted.reversed() else sorted).map { it.key }
    }

    suspend fun findItem(id: String): VaultItem? = withContext(ioDispatcher) {
        vaultItemDao.findById(id)?.let(::toDomain)
    }

    suspend fun findItems(ids: List<String>): List<VaultItem> = withContext(ioDispatcher) {
        vaultItemDao.findByIds(ids).map(::toDomain)
    }

    /** The decrypted thumbnail bytes, or null when there is none or it cannot be read. */
    suspend fun thumbnailBytes(id: String): ByteArray? = withContext(ioDispatcher) {
        val entity = vaultItemDao.findById(id) ?: return@withContext null
        if (entity.thumbnailRelativePath == null) return@withContext null

        try {
            val fileKey = fileKeyFor(entity) ?: return@withContext null
            val sealed = fileSystem.thumbnailFile(id).readBytes()
            cryptoService.openThumbnail(sealed, fileKey)
        } catch (e: Exception) {
            SecureLog.w(TAG, "Thumbnail unreadable (${e.javaClass.simpleName})")
            null
        }
    }

    /**
     * Decrypts an item into the internal plaintext cache so a viewer can open it.
     *
     * The plaintext lives in `cacheDir`, never in shared storage, and the caller is responsible for
     * deleting it when the viewer closes. Anything left behind by a crash is cleared at startup.
     */
    suspend fun materialiseForViewing(id: String): Outcome<File> = withContext(ioDispatcher) {
        val entity = vaultItemDao.findById(id)
            ?: return@withContext VaultError.SourceNotFound.asFailure()

        val target = File(fileSystem.plaintextCacheDir, "$id.plain")

        try {
            fileSystem.itemFile(id).inputStream().use { input ->
                FileOutputStream(target).use { output ->
                    cryptoService.decryptFile(input, output)
                }
            }
            target.asSuccess()
        } catch (e: VaultLockedException) {
            target.delete()
            VaultError.AuthenticationRequired.asFailure()
        } catch (e: GeneralSecurityException) {
            // The tag did not verify. Never hand back what decrypted so far.
            target.delete()
            markCorrupted(entity.id)
            VaultError.IntegrityCheckFailed.asFailure()
        } catch (e: VaultContainerException.UnsupportedVersion) {
            target.delete()
            VaultError.UnsupportedFormatVersion(e.found, e.maxSupported).asFailure()
        } catch (e: VaultContainerException) {
            target.delete()
            markCorrupted(entity.id)
            VaultError.IntegrityCheckFailed.asFailure()
        } catch (e: IOException) {
            target.delete()
            VaultError.DecryptionFailed.asFailure()
        }
    }

    fun discardPlaintext(file: File) {
        if (file.exists() && !file.delete()) {
            SecureLog.w(TAG, "Temporary plaintext could not be deleted immediately")
        }
    }

    /**
     * Re-reads a container end to end and records the result.
     *
     * Verification is what turns "the file is on disk" into "the file can still be opened", which
     * are different claims — bit rot and partial writes are real.
     */
    suspend fun verifyItem(id: String): Outcome<Unit> = withContext(ioDispatcher) {
        val entity = vaultItemDao.findById(id)
            ?: return@withContext VaultError.SourceNotFound.asFailure()

        try {
            fileSystem.itemFile(entity.id).inputStream().use { cryptoService.verifyFile(it) }
            vaultItemDao.updateVerification(
                entity.id,
                VerificationStatus.VERIFIED.name,
                timeProvider.currentTimeMillis(),
            )
            Unit.asSuccess()
        } catch (e: VaultLockedException) {
            VaultError.AuthenticationRequired.asFailure()
        } catch (e: Exception) {
            markCorrupted(entity.id)
            VaultError.IntegrityCheckFailed.asFailure()
        }
    }

    /** Removes a vault item and its files. Does not touch anything outside TrueVault. */
    suspend fun deleteItem(id: String): Outcome<Unit> = withContext(ioDispatcher) {
        val removed = vaultItemDao.deleteById(id)
        if (removed == 0) return@withContext VaultError.SourceNotFound.asFailure()

        fileSystem.deleteItem(id)
        invalidateNameIndex()
        Unit.asSuccess()
    }

    suspend fun deleteItems(ids: List<String>): Int = withContext(ioDispatcher) {
        var deleted = 0
        ids.forEach { id ->
            if (vaultItemDao.deleteById(id) > 0) {
                fileSystem.deleteItem(id)
                deleted++
            }
        }
        invalidateNameIndex()
        deleted
    }

    /** Called when the vault locks: the decrypted name index must not outlive the session. */
    fun invalidateNameIndex() {
        nameIndex = null
    }

    private suspend fun ensureNameIndex(): Map<String, String>? {
        nameIndex?.let { return it }
        if (!cryptoService.isUnlocked) return null

        return try {
            val built = vaultItemDao.allMetadata().associate { row ->
                val metadata = decodeMetadata(row.encryptedMetadata)
                row.id to (metadata?.displayName?.lowercase() ?: "")
            }
            nameIndex = built
            built
        } catch (e: VaultLockedException) {
            null
        }
    }

    private suspend fun markCorrupted(id: String) {
        vaultItemDao.updateVerification(
            id,
            VerificationStatus.FAILED.name,
            timeProvider.currentTimeMillis(),
        )
        vaultItemDao.updatePrivacyStatus(
            id,
            PrivacyStatus.CORRUPTED.name,
            timeProvider.currentTimeMillis(),
        )
    }

    private fun fileKeyFor(entity: VaultItemEntity): javax.crypto.SecretKey? = try {
        fileSystem.itemFile(entity.id).inputStream().use { input ->
            val header = com.truevault.core.crypto.file.VaultContainerCodec.read(input)
            cryptoService.unwrapFileKey(header.wrappedFileKey)
        }
    } catch (e: Exception) {
        null
    }

    private fun decodeMetadata(sealed: ByteArray): VaultItemMetadata? = try {
        json.decodeFromString(
            VaultItemMetadata.serializer(),
            String(cryptoService.openDatabaseField(sealed)),
        )
    } catch (e: Exception) {
        null
    }

    private fun toDomain(entity: VaultItemEntity): VaultItem {
        val metadata = decodeMetadata(entity.encryptedMetadata)
        val status = runCatching { PrivacyStatus.valueOf(entity.privacyStatus) }
            .getOrDefault(PrivacyStatus.VERIFYING)

        return VaultItem(
            id = entity.id,
            displayName = metadata?.displayName ?: UNREADABLE_NAME,
            mimeType = metadata?.mimeType,
            category = runCatching { MimeCategory.valueOf(entity.mimeCategory) }
                .getOrDefault(MimeCategory.OTHER),
            originalSizeBytes = entity.originalSize,
            encryptedSizeBytes = entity.encryptedSize,
            createdAtMillis = entity.createdAt,
            updatedAtMillis = entity.updatedAt,
            importMode = runCatching { ImportMode.valueOf(entity.importMode) }
                .getOrDefault(ImportMode.SECURE_COPY),
            // A row whose metadata will not decrypt is reported as corrupted rather than hidden.
            privacyStatus = if (metadata == null) PrivacyStatus.CORRUPTED else status,
            verificationStatus = runCatching { VerificationStatus.valueOf(entity.verificationStatus) }
                .getOrDefault(VerificationStatus.NOT_VERIFIED),
            originalDeletionState = runCatching {
                OriginalDeletionState.valueOf(entity.originalDeletionState)
            }.getOrDefault(OriginalDeletionState.NOT_REQUESTED),
            hasThumbnail = entity.thumbnailRelativePath != null,
            originalUriToken = metadata?.originalUriToken,
            lastIntegrityCheckAtMillis = entity.lastIntegrityCheckAt,
        )
    }

    private companion object {
        const val UNREADABLE_NAME = "Unreadable item"
    }
}
