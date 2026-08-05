package com.truevault.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.common.log.SecureLog
import com.truevault.core.common.result.Outcome
import com.truevault.core.common.result.asFailure
import com.truevault.core.common.result.asSuccess
import com.truevault.core.model.VaultError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "Share"

/** A share the caller must launch and then release. */
data class SecureShare(
    val intent: Intent,
    val uri: Uri,
    private val plaintextFile: File,
) {
    internal fun file(): File = plaintextFile
}

/**
 * Sharing a secured file out of the vault.
 *
 * How it works, and its limit:
 *
 *  1. The file is decrypted into the app's internal cache — never into shared storage.
 *  2. It is exposed through a `FileProvider` path that covers only that cache directory.
 *  3. The receiving app gets a one-shot read grant, and the grant is revoked when sharing ends.
 *  4. The temporary plaintext is deleted.
 *
 * **Once another app receives the file, TrueVault cannot control its copies.** There is no
 * expiry, no remote wipe and no "view once" — anything claiming otherwise for a normal Android share
 * would be a lie, so the UI states this before the share sheet opens.
 */
@Singleton
class SecureShareCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val vaultRepository: VaultRepository,
    @param:Dispatcher(TrueVaultDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun prepare(vaultItemId: String): Outcome<SecureShare> = withContext(ioDispatcher) {
        val item = vaultRepository.findItem(vaultItemId)
            ?: return@withContext VaultError.SourceNotFound.asFailure()

        when (val materialised = vaultRepository.materialiseForViewing(vaultItemId)) {
            is Outcome.Failure -> materialised
            is Outcome.Success -> {
                val file = materialised.value
                try {
                    val uri = FileProvider.getUriForFile(context, authority(), file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = item.mimeType ?: "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        // No file name is put in the subject: the share sheet preview would show it
                        // to whoever is looking at the screen.
                        clipData = android.content.ClipData.newRawUri("", uri)
                    }
                    SecureShare(intent = intent, uri = uri, plaintextFile = file).asSuccess()
                } catch (e: IllegalArgumentException) {
                    SecureLog.e(TAG, "FileProvider rejected the cached file", e)
                    vaultRepository.discardPlaintext(file)
                    VaultError.Unknown("This file could not be prepared for sharing.").asFailure()
                }
            }
        }
    }

    /** Revokes the grant and removes the temporary plaintext. Safe to call more than once. */
    fun release(share: SecureShare) {
        runCatching {
            context.revokeUriPermission(share.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        vaultRepository.discardPlaintext(share.file())
    }

    private fun authority(): String = "${context.packageName}.fileprovider"
}
