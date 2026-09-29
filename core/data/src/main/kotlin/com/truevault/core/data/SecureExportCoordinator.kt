package com.truevault.core.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.common.log.SecureLog
import com.truevault.core.common.result.Outcome
import com.truevault.core.common.result.asFailure
import com.truevault.core.common.result.asSuccess
import com.truevault.core.data.model.VaultItem
import com.truevault.core.model.MimeCategory
import com.truevault.core.model.VaultError
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "Export"

/** Folder name used under Pictures/ and Movies/ when a file goes back to the gallery. */
private const val GALLERY_FOLDER = "TrueVault"

/**
 * Writing a secured file back out of the vault — "save a copy" and "unhide".
 *
 * This is the deliberate inverse of import, and it carries the same honesty the share path does: the
 * copy it writes is **no longer protected**. TrueVault cannot follow it, expire it or wipe it once it
 * is a normal file on the device, so the UI says so before this runs.
 *
 * Where a file lands:
 *
 *  - **Photos and videos** go straight back to the system gallery (`Pictures/TrueVault` or
 *    `Movies/TrueVault`) with no folder picker — but only on Android 10+ (API 29), where scoped
 *    storage lets an app add its own media without the broad storage permission TrueVault refuses to
 *    ask for. [canSaveToGallery] reports whether that path is available.
 *  - **Everything else** (and everything on Android 9 and below) is written to a location the user
 *    picks through the system file UI, so no storage permission is ever required.
 *
 * The decryption itself is streamed by [VaultRepository.exportToStream] directly into the
 * destination stream, so no plaintext copy is ever written inside the app.
 */
@Singleton
class SecureExportCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val vaultRepository: VaultRepository,
    @param:Dispatcher(TrueVaultDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Whether [item] can be saved to the gallery with no picker.
     *
     * True only for photos and videos on API 29+. Audio, documents and everything on older Androids
     * go through the file picker instead, so the caller must request a destination for those.
     */
    fun canSaveToGallery(item: VaultItem): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (item.category == MimeCategory.PHOTO || item.category == MimeCategory.VIDEO)

    /**
     * Saves [item] into the system gallery. API 29+ only — guard callers with [canSaveToGallery].
     *
     * The entry is created `IS_PENDING` so a half-written file is never visible, then published once
     * decryption succeeds. Any failure deletes the pending entry, leaving no broken row behind.
     */
    suspend fun saveToGallery(item: VaultItem): Outcome<Unit> = withContext(ioDispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext VaultError.Unknown("Saving to the gallery needs Android 10 or newer.")
                .asFailure()
        }

        val resolver = context.contentResolver
        val isVideo = item.category == MimeCategory.VIDEO
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativeDir = if (isVideo) {
            "${Environment.DIRECTORY_MOVIES}/$GALLERY_FOLDER"
        } else {
            "${Environment.DIRECTORY_PICTURES}/$GALLERY_FOLDER"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(item.displayName))
            item.mimeType?.let { put(MediaStore.MediaColumns.MIME_TYPE, it) }
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            SecureLog.e(TAG, "MediaStore refused a new gallery entry", e)
            null
        } ?: return@withContext writeFailure()

        try {
            val outcome = resolver.openOutputStream(uri).use { stream ->
                if (stream == null) writeFailure() else vaultRepository.exportToStream(item.id, stream)
            }
            when (outcome) {
                is Outcome.Success -> {
                    resolver.update(uri, ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }, null, null)
                    Unit.asSuccess()
                }

                is Outcome.Failure -> {
                    runCatching { resolver.delete(uri, null, null) }
                    outcome
                }
            }
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed while writing a gallery entry", e)
            runCatching { resolver.delete(uri, null, null) }
            writeFailure()
        }
    }

    /**
     * Saves [item] to a single document the user just created through the system file UI
     * (`ACTION_CREATE_DOCUMENT`). Works on every supported API and needs no storage permission.
     */
    suspend fun saveToDocument(item: VaultItem, target: Uri): Outcome<Unit> =
        withContext(ioDispatcher) {
            try {
                context.contentResolver.openOutputStream(target).use { stream ->
                    if (stream == null) {
                        writeFailure()
                    } else {
                        vaultRepository.exportToStream(item.id, stream)
                    }
                }
            } catch (e: Exception) {
                SecureLog.e(TAG, "Failed while writing to a chosen document", e)
                writeFailure()
            }
        }

    /**
     * Creates a file for [item] inside the folder [treeUri] (from `ACTION_OPEN_DOCUMENT_TREE`) and
     * writes it there. Used for batch "save selected" / "unhide selected" of non-gallery files.
     *
     * The provider itself resolves name clashes by appending a suffix, so exporting two files with
     * the same name into one folder does not overwrite either.
     */
    suspend fun saveToFolderChild(item: VaultItem, treeUri: Uri): Outcome<Unit> =
        withContext(ioDispatcher) {
            try {
                val dir = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri),
                )
                val child = DocumentsContract.createDocument(
                    context.contentResolver,
                    dir,
                    item.mimeType ?: "application/octet-stream",
                    safeName(item.displayName),
                ) ?: return@withContext writeFailure()

                context.contentResolver.openOutputStream(child).use { stream ->
                    if (stream == null) {
                        writeFailure()
                    } else {
                        vaultRepository.exportToStream(item.id, stream)
                    }
                }
            } catch (e: Exception) {
                SecureLog.e(TAG, "Failed while writing into the chosen folder", e)
                writeFailure()
            }
        }

    private fun writeFailure(): Outcome<Unit> =
        VaultError.Unknown("This file could not be saved to the chosen location.").asFailure()

    /** Strips path separators so a stored name can never escape the destination folder. */
    private fun safeName(name: String): String =
        name.replace('/', '_').replace('\\', '_').ifBlank { "file" }
}
