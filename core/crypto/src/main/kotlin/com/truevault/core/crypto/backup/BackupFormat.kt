package com.truevault.core.crypto.backup

import kotlinx.serialization.Serializable

/**
 * The manifest at the head of every backup archive.
 *
 * A backup is a ZIP whose first entry is this manifest in plaintext, followed by encrypted blobs.
 * The manifest is readable without any key on purpose: the app must be able to say "this archive
 * was made by a newer TrueVault" or "this is not a TrueVault backup" *before* asking the user for a
 * password, rather than after a failed decryption that looks identical to a wrong password.
 *
 * Nothing in the manifest identifies content. It carries counts, sizes, versions and a salt.
 */
@Serializable
data class BackupManifest(
    val magic: String = MAGIC,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val createdAtMillis: Long,
    val itemCount: Int,
    val totalOriginalBytes: Long,
    /** KDF parameters for the archive key, so a future cost increase does not orphan old backups. */
    val kdfVersion: Int,
    val kdfSaltBase64: String,
    /** Distinguishes "wrong password" from "corrupt archive" without weakening either. */
    val checkValueBase64: String,
    /** Every entry's name and sealed size, so restore can verify completeness before committing. */
    val entries: List<BackupEntry>,
) {
    companion object {
        const val MAGIC: String = "TRUEVAULT-BACKUP"
        const val CURRENT_FORMAT_VERSION: Int = 1
        const val MANIFEST_ENTRY_NAME: String = "manifest.json"
        const val ITEM_ENTRY_PREFIX: String = "items/"
        const val THUMBNAIL_ENTRY_PREFIX: String = "thumbnails/"
        const val METADATA_ENTRY_NAME: String = "metadata.bin"
        const val FILE_EXTENSION: String = "tvbackup"
    }
}

@Serializable
data class BackupEntry(
    val name: String,
    val sealedSizeBytes: Long,
    /** SHA-256 of the sealed bytes, so a truncated or altered entry is caught before commit. */
    val sha256Base64: String,
)

/** Why a backup archive was rejected. Each maps to a different, useful message. */
sealed class BackupException(message: String) : Exception(message) {
    class NotABackup : BackupException("File is not a TrueVault backup")
    class UnsupportedVersion(val found: Int, val maxSupported: Int) :
        BackupException("Backup format version $found is newer than $maxSupported")
    class WrongPassword : BackupException("The password or recovery key did not open this backup")
    class Corrupt(val entryName: String?) : BackupException("Backup archive failed verification")
    class Incomplete(val missingEntries: Int) : BackupException("Backup archive is missing entries")
    class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) :
        BackupException("Not enough free space to restore")
}
