package com.truevault.core.storage

import android.content.Context
import android.os.StatFs
import com.truevault.core.common.log.SecureLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VaultFs"

private const val VAULT_DIR = "vault"
private const val ITEMS_DIR = "items"
private const val THUMBNAILS_DIR = "thumbnails"
private const val TEMP_DIR = "temp"
private const val PLAINTEXT_CACHE_DIR = "tv_plaintext"

/**
 * Owns every path TrueVault writes to.
 *
 * Encrypted content lives in `noBackupFilesDir`, not `filesDir`. Both are app-private, but
 * `noBackupFilesDir` is excluded from Android's automatic backup — and a backup that copies
 * ciphertext without the Keystore key that opens it would produce an archive that looks complete
 * and can never be restored.
 *
 * Nothing is ever written to `Downloads/TrueVault`, `Pictures/.hidden`, `DCIM/.vault` or any other
 * public location, and `.nomedia` is not treated as a security measure anywhere.
 */
@Singleton
class VaultFileSystem @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val vaultRoot: File get() = File(context.noBackupFilesDir, VAULT_DIR)

    val itemsDir: File get() = ensure(File(vaultRoot, ITEMS_DIR))
    val thumbnailsDir: File get() = ensure(File(vaultRoot, THUMBNAILS_DIR))
    val tempDir: File get() = ensure(File(vaultRoot, TEMP_DIR))

    /** Temporary plaintext for the viewer. In the internal cache, never in shared storage. */
    val plaintextCacheDir: File get() = ensure(File(context.cacheDir, PLAINTEXT_CACHE_DIR))

    /**
     * Where a container is written while an import is still in flight.
     *
     * The `.vault.part` suffix is what crash recovery looks for: any file with this name is, by
     * definition, an import that never completed.
     */
    fun partFile(vaultItemId: String): File = File(tempDir, "$vaultItemId.vault.part")

    fun itemFile(vaultItemId: String): File = File(itemsDir, "$vaultItemId.vault")

    fun thumbnailFile(vaultItemId: String): File = File(thumbnailsDir, "$vaultItemId.thumb")

    /**
     * Moves a verified container from temp into the vault.
     *
     * `File.renameTo` within the same filesystem is atomic: the container is either fully present
     * under its final name or not present at all. There is no window in which a half-written file
     * carries the name of a committed vault item — which is what lets the app trust that anything in
     * `items/` is complete.
     */
    fun commit(vaultItemId: String): Boolean {
        val part = partFile(vaultItemId)
        val target = itemFile(vaultItemId)

        if (!part.exists()) {
            SecureLog.e(TAG, "Commit requested for a missing temporary file")
            return false
        }
        if (target.exists()) {
            SecureLog.e(TAG, "Commit target already exists")
            return false
        }

        return part.renameTo(target).also { renamed ->
            if (!renamed) SecureLog.e(TAG, "Atomic rename failed")
        }
    }

    /** Removes the temporary artefacts of one import. Never touches a committed item. */
    fun discardPart(vaultItemId: String) {
        val part = partFile(vaultItemId)
        if (part.exists() && !part.delete()) {
            SecureLog.w(TAG, "Could not delete a temporary import file")
        }
    }

    fun deleteItem(vaultItemId: String): Boolean {
        val thumbnail = thumbnailFile(vaultItemId)
        if (thumbnail.exists()) thumbnail.delete()
        val item = itemFile(vaultItemId)
        return !item.exists() || item.delete()
    }

    /**
     * Temporary files left behind by a crash.
     *
     * Returned rather than deleted, because deciding what is abandoned needs the database: a
     * `.part` file belonging to a transaction that is still running must not be removed.
     */
    fun findOrphanedParts(): List<String> =
        tempDir.listFiles { file -> file.isFile && file.name.endsWith(".vault.part") }
            ?.map { it.name.removeSuffix(".vault.part") }
            .orEmpty()

    /**
     * Clears plaintext the viewer had to materialise.
     *
     * Called at startup as well as on viewer close: if the process was killed while a file was open,
     * the plaintext would otherwise sit in the cache until Android decided to reclaim it.
     */
    fun clearPlaintextCache() {
        plaintextCacheDir.listFiles()?.forEach { file ->
            if (!file.delete()) SecureLog.w(TAG, "Could not clear a cached plaintext file")
        }
    }

    fun freeSpaceBytes(): Long = try {
        val stat = StatFs(context.noBackupFilesDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    } catch (e: IllegalArgumentException) {
        SecureLog.e(TAG, "Could not read free space", e)
        0L
    }

    fun totalVaultBytes(): Long {
        val files = itemsDir.listFiles().orEmpty().toList() +
            thumbnailsDir.listFiles().orEmpty().toList()
        return files.filter(File::isFile).sumOf(File::length)
    }

    private fun ensure(dir: File): File {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Could not create a vault directory")
        }
        return dir
    }
}

/**
 * How much room an import really needs.
 *
 * The encrypted container is slightly larger than the source (one nonce and one tag per chunk), a
 * thumbnail may be written, and the temporary and final copies briefly coexist during the rename.
 * A safety buffer on top means the device is not driven to genuinely zero free space, which makes
 * every other app on the phone misbehave.
 */
object StorageEstimate {

    private const val NONCE_AND_TAG_PER_CHUNK = 12 + 16
    private const val THUMBNAIL_ALLOWANCE_BYTES = 512L * 1024L
    private const val SAFETY_BUFFER_BYTES = 32L * 1024L * 1024L

    fun requiredBytes(sourceBytes: Long, chunkSize: Int, includesThumbnail: Boolean): Long {
        val chunks = if (sourceBytes == 0L) 1L else (sourceBytes + chunkSize - 1) / chunkSize
        val encrypted = sourceBytes + chunks * NONCE_AND_TAG_PER_CHUNK + HEADER_ALLOWANCE_BYTES
        val thumbnail = if (includesThumbnail) THUMBNAIL_ALLOWANCE_BYTES else 0L
        return encrypted + thumbnail + SAFETY_BUFFER_BYTES
    }

    fun requiredBytesForBatch(
        sourceBytes: List<Long>,
        chunkSize: Int,
        thumbnailCount: Int,
    ): Long {
        val encrypted = sourceBytes.sumOf { size ->
            val chunks = if (size == 0L) 1L else (size + chunkSize - 1) / chunkSize
            size + chunks * NONCE_AND_TAG_PER_CHUNK + HEADER_ALLOWANCE_BYTES
        }
        return encrypted + thumbnailCount * THUMBNAIL_ALLOWANCE_BYTES + SAFETY_BUFFER_BYTES
    }

    private const val HEADER_ALLOWANCE_BYTES = 512L
}
