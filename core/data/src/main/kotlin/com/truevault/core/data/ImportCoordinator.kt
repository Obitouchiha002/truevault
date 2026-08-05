package com.truevault.core.data

import android.content.IntentSender
import androidx.core.net.toUri
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.model.DeletionOutcome
import com.truevault.core.model.SelectedSource
import com.truevault.core.storage.DeletionPlan
import com.truevault.core.storage.OriginalFileDeleter
import com.truevault.core.storage.SourceResolver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * What the import UI is allowed to ask for.
 *
 * Feature modules do not depend on `:core:storage`, so `ContentResolver`, URI grants and the
 * platform's delete APIs are reached only through this class. That boundary is what keeps a screen
 * from acquiring the ability to touch a user's files directly.
 */
sealed interface OriginalDeletionRequest {

    /** The platform will ask the user. The caller launches this and reports the answer back. */
    data class NeedsUserConfirmation(val intentSender: IntentSender) : OriginalDeletionRequest

    /** Already resolved without a dialog — deleted, unsupported, or permission lost. */
    data class Resolved(val outcome: DeletionOutcome) : OriginalDeletionRequest
}

@Singleton
class ImportCoordinator @Inject constructor(
    private val sourceResolver: SourceResolver,
    private val originalFileDeleter: OriginalFileDeleter,
    @param:Dispatcher(TrueVaultDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Turns picked URIs into described sources.
     *
     * A URI that cannot be described at all is dropped here rather than failing the whole batch:
     * one revoked grant among forty photos should cost the user one photo, not the import.
     */
    suspend fun describeSources(
        uriTokens: List<String>,
        fromPhotoPicker: Boolean,
    ): List<SelectedSource> = withContext(ioDispatcher) {
        uriTokens.mapNotNull { token ->
            val uri = runCatching { token.toUri() }.getOrNull() ?: return@mapNotNull null
            // A persistable grant lets a retry survive an app restart. Photo Picker URIs cannot be
            // persisted, which is expected rather than an error.
            if (!fromPhotoPicker) sourceResolver.takePersistableReadPermission(uri)
            sourceResolver.describe(uri, fromPhotoPicker)
        }
    }

    /** Works out how these originals can be removed, if at all. */
    suspend fun planOriginalDeletion(uriTokens: List<String>): OriginalDeletionRequest =
        withContext(ioDispatcher) {
            val uris = uriTokens.mapNotNull { runCatching { it.toUri() }.getOrNull() }

            when (val plan = originalFileDeleter.planFor(uris)) {
                is DeletionPlan.SystemConfirmation ->
                    OriginalDeletionRequest.NeedsUserConfirmation(plan.intentSender)

                is DeletionPlan.DocumentDelete ->
                    OriginalDeletionRequest.Resolved(originalFileDeleter.deleteDocument(plan.uri))

                is DeletionPlan.Unsupported -> OriginalDeletionRequest.Resolved(plan.reason)
            }
        }

    /**
     * Turns the result of the system dialog into an observed outcome.
     *
     * `RESULT_OK` means the user accepted the dialog, not that the files are gone — so the URIs are
     * re-checked. Reporting "Original removed" because a dialog was dismissed is exactly the claim
     * this app must never make.
     */
    suspend fun confirmDeletion(uriTokens: List<String>, approved: Boolean): DeletionOutcome =
        withContext(ioDispatcher) {
            if (!approved) return@withContext DeletionOutcome.USER_CANCELLED
            val uris = uriTokens.mapNotNull { runCatching { it.toUri() }.getOrNull() }
            originalFileDeleter.verifyDeleted(uris)
        }
}
