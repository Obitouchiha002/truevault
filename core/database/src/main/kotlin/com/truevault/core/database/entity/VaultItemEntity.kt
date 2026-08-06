package com.truevault.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One secured file.
 *
 * What is plaintext here is deliberate, and so is what is not:
 *
 *  - **Plaintext:** the opaque item id, sizes, timestamps, category and status columns. None of them
 *    identify content, and all of them are needed to sort, filter and page in SQL rather than by
 *    decrypting the whole vault.
 *  - **Encrypted:** the display name, MIME type, and the original URI, all inside
 *    [encryptedMetadata]. A file name alone can be the whole secret.
 *  - **Keyed fingerprint, not a plain hash:** [contentFingerprint] is an HMAC of the file's SHA-256
 *    under a key derived from the vault master key. Equality still works, which is all duplicate
 *    detection needs, but the value cannot be checked against a rainbow table of known files.
 */
@Entity(
    tableName = "vault_items",
    indices = [
        Index("created_at"),
        Index("mime_category"),
        Index("privacy_status"),
        Index("content_fingerprint"),
        Index("original_size"),
    ],
)
data class VaultItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** Relative to the vault items directory. Never an absolute path. */
    @ColumnInfo(name = "file_relative_path")
    val fileRelativePath: String,

    @ColumnInfo(name = "thumbnail_relative_path")
    val thumbnailRelativePath: String?,

    /** Sealed blob: display name, MIME type, original URI token. */
    @ColumnInfo(name = "encrypted_metadata", typeAffinity = ColumnInfo.BLOB)
    val encryptedMetadata: ByteArray,

    @ColumnInfo(name = "mime_category")
    val mimeCategory: String,

    @ColumnInfo(name = "encrypted_size")
    val encryptedSize: Long,

    @ColumnInfo(name = "original_size")
    val originalSize: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "import_mode")
    val importMode: String,

    @ColumnInfo(name = "privacy_status")
    val privacyStatus: String,

    @ColumnInfo(name = "verification_status")
    val verificationStatus: String,

    /** Which master-key generation wrapped this item's file key. */
    @ColumnInfo(name = "key_version")
    val keyVersion: Int,

    @ColumnInfo(name = "file_format_version")
    val fileFormatVersion: Int,

    @ColumnInfo(name = "original_deletion_state")
    val originalDeletionState: String,

    @ColumnInfo(name = "content_fingerprint", typeAffinity = ColumnInfo.BLOB)
    val contentFingerprint: ByteArray?,

    /**
     * The item's file key, wrapped by the vault master key **of this vault**.
     *
     * The container header carries a copy too, but that copy was wrapped by whichever master key
     * existed when the file was written. After a restore into a different vault the header's copy
     * is unusable, so the authoritative wrapped key lives here, where a restore can re-wrap it.
     * Null only for rows written before schema version 2, which fall back to the header.
     */
    @ColumnInfo(name = "wrapped_file_key", typeAffinity = ColumnInfo.BLOB)
    val wrappedFileKey: ByteArray?,

    @ColumnInfo(name = "last_integrity_check_at")
    val lastIntegrityCheckAt: Long?,
) {
    override fun equals(other: Any?): Boolean = other is VaultItemEntity &&
        id == other.id &&
        fileRelativePath == other.fileRelativePath &&
        thumbnailRelativePath == other.thumbnailRelativePath &&
        encryptedMetadata.contentEquals(other.encryptedMetadata) &&
        mimeCategory == other.mimeCategory &&
        encryptedSize == other.encryptedSize &&
        originalSize == other.originalSize &&
        createdAt == other.createdAt &&
        updatedAt == other.updatedAt &&
        importMode == other.importMode &&
        privacyStatus == other.privacyStatus &&
        verificationStatus == other.verificationStatus &&
        keyVersion == other.keyVersion &&
        fileFormatVersion == other.fileFormatVersion &&
        originalDeletionState == other.originalDeletionState &&
        contentFingerprint.contentEqualsOrNull(other.contentFingerprint) &&
        wrappedFileKey.contentEqualsOrNull(other.wrappedFileKey) &&
        lastIntegrityCheckAt == other.lastIntegrityCheckAt

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + encryptedMetadata.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (contentFingerprint?.contentHashCode() ?: 0)
        return result
    }

    /** Never prints metadata or the fingerprint. */
    override fun toString(): String =
        "VaultItemEntity(id=$id, category=$mimeCategory, status=$privacyStatus)"
}

internal fun ByteArray?.contentEqualsOrNull(other: ByteArray?): Boolean = when {
    this == null && other == null -> true
    this == null || other == null -> false
    else -> contentEquals(other)
}
