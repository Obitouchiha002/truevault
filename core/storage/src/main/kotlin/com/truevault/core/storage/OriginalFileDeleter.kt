package com.truevault.core.storage

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.truevault.core.common.log.SecureLog
import com.truevault.core.model.DeletionOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Deletion"

/**
 * How the original file can be removed on this device, for this URI.
 *
 * TrueVault cannot delete a picked file on its own, and it should not be able to. Every path below
 * ends either in the platform's own confirmation dialog or in a provider that has explicitly
 * advertised delete support.
 */
sealed interface DeletionPlan {

    /** Android will show its own confirmation dialog. The user can decline. */
    data class SystemConfirmation(val intentSender: IntentSender) : DeletionPlan

    /** A document provider that advertises `FLAG_SUPPORTS_DELETE`. */
    data class DocumentDelete(val uri: Uri) : DeletionPlan

    /** Nothing can be done: read-only provider, cloud document, or an unsupported platform path. */
    data class Unsupported(val reason: DeletionOutcome) : DeletionPlan
}

/**
 * Removes original files, only ever with the platform's cooperation.
 *
 * The rule this class exists to enforce: **the result is observed, never assumed.** Every method
 * returns what actually happened, and "the user declined" is a first-class outcome rather than an
 * error to be retried or hidden.
 */
@Singleton
class OriginalFileDeleter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sourceResolver: SourceResolver,
) {

    /**
     * Works out how (or whether) these originals can be removed.
     *
     * MediaStore items on API 30+ are batched into one system dialog, which is both the supported
     * API and much better for a user deleting forty photos than forty consecutive prompts.
     */
    fun planFor(uris: List<Uri>): DeletionPlan {
        if (uris.isEmpty()) return DeletionPlan.Unsupported(DeletionOutcome.ALREADY_MISSING)

        val mediaStoreUris = uris.filter { it.isMediaStoreUri() }

        if (mediaStoreUris.size == uris.size && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                DeletionPlan.SystemConfirmation(
                    MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris).intentSender,
                )
            } catch (e: Exception) {
                SecureLog.e(TAG, "MediaStore refused a delete request", e)
                DeletionPlan.Unsupported(DeletionOutcome.FAILED)
            }
        }

        if (uris.size == 1) {
            val uri = uris.first()
            if (sourceResolver.supportsDelete(uri)) return DeletionPlan.DocumentDelete(uri)

            if (uri.isMediaStoreUri() && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                // On Android 10 a delete that needs the user's consent surfaces as a recoverable
                // exception carrying the confirmation intent.
                return try {
                    context.contentResolver.delete(uri, null, null)
                    DeletionPlan.Unsupported(DeletionOutcome.DELETED)
                } catch (e: RecoverableSecurityException) {
                    DeletionPlan.SystemConfirmation(e.userAction.actionIntent.intentSender)
                } catch (e: SecurityException) {
                    DeletionPlan.Unsupported(DeletionOutcome.PERMISSION_LOST)
                } catch (e: Exception) {
                    DeletionPlan.Unsupported(DeletionOutcome.FAILED)
                }
            }
        }

        // Below API 29, deleting MediaStore content needs a broad storage permission that TrueVault
        // deliberately never requests. Saying so is more honest than asking for all-files access.
        return DeletionPlan.Unsupported(DeletionOutcome.PROVIDER_NOT_SUPPORTED)
    }

    /** Executes a provider delete. Only called for [DeletionPlan.DocumentDelete]. */
    fun deleteDocument(uri: Uri): DeletionOutcome = try {
        val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
        if (deleted) DeletionOutcome.DELETED else DeletionOutcome.FAILED
    } catch (e: SecurityException) {
        SecureLog.w(TAG, "Lost permission before deleting an original")
        DeletionOutcome.PERMISSION_LOST
    } catch (e: UnsupportedOperationException) {
        DeletionOutcome.PROVIDER_NOT_SUPPORTED
    } catch (e: IllegalArgumentException) {
        // The provider no longer knows this document; the file is already gone.
        DeletionOutcome.ALREADY_MISSING
    } catch (e: Exception) {
        SecureLog.e(TAG, "Provider threw while deleting an original", e)
        DeletionOutcome.FAILED
    }

    /**
     * Confirms what a system delete request actually did.
     *
     * A returned `RESULT_OK` means the user approved the dialog, not that every file vanished — so
     * the URIs are re-checked. This is the difference between reporting "Original removed" because
     * it is true and reporting it because a dialog was dismissed.
     */
    fun verifyDeleted(uris: List<Uri>): DeletionOutcome {
        val remaining = uris.filter { sourceResolver.stillExists(it) }
        return when {
            remaining.isEmpty() -> DeletionOutcome.DELETED
            remaining.size == uris.size -> DeletionOutcome.FAILED
            // A partial result is reported as a failure for the ones that survived, never as
            // success for the batch.
            else -> DeletionOutcome.FAILED
        }
    }

    private fun Uri.isMediaStoreUri(): Boolean =
        authority == MediaStore.AUTHORITY && runCatching { ContentUris.parseId(this) }.isSuccess
}
