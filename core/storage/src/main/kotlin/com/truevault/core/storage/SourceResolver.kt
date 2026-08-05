package com.truevault.core.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.truevault.core.common.log.SecureLog
import com.truevault.core.model.SelectedSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SourceResolver"

/**
 * The only place in TrueVault that talks to `ContentResolver`.
 *
 * Everything a provider returns is treated as untrusted input: a display name can be null, absurdly
 * long, or contain path separators; a size can be missing or wrong; a MIME type can be absent. Each
 * case is handled here so no other layer has to guess.
 */
@Singleton
class SourceResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val resolver: ContentResolver get() = context.contentResolver

    /**
     * Reads what the provider will tell us about a picked URI.
     *
     * Returns null when the URI cannot be queried at all — the file was deleted between picking and
     * importing, or the grant was already revoked.
     */
    fun describe(uri: Uri, fromPhotoPicker: Boolean): SelectedSource? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

        return try {
            resolver.query(uri, projection, null, null, null).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) {
                    // Some providers answer no query but still open a stream. Report what we know.
                    return SelectedSource(
                        uriToken = uri.toString(),
                        displayName = null,
                        sizeBytes = null,
                        mimeType = resolver.getType(uri),
                        isFromPhotoPicker = fromPhotoPicker,
                    )
                }

                SelectedSource(
                    uriToken = uri.toString(),
                    displayName = cursor.stringOrNull(OpenableColumns.DISPLAY_NAME)?.sanitiseFileName(),
                    sizeBytes = cursor.longOrNull(OpenableColumns.SIZE),
                    mimeType = resolver.getType(uri),
                    isFromPhotoPicker = fromPhotoPicker,
                )
            }
        } catch (e: SecurityException) {
            SecureLog.w(TAG, "URI permission was not granted or has expired")
            null
        } catch (e: IllegalArgumentException) {
            SecureLog.w(TAG, "Provider rejected the query (${e.javaClass.simpleName})")
            null
        } catch (e: Exception) {
            SecureLog.e(TAG, "Provider threw while describing a source", e)
            null
        }
    }

    /**
     * Opens the source for reading.
     *
     * @throws SourceUnavailableException when the file is gone or access was lost. Callers must
     * treat this as "this one file failed", never as a reason to abandon a batch.
     */
    fun openInputStream(uri: Uri): InputStream = try {
        resolver.openInputStream(uri) ?: throw SourceUnavailableException("Provider returned no stream")
    } catch (e: FileNotFoundException) {
        throw SourceUnavailableException("Source no longer exists")
    } catch (e: SecurityException) {
        throw SourceUnavailableException("Access to the source was lost")
    } catch (e: IOException) {
        throw SourceUnavailableException("Source could not be read")
    }

    /**
     * Measures a source by reading it.
     *
     * Used only when the provider reported no size. It costs a full read, so it is never done
     * speculatively — but importing without knowing the size is worse: the container's declared
     * length would be wrong and the file would fail to open afterwards.
     */
    fun measure(uri: Uri): Long? = try {
        openInputStream(uri).use { stream ->
            var total = 0L
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
            }
            total
        }
    } catch (e: SourceUnavailableException) {
        null
    }

    /** Takes a persistable read grant when the provider offers one, so a retry after a restart works. */
    fun takePersistableReadPermission(uri: Uri): Boolean = try {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        // Photo Picker URIs are not persistable. That is expected, not an error.
        false
    }

    fun releasePersistableReadPermission(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            SecureLog.d(TAG, "No persisted grant to release")
        }
    }

    /** Whether the URI still resolves. Used before offering a retry or a deletion. */
    fun stillExists(uri: Uri): Boolean = try {
        resolver.openInputStream(uri)?.use { true } ?: false
    } catch (e: Exception) {
        false
    }

    /** True when the document provider advertises delete support for this URI. */
    fun supportsDelete(uri: Uri): Boolean {
        if (!DocumentsContract.isDocumentUri(context, uri)) return false

        return try {
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)
                .use { cursor ->
                    if (cursor == null || !cursor.moveToFirst()) return false
                    val flags = cursor.getInt(0)
                    flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0
                }
        } catch (e: Exception) {
            false
        }
    }
}

/** The source could not be read. Carries no path, name or URI. */
class SourceUnavailableException(message: String) : IOException(message)

private fun Cursor.stringOrNull(column: String): String? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private fun Cursor.longOrNull(column: String): Long? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}

/**
 * Makes a provider-supplied name safe to store and display.
 *
 * Path separators are stripped so a hostile name can never influence a file path, and the length is
 * capped so a pathological name cannot bloat the database. Unicode is left intact — a Devanagari or
 * emoji file name is perfectly valid and must survive.
 */
internal fun String.sanitiseFileName(): String = this
    .replace('/', '_')
    .replace('\\', '_')
    .filter { it.code >= 0x20 && it.code != 0x7F }
    .trim()
    .take(255)
    .ifEmpty { "file" }
