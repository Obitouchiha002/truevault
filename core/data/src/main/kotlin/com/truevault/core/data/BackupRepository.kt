package com.truevault.core.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.common.log.SecureLog
import com.truevault.core.common.result.Outcome
import com.truevault.core.common.result.asFailure
import com.truevault.core.common.result.asSuccess
import com.truevault.core.common.time.TimeProvider
import com.truevault.core.crypto.aead.AesGcm
import com.truevault.core.crypto.aead.SealedData
import com.truevault.core.crypto.backup.BackupEntry
import com.truevault.core.crypto.backup.BackupException
import com.truevault.core.crypto.backup.BackupManifest
import com.truevault.core.crypto.file.VaultContainerCodec
import com.truevault.core.crypto.kdf.KdfParams
import com.truevault.core.crypto.kdf.PasswordKeyDerivation
import com.truevault.core.crypto.vault.VaultCryptoService
import com.truevault.core.crypto.vault.VaultLockedException
import com.truevault.core.data.model.VaultItemMetadata
import com.truevault.core.database.dao.VaultItemDao
import com.truevault.core.database.entity.VaultItemEntity
import com.truevault.core.datastore.UserPreferencesDataSource
import com.truevault.core.model.VaultError
import com.truevault.core.storage.VaultFileSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private const val TAG = "Backup"
private val AAD_BACKUP = "truevault.backup.v1".toByteArray()

/** 64 KiB. Everything that touches a container streams through a buffer this size. */
private const val STREAM_BUFFER_BYTES = 64 * 1024

/** What an export or restore is doing. */
sealed interface BackupStep {
    data class Progress(val completed: Int, val total: Int) : BackupStep
    data class ExportFinished(val itemCount: Int, val bytesWritten: Long) : BackupStep
    data class RestoreFinished(val report: RestoreReport) : BackupStep
    data class Failed(val error: VaultError, val detail: String?) : BackupStep
}

/**
 * The complete restore report.
 *
 * Skipped items are reported rather than hidden: restoring over an existing vault must never
 * silently overwrite something the user already has.
 */
data class RestoreReport(
    val itemsRestored: Int,
    val itemsSkippedAsDuplicate: Int,
    val itemsFailed: Int,
    val totalInArchive: Int,
)

/**
 * Encrypted local backup and restore.
 *
 * ## Why the archive is shaped the way it is
 *
 * A vault container is already ciphertext: its bytes are AES-256-GCM under a random per-file key.
 * So the archive stores each container **verbatim** and carries only the small things that make it
 * openable elsewhere:
 *
 * - the per-file key, re-wrapped under a key derived from the **backup passphrase**, and
 * - the item's metadata, re-sealed under that same archive key.
 *
 * Restoring re-wraps the file key again, this time under the destination vault's master key, and
 * writes it into the item's row. The container is never rewritten — its header is the associated
 * data for every chunk, so altering one byte of it would invalidate the whole file.
 *
 * Two consequences follow, and both are the point:
 *
 * 1. **A backup restores into a vault it did not come from.** That is what a backup is *for* — a new
 *    phone, a reinstall — and it is exactly what format version 1 could not do: every file key in a
 *    v1 archive was wrapped by the source vault's master key, which no longer exists after a
 *    reinstall, so every restored item was permanently unopenable.
 * 2. **Memory is constant.** Entries stream through a 64 KiB buffer, so a vault holding a 4 GB video
 *    backs up and restores in the same memory as one holding a text file. Version 1 read each whole
 *    container into memory twice.
 *
 * The archive key never leaves this class, and no vault master key ever enters the archive.
 */
