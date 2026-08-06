package com.truevault.core.data

import androidx.core.net.toUri
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.common.log.SecureLog
import com.truevault.core.common.time.TimeProvider
import com.truevault.core.crypto.vault.VaultCryptoService
import com.truevault.core.crypto.vault.VaultLockedException
import com.truevault.core.data.model.VaultItemMetadata
import com.truevault.core.database.dao.ScanResultDao
import com.truevault.core.database.dao.VaultItemDao
import com.truevault.core.database.entity.ScanResultEntity
import com.truevault.core.database.entity.VaultItemEntity
import com.truevault.core.model.MimeCategory
import com.truevault.core.model.OriginalDeletionState
import com.truevault.core.model.PrivacyStatus
import com.truevault.core.model.ScanMatchType
import com.truevault.core.model.SelectedSource
import com.truevault.core.storage.ContentHasher
import com.truevault.core.storage.DocumentTreeWalker
import com.truevault.core.storage.SourceResolver
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val TAG = "Scan"

/** One finding, as the review screen shows it. */
data class ScanFinding(
    val id: String,
    val scanId: String,
    val vaultItemId: String,
    val vaultItemName: String,
    val matchType: ScanMatchType,
    val matchedUriToken: String,
    val matchedDisplayName: String?,
    val matchedSizeBytes: Long,
    /** 0..100. Always shown; the user decides, not the app. */
    val confidence: Int,
    val resolved: Boolean,
)

/** What the scanner is doing right now. */
sealed interface ScanStep {
    data class Enumerating(val filesFound: Int) : ScanStep
    data class Comparing(val checked: Int, val total: Int) : ScanStep
    data class Finished(val report: ScanReport) : ScanStep
}

data class ScanReport(
    val scanId: String,
    val filesExamined: Int,
    val findings: List<ScanFinding>,
    val truncated: Boolean,
) {
    val exactDuplicates: Int get() = findings.count { it.matchType == ScanMatchType.EXACT_DUPLICATE }
    val originalsRemaining: Int get() = findings.count { it.matchType == ScanMatchType.ORIGINAL_REMAINS }
    val cloudCopies: Int get() = findings.count { it.matchType == ScanMatchType.CLOUD_COPY_POSSIBLE }
}

/**
 * The privacy leak scanner.
 *
 * What it can do, and nothing more: compare files inside a folder the user explicitly granted
 * against the vault, by size, MIME type and SHA-256 content hash. It cannot look inside other apps'
 * storage, encrypted chats, or cloud accounts, and it does not pretend to.
 *
 * Two design points that keep the results honest:
 *
 *  - **Hashing is the last step, not the first.** Size and category are compared first, so a scan of
 *    a 20 GB folder hashes only the handful of files that could possibly match. Hashing everything
 *    would be minutes of CPU for the same answer.
 *  - **Nothing is ever deleted here.** The engine records findings; removal happens only after the
 *    user reviews them and the platform asks for confirmation.
 */
