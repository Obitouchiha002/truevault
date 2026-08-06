package com.truevault.core.data.model

import com.truevault.core.model.ImportMode
import com.truevault.core.model.MimeCategory
import com.truevault.core.model.OriginalDeletionState
import com.truevault.core.model.PrivacyStatus
import com.truevault.core.model.VerificationStatus
import kotlinx.serialization.Serializable

/**
 * The sensitive part of a vault item, sealed before it reaches the database.
 *
 * Serialised as JSON and encrypted with the vault master key. The file name alone can reveal what a
 * file is — "divorce-papers.pdf", "passport.jpg" — so it never touches a plaintext column.
 */
@Serializable
data class VaultItemMetadata(
    val displayName: String,
    val mimeType: String?,
    /** Kept only while a deletion or retry workflow may still need it; cleared afterwards. */
    val originalUriToken: String?,
    val contentHashHex: String?,
)

/** A vault item as the rest of the app sees it: metadata already decrypted. */
data class VaultItem(
    val id: String,
    val displayName: String,
    val mimeType: String?,
    val category: MimeCategory,
    val originalSizeBytes: Long,
    val encryptedSizeBytes: Long,
    val createdAtMillis: Long,
    /** When this item was moved to the trash, or null while it is in the vault. */
    val trashedAtMillis: Long? = null,
    val updatedAtMillis: Long,
    val importMode: ImportMode,
    val privacyStatus: PrivacyStatus,
    val verificationStatus: VerificationStatus,
    val originalDeletionState: OriginalDeletionState,
    val hasThumbnail: Boolean,
    val originalUriToken: String?,
    val lastIntegrityCheckAtMillis: Long?,
) {
    /** Safe for logs and error messages: no name, no URI. */
    fun describeSafely(): String = "vaultItem(id=$id, category=$category, status=$privacyStatus)"
}