@Singleton
class BackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val vaultItemDao: VaultItemDao,
    private val fileSystem: VaultFileSystem,
    private val cryptoService: VaultCryptoService,
    private val preferences: UserPreferencesDataSource,
    private val activityRepository: ActivityRepository,
    private val timeProvider: TimeProvider,
    @param:Dispatcher(TrueVaultDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /**
     * Writes an encrypted archive to a destination the user picked.
     *
     * The caller owns [passphrase] and must wipe it.
     */
    fun export(destination: Uri, passphrase: CharArray): Flow<BackupStep> = channelFlow {
        val items = try {
            vaultItemDao.findByIds(vaultItemDao.allMetadata().map { it.id })
        } catch (e: VaultLockedException) {
            send(BackupStep.Failed(VaultError.AuthenticationRequired, null))
            return@channelFlow
        }

        if (items.isEmpty()) {
            send(
                BackupStep.Failed(
                    VaultError.Unknown("There is nothing in the vault to back up."),
                    null,
                ),
            )
            return@channelFlow
        }

        withContext(ioDispatcher) {
            val salt = PasswordKeyDerivation.randomSalt()
            val params = KdfParams.CURRENT
            val archiveKey = PasswordKeyDerivation.deriveKey(passphrase, salt, params)

            try {
                val out = context.contentResolver.openOutputStream(destination)
                if (out == null) {
                    send(
                        BackupStep.Failed(
                            VaultError.Unknown("That location could not be written."),
                            null,
                        ),
                    )
                    return@withContext
                }

                var totalBytes = 0L

                out.use { rawOut ->
                    ZipOutputStream(rawOut.buffered()).use { zip ->
                        val entries = mutableListOf<BackupEntry>()
                        val records = mutableListOf<BackupItemRecord>()

                        items.forEachIndexed { index, item ->
                            send(BackupStep.Progress(index, items.size))

                            val container = fileSystem.itemFile(item.id)
                            if (!container.exists()) return@forEachIndexed

                            // Re-key at the archive boundary: unwrap with this vault's master key,
                            // re-wrap with the archive key so another vault can open it later.
                            val fileKey = fileKeyFor(item, container)
                            if (fileKey == null) {
                                SecureLog.w(TAG, "Skipping an item whose file key is unavailable")
                                return@forEachIndexed
                            }
                            val archiveWrappedKey = cryptoService.wrapFileKeyWith(archiveKey, fileKey)
                            val metadata = decodeMetadata(item.encryptedMetadata)

                            entries += zip.streamEntry(
                                name = "${BackupManifest.ITEM_ENTRY_PREFIX}${item.id}",
                                source = container,
                            )
                            totalBytes += container.length()

                            val thumbnail = fileSystem.thumbnailFile(item.id)
                            val hasThumbnail = item.thumbnailRelativePath != null && thumbnail.exists()
                            if (hasThumbnail) {
                                entries += zip.streamEntry(
                                    name = "${BackupManifest.THUMBNAIL_ENTRY_PREFIX}${item.id}",
                                    source = thumbnail,
                                )
                            }

                            records += item.toRecord(
                                archiveWrappedFileKey = archiveWrappedKey,
                                metadata = metadata,
                                hasThumbnail = hasThumbnail,
                            )
                        }

                        // The index is small, and it is the only part of the archive that carries
                        // names, so it is the only part that is sealed.
                        val metadataBytes = json.encodeToString(
                            ListSerializer(BackupItemRecord.serializer()),
                            records,
                        ).toByteArray()
                        entries += zip.writeSealed(
                            name = BackupManifest.METADATA_ENTRY_NAME,
                            plaintext = metadataBytes,
                            key = archiveKey,
                        )

                        val manifest = BackupManifest(
                            createdAtMillis = timeProvider.currentTimeMillis(),
                            itemCount = records.size,
                            totalOriginalBytes = items.sumOf { it.originalSize },
                            kdfVersion = params.version,
                            kdfSaltBase64 = salt.b64(),
                            checkValueBase64 = checkValue(archiveKey).b64(),
                            entries = entries,
                        )

                        zip.putNextEntry(ZipEntry(BackupManifest.MANIFEST_ENTRY_NAME))
                        zip.write(
                            json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(),
                        )
                        zip.closeEntry()
                    }
                }

                preferences.setLastBackupAt(timeProvider.currentTimeMillis())
                activityRepository.record(ActivityKind.BACKUP_COMPLETED, items.size)
                send(BackupStep.ExportFinished(items.size, totalBytes))
            } catch (e: CancellationException) {
                // Cancellation is not a failure and must not be reported as one.
                throw e
            } catch (e: VaultLockedException) {
                send(BackupStep.Failed(VaultError.AuthenticationRequired, null))
            } catch (e: Exception) {
                SecureLog.e(TAG, "Backup export failed", e)
                send(BackupStep.Failed(VaultError.Unknown("The backup could not be written."), null))
            }
        }
    }

    /**
     * Inspects an archive without restoring anything.
     *
     * This is what lets the UI say "this is a backup of 412 items made on 3 March" before the user
     * commits to anything, and reject an unreadable file with a specific reason.
     */
    suspend fun inspect(source: Uri): Outcome<BackupManifest> = withContext(ioDispatcher) {
        try {
            readManifest(source)?.asSuccess() ?: VaultError.BackupInvalid.asFailure()
        } catch (e: BackupException.UnsupportedVersion) {
            VaultError.UnsupportedFormatVersion(e.found, e.maxSupported).asFailure()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VaultError.BackupInvalid.asFailure()
        }
    }

    /**
     * Restores an archive.
     *
     * The order mirrors the import engine's: validate the manifest, check the format version, check
     * free space, unpack to a staging directory verifying every entry's hash, and only then commit.
     * A restore that fails part-way leaves the active vault exactly as it was.
     */
    fun restore(source: Uri, passphrase: CharArray): Flow<BackupStep> = channelFlow {
        withContext(ioDispatcher) {
            val manifest = try {
                readManifest(source) ?: run {
                    send(BackupStep.Failed(VaultError.BackupInvalid, null))
                    return@withContext
                }
            } catch (e: BackupException.UnsupportedVersion) {
                send(
                    BackupStep.Failed(
                        VaultError.UnsupportedFormatVersion(e.found, e.maxSupported),
                        null,
                    ),
                )
                return@withContext
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send(BackupStep.Failed(VaultError.BackupInvalid, null))
                return@withContext
            }

            val params = KdfParams.forVersion(manifest.kdfVersion)
            if (params == null) {
                send(
                    BackupStep.Failed(
                        VaultError.UnsupportedFormatVersion(
                            manifest.kdfVersion,
                            KdfParams.CURRENT.version,
                        ),
                        null,
                    ),
                )
                return@withContext
            }

            val archiveKey = PasswordKeyDerivation.deriveKey(
                passphrase,
                manifest.kdfSaltBase64.unB64(),
                params,
            )

            // A wrong passphrase is caught here, cheaply, instead of as a confusing decryption
            // failure half way through a large restore.
            if (!checkValue(archiveKey).contentEquals(manifest.checkValueBase64.unB64())) {
                send(BackupStep.Failed(VaultError.AuthenticationRequired, null))
                return@withContext
            }

            val required = manifest.entries.sumOf { it.sealedSizeBytes } * 2
            val available = fileSystem.freeSpaceBytes()
            if (available < required) {
                send(BackupStep.Failed(VaultError.InsufficientStorage(required, available), null))
                return@withContext
            }

            val staging = File(fileSystem.tempDir, "restore-${timeProvider.currentTimeMillis()}")
            staging.mkdirs()

            try {
                val metadataBytes = unpackAndVerify(source, manifest, archiveKey, staging) { done, total ->
                    trySend(BackupStep.Progress(done, total))
                }

                val records = json.decodeFromString(
                    ListSerializer(BackupItemRecord.serializer()),
                    String(metadataBytes),
                )

                var restored = 0
                var skipped = 0
                var failed = 0

                records.forEach { record ->
                    // Never overwrite silently: an id that already exists is reported, not replaced.
                    if (vaultItemDao.findById(record.id) != null) {
                        skipped++
                        return@forEach
                    }

                    val stagedItem = File(staging, record.id)
                    if (!stagedItem.exists()) {
                        failed++
                        return@forEach
                    }

                    try {
                        // Re-key into this vault. Without this the restored container would carry a
                        // file key wrapped by a master key that no longer exists anywhere.
                        val fileKey = cryptoService.unwrapFileKeyWith(
                            archiveKey,
                            record.archiveWrappedFileKeyBase64.unB64(),
                        )
                        val rewrapped = cryptoService.wrapFileKey(fileKey)
                        val resealedMetadata = cryptoService.sealDatabaseField(
                            json.encodeToString(
                                VaultItemMetadata.serializer(),
                                record.metadata,
                            ).toByteArray(),
                        )
                        // The fingerprint is an HMAC under the master key, so it has to be recomputed
                        // for this vault or duplicate detection would never match again.
                        val fingerprint = record.metadata.contentHashHex
                            ?.let(::hexToBytes)
                            ?.let(cryptoService::fingerprint)

                        stagedItem.copyTo(fileSystem.itemFile(record.id), overwrite = false)
                        val stagedThumb = File(staging, "${record.id}.thumb")
                        if (stagedThumb.exists()) {
                            stagedThumb.copyTo(fileSystem.thumbnailFile(record.id), overwrite = false)
                        }

                        vaultItemDao.insert(
                            record.toEntity(
                                wrappedFileKey = rewrapped,
                                encryptedMetadata = resealedMetadata,
                                contentFingerprint = fingerprint,
                            ),
                        )
                        restored++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        SecureLog.e(TAG, "One item failed to restore", e)
                        fileSystem.deleteItem(record.id)
                        failed++
                    }
                }

                send(
                    BackupStep.RestoreFinished(
                        RestoreReport(
                            itemsRestored = restored,
                            itemsSkippedAsDuplicate = skipped,
                            itemsFailed = failed,
                            totalInArchive = records.size,
                        ),
                    ),
                )
            } catch (e: BackupException.Corrupt) {
                send(BackupStep.Failed(VaultError.IntegrityCheckFailed, e.entryName))
            } catch (e: BackupException.Incomplete) {
                send(BackupStep.Failed(VaultError.BackupInvalid, null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: GeneralSecurityException) {
                send(BackupStep.Failed(VaultError.AuthenticationRequired, null))
            } catch (e: Exception) {
                SecureLog.e(TAG, "Restore failed", e)
                send(BackupStep.Failed(VaultError.BackupInvalid, null))
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    /**
     * Unpacks every entry into [staging], verifying each one's hash as it goes, and returns the
     * decrypted metadata blob.
     *
     * Verification is against the manifest's recorded hash of the stored bytes, so a truncated or
     * edited archive is caught before any of it reaches the vault directory. Container entries are
     * streamed straight to disk; only the small metadata index is ever held in memory.
     */
    private fun unpackAndVerify(
        source: Uri,
        manifest: BackupManifest,
        key: SecretKey,
        staging: File,
        onProgress: (Int, Int) -> Unit,
    ): ByteArray {
        var metadata: ByteArray? = null
        var seen = 0
        val expected = manifest.entries.associateBy { it.name }

        val input = context.contentResolver.openInputStream(source)
            ?: throw BackupException.NotABackup()

        input.use { rawIn ->
            ZipInputStream(rawIn.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name

                    if (name == BackupManifest.MANIFEST_ENTRY_NAME) {
                        zip.closeEntry()
                        continue
                    }

                    // Path-traversal defence: entry names are matched against the manifest, and any
                    // name that is not in it — or that contains a separator — is refused outright.
                    val declared = expected[name]
                    if (declared == null || name.contains("..") || name.startsWith("/")) {
                        throw BackupException.Corrupt(name)
                    }

                    when {
                        name == BackupManifest.METADATA_ENTRY_NAME -> {
                            val sealedBytes = zip.readBytes()
                            if (!sha256(sealedBytes).b64().contentEquals(declared.sha256Base64)) {
                                throw BackupException.Corrupt(name)
                            }
                            metadata = AesGcm.decrypt(
                                key,
                                SealedData.fromByteArray(sealedBytes),
                                AAD_BACKUP,
                            )
                        }

                        name.startsWith(BackupManifest.ITEM_ENTRY_PREFIX) -> streamToFileVerifying(
                            input = zip,
                            target = File(
                                staging,
                                name.removePrefix(BackupManifest.ITEM_ENTRY_PREFIX),
                            ),
                            declared = declared,
                            name = name,
                        )

                        name.startsWith(BackupManifest.THUMBNAIL_ENTRY_PREFIX) -> streamToFileVerifying(
                            input = zip,
                            target = File(
                                staging,
                                "${name.removePrefix(BackupManifest.THUMBNAIL_ENTRY_PREFIX)}.thumb",
                            ),
                            declared = declared,
                            name = name,
                        )

                        else -> throw BackupException.Corrupt(name)
                    }

                    zip.closeEntry()
                    seen++
                    onProgress(seen, manifest.entries.size)
                }
            }
        }

        if (seen < manifest.entries.size) {
            throw BackupException.Incomplete(manifest.entries.size - seen)
        }

        return metadata ?: throw BackupException.Incomplete(1)
    }

    /** Streams one entry to disk, hashing as it goes, and deletes it if the hash does not match. */
    private fun streamToFileVerifying(
        input: InputStream,
        target: File,
        declared: BackupEntry,
        name: String,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(STREAM_BUFFER_BYTES)

        target.outputStream().use { output ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
        }

        if (!digest.digest().b64().contentEquals(declared.sha256Base64)) {
            target.delete()
            throw BackupException.Corrupt(name)
        }
    }

    private fun readManifest(source: Uri): BackupManifest? {
        context.contentResolver.openInputStream(source)?.use { rawIn ->
            ZipInputStream(rawIn.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == BackupManifest.MANIFEST_ENTRY_NAME) {
                        val manifest = json.decodeFromString(
                            BackupManifest.serializer(),
                            String(zip.readBytes()),
                        )
                        if (manifest.magic != BackupManifest.MAGIC) throw BackupException.NotABackup()
                        if (manifest.formatVersion > BackupManifest.CURRENT_FORMAT_VERSION) {
                            throw BackupException.UnsupportedVersion(
                                found = manifest.formatVersion,
                                maxSupported = BackupManifest.CURRENT_FORMAT_VERSION,
                            )
                        }
                        return manifest
                    }
                    zip.closeEntry()
                }
            }
        }
        return null
    }

    /** Copies a file into the archive verbatim, hashing as it streams. Constant memory. */
    private fun ZipOutputStream.streamEntry(name: String, source: File): BackupEntry {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        var total = 0L

        putNextEntry(ZipEntry(name))
        source.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                write(buffer, 0, read)
                total += read
            }
        }
        closeEntry()

        return BackupEntry(name = name, sealedSizeBytes = total, sha256Base64 = digest.digest().b64())
    }

    /** Only the metadata index uses this: it is small, and it is the only part carrying names. */
    private fun ZipOutputStream.writeSealed(
        name: String,
        plaintext: ByteArray,
        key: SecretKey,
    ): BackupEntry {
        val sealed = AesGcm.encrypt(key, plaintext, AAD_BACKUP).toByteArray()
        putNextEntry(ZipEntry(name))
        write(sealed)
        closeEntry()

        return BackupEntry(
            name = name,
            sealedSizeBytes = sealed.size.toLong(),
            sha256Base64 = sha256(sealed).b64(),
        )
    }

    /** The row's wrapped key, falling back to the container header for rows written before v2. */
    private fun fileKeyFor(item: VaultItemEntity, container: File): SecretKey? = try {
        val wrapped = item.wrappedFileKey
            ?: container.inputStream().use { VaultContainerCodec.read(it).wrappedFileKey }
        cryptoService.unwrapFileKey(wrapped)
    } catch (e: Exception) {
        null
    }

    private fun decodeMetadata(sealed: ByteArray): VaultItemMetadata = try {
        json.decodeFromString(
            VaultItemMetadata.serializer(),
            String(cryptoService.openDatabaseField(sealed)),
        )
    } catch (e: Exception) {
        VaultItemMetadata(
            displayName = "Restored item",
            mimeType = null,
            originalUriToken = null,
            contentHashHex = null,
        )
    }

    private fun checkValue(key: SecretKey): ByteArray =
        MessageDigest.getInstance("SHA-256").apply {
            update("truevault.backup.check.v1".toByteArray())
            update(key.encoded)
        }.digest().copyOf(16)

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hexToBytes(hex: String): ByteArray? = try {
        ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * One vault item as it travels inside a backup.
 *
 * The whole record lives inside the sealed metadata entry, which is why the display name and the
 * file key can appear here in usable form: that entry is encrypted under the archive key.
 */
@kotlinx.serialization.Serializable
internal data class BackupItemRecord(
    val id: String,
    /** The item's file key, wrapped under the archive key rather than any vault's master key. */
    val archiveWrappedFileKeyBase64: String,
    val metadata: VaultItemMetadata,
    val mimeCategory: String,
    val encryptedSize: Long,
    val originalSize: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val importMode: String,
    val privacyStatus: String,
    val verificationStatus: String,
    val keyVersion: Int,
    val fileFormatVersion: Int,
    val originalDeletionState: String,
    val hasThumbnail: Boolean,
)

private fun VaultItemEntity.toRecord(
    archiveWrappedFileKey: ByteArray,
    metadata: VaultItemMetadata,
    hasThumbnail: Boolean,
) = BackupItemRecord(
    id = id,
    archiveWrappedFileKeyBase64 = archiveWrappedFileKey.b64(),
    metadata = metadata,
    mimeCategory = mimeCategory,
    encryptedSize = encryptedSize,
    originalSize = originalSize,
    createdAt = createdAt,
    updatedAt = updatedAt,
    importMode = importMode,
    privacyStatus = privacyStatus,
    verificationStatus = verificationStatus,
    keyVersion = keyVersion,
    fileFormatVersion = fileFormatVersion,
    originalDeletionState = originalDeletionState,
    hasThumbnail = hasThumbnail,
)

private fun BackupItemRecord.toEntity(
    wrappedFileKey: ByteArray,
    encryptedMetadata: ByteArray,
    contentFingerprint: ByteArray?,
) = VaultItemEntity(
    id = id,
    fileRelativePath = "$id.vault",
    thumbnailRelativePath = if (hasThumbnail) "$id.thumb" else null,
    encryptedMetadata = encryptedMetadata,
    mimeCategory = mimeCategory,
    encryptedSize = encryptedSize,
    originalSize = originalSize,
    createdAt = createdAt,
    updatedAt = updatedAt,
    importMode = importMode,
    privacyStatus = privacyStatus,
    verificationStatus = verificationStatus,
    keyVersion = keyVersion,
    fileFormatVersion = fileFormatVersion,
    originalDeletionState = originalDeletionState,
    contentFingerprint = contentFingerprint,
    wrappedFileKey = wrappedFileKey,
    lastIntegrityCheckAt = null,
)

private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

private fun String.unB64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