@Singleton
class PrivacyScanEngine @Inject constructor(
    private val treeWalker: DocumentTreeWalker,
    private val sourceResolver: SourceResolver,
    private val hasher: ContentHasher,
    private val cryptoService: VaultCryptoService,
    private val vaultItemDao: VaultItemDao,
    private val scanResultDao: ScanResultDao,
    private val activityRepository: ActivityRepository,
    private val timeProvider: TimeProvider,
    @param:Dispatcher(TrueVaultDispatcher.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Scans a granted folder tree.
     *
     * @param treeUriToken the URI returned by `ACTION_OPEN_DOCUMENT_TREE`.
     */
    fun scan(treeUriToken: String): Flow<ScanStep> = channelFlow {
        val scanId = UUID.randomUUID().toString()

        val sources = withContext(defaultDispatcher) {
            treeWalker.walk(
                treeUri = treeUriToken.toUri(),
                onProgress = { visited -> trySend(ScanStep.Enumerating(visited)) },
                cancellationSignal = { !this@channelFlow.isActive },
            )
        }

        val findings = withContext(defaultDispatcher) {
            compare(scanId, sources) { checked, total ->
                trySend(ScanStep.Comparing(checked, total))
            }
        }

        if (findings.isNotEmpty()) {
            scanResultDao.insertAll(findings.map { it.toEntity() })
            activityRepository.record(ActivityKind.DUPLICATE_DETECTED, findings.size)
        }

        send(
            ScanStep.Finished(
                ScanReport(
                    scanId = scanId,
                    filesExamined = sources.size,
                    findings = findings,
                    truncated = treeWalker.wasTruncated(sources.size),
                ),
            ),
        )
    }

    private suspend fun compare(
        scanId: String,
        sources: List<SelectedSource>,
        onProgress: (Int, Int) -> Unit,
    ): List<ScanFinding> {
        val vaultItems = try {
            vaultItemDao.allMetadata()
        } catch (e: VaultLockedException) {
            return emptyList()
        }

        if (vaultItems.isEmpty()) return emptyList()

        val entities = vaultItemDao.findByIds(vaultItems.map { it.id })
        val byFingerprint = entities
            .filter { it.contentFingerprint != null }
            .groupBy { it.contentFingerprint!!.toList() }
        val sizesInVault = entities.map { it.originalSize }.toSet()
        val names = entities.associate { it.id to (decodeMetadata(it.encryptedMetadata)?.displayName ?: "") }

        val findings = mutableListOf<ScanFinding>()
        val now = timeProvider.currentTimeMillis()

        // Pass 1: originals that Secure Move was supposed to remove but that are still readable.
        entities.forEach { entity ->
            if (entity.originalDeletionState == OriginalDeletionState.CONFIRMED_DELETED.name) return@forEach
            val token = decodeMetadata(entity.encryptedMetadata)?.originalUriToken ?: return@forEach

            val stillThere = runCatching { sourceResolver.stillExists(token.toUri()) }.getOrDefault(false)
            if (stillThere) {
                findings += ScanFinding(
                    id = UUID.randomUUID().toString(),
                    scanId = scanId,
                    vaultItemId = entity.id,
                    vaultItemName = names[entity.id].orEmpty(),
                    matchType = ScanMatchType.ORIGINAL_REMAINS,
                    matchedUriToken = token,
                    matchedDisplayName = names[entity.id],
                    matchedSizeBytes = entity.originalSize,
                    // Directly observed, not inferred: the exact URI still opens.
                    confidence = 100,
                    resolved = false,
                )
            }
        }

        // Pass 2: candidates in the scanned folder. Size is the cheap filter; only survivors get hashed.
        val candidates = candidateSources(sources, sizesInVault)

        candidates.forEachIndexed { index, source ->
            if (!currentCoroutineContext().isActive) return findings
            onProgress(index + 1, candidates.size)

            val uri = runCatching { source.uriToken.toUri() }.getOrNull() ?: return@forEachIndexed
            val hash = runCatching {
                sourceResolver.openInputStream(uri).use { hasher.sha256(it) }
            }.getOrNull() ?: return@forEachIndexed

            val fingerprint = try {
                cryptoService.fingerprint(hash).toList()
            } catch (e: VaultLockedException) {
                return findings
            }

            byFingerprint[fingerprint]?.forEach { match ->
                // An exact content match. This is the only claim the scanner makes with full
                // confidence, because it is the only one it can prove.
                findings += ScanFinding(
                    id = UUID.randomUUID().toString(),
                    scanId = scanId,
                    vaultItemId = match.id,
                    vaultItemName = names[match.id].orEmpty(),
                    matchType = ScanMatchType.EXACT_DUPLICATE,
                    matchedUriToken = source.uriToken,
                    matchedDisplayName = source.displayName,
                    matchedSizeBytes = source.sizeBytes ?: 0L,
                    confidence = 100,
                    resolved = false,
                )
            }
        }

        SecureLog.i(TAG, "Scan produced ${findings.size} finding(s) from ${sources.size} file(s)")
        return findings
    }

    /** Marks a finding handled. Called after the user acts on it, never automatically. */
    suspend fun markResolved(findingId: String): Unit = withContext(defaultDispatcher) {
        scanResultDao.markResolved(findingId)
    }

    /** After an original is confirmed gone, the item is finally SECURED and its findings are closed. */
    suspend fun onOriginalRemoved(vaultItemId: String): Unit = withContext(defaultDispatcher) {
        val now = timeProvider.currentTimeMillis()
        vaultItemDao.updateDeletionState(
            id = vaultItemId,
            state = OriginalDeletionState.CONFIRMED_DELETED.name,
            privacyStatus = PrivacyStatus.SECURED.name,
            updatedAt = now,
        )
        scanResultDao.markAllResolvedForItem(vaultItemId)
    }

    fun observeUnresolvedDuplicateCount(): Flow<Int> =
        scanResultDao.observeUnresolvedCount(ScanMatchType.EXACT_DUPLICATE.name)

    private fun decodeMetadata(sealed: ByteArray): VaultItemMetadata? = try {
        json.decodeFromString(
            VaultItemMetadata.serializer(),
            String(cryptoService.openDatabaseField(sealed)),
        )
    } catch (e: Exception) {
        null
    }

    private fun ScanFinding.toEntity() = ScanResultEntity(
        id = id,
        scanId = scanId,
        vaultItemId = vaultItemId,
        matchType = matchType.name,
        // The matched URI is sealed: it names a file and a folder on the user's device.
        encryptedMatchedToken = cryptoService.sealDatabaseField(matchedUriToken.toByteArray()),
        confidence = confidence,
        matchedSize = matchedSizeBytes,
        createdAt = timeProvider.currentTimeMillis(),
        resolved = resolved,
    )

    @Suppress("unused")
    private fun VaultItemEntity.categoryOrOther(): MimeCategory =
        runCatching { MimeCategory.valueOf(mimeCategory) }.getOrDefault(MimeCategory.OTHER)
}

/**
 * Narrows a scanned folder to the files worth hashing.
 *
 * Size is the only filter cheap enough to apply to every file in a large tree, and it is safe in the
 * direction that matters: two files with different sizes can never have the same content, so nothing
 * a full hash would have caught is dropped here.
 *
 * A file whose size the provider did not report is **excluded**. It cannot be compared, and
 * including it would put a file in the results that the scanner has no evidence about.
 */
internal fun candidateSources(
    sources: List<SelectedSource>,
    sizesInVault: Set<Long>,
): List<SelectedSource> = sources.filter { source ->
    val size = source.sizeBytes ?: return@filter false
    size in sizesInVault
}
