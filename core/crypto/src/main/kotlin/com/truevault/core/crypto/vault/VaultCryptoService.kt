package com.truevault.core.crypto.vault

import com.truevault.core.crypto.aead.AesGcm
import com.truevault.core.crypto.aead.SealedData
import com.truevault.core.crypto.file.ByteProgressListener
import com.truevault.core.crypto.file.CancellationSignal
import com.truevault.core.crypto.file.VaultContainer
import com.truevault.core.crypto.file.VaultContainerHeader
import com.truevault.core.crypto.file.VaultFileCipher
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Raised when a crypto operation is attempted while the vault is locked. */
class VaultLockedException : Exception("The vault is locked")

/** Associated data keeps a sealed blob usable only in the slot it was created for. */
private val AAD_FILE_KEY = "truevault.filekey.v1".toByteArray()
private val AAD_METADATA = "truevault.metadata.v1".toByteArray()
private val AAD_THUMBNAIL = "truevault.thumbnail.v1".toByteArray()
private val FINGERPRINT_DOMAIN = "truevault.fingerprint.v1".toByteArray()

/**
 * The only way the rest of the app encrypts or decrypts vault content.
 *
 * Everything here needs an unlocked session, and every operation fails loudly with
 * [VaultLockedException] rather than degrading if the vault has locked mid-operation.
 *
 * Per-file keys exist so that changing the vault password re-wraps one small blob instead of
 * re-encrypting every file, and so that a single leaked file key cannot open the rest of the vault.
 */
@Singleton
class VaultCryptoService @Inject constructor(
    private val keyManager: VaultKeyManager,
) {

    /** A fresh random key for exactly one vault item. */
    fun generateFileKey(): SecretKey =
        SecretKeySpec(AesGcm.randomBytes(AesGcm.KEY_SIZE_BITS / 8), "AES")

    fun wrapFileKey(fileKey: SecretKey): ByteArray =
        AesGcm.encrypt(requireMasterKey(), fileKey.encoded, AAD_FILE_KEY).toByteArray()

    fun unwrapFileKey(wrapped: ByteArray): SecretKey {
        val raw = AesGcm.decrypt(requireMasterKey(), SealedData.fromByteArray(wrapped), AAD_FILE_KEY)
        return try {
            SecretKeySpec(raw, "AES")
        } finally {
            raw.fill(0)
        }
    }

    /**
     * Wraps a file key under an arbitrary key rather than the vault master key.
     *
     * Used at the backup boundary: an archive must be openable on a device whose vault master key
     * does not yet exist, so the file key travels wrapped by the archive key instead.
     */
    fun wrapFileKeyWith(wrappingKey: SecretKey, fileKey: SecretKey): ByteArray =
        AesGcm.encrypt(wrappingKey, fileKey.encoded, AAD_FILE_KEY).toByteArray()

    fun unwrapFileKeyWith(wrappingKey: SecretKey, wrapped: ByteArray): SecretKey {
        val raw = AesGcm.decrypt(wrappingKey, SealedData.fromByteArray(wrapped), AAD_FILE_KEY)
        return try {
            SecretKeySpec(raw, "AES")
        } finally {
            raw.fill(0)
        }
    }

    /** Seals the metadata blob that travels inside a container header. */
    fun sealMetadata(plaintext: ByteArray, fileKey: SecretKey): ByteArray =
        AesGcm.encrypt(fileKey, plaintext, AAD_METADATA).toByteArray()

    fun openMetadata(sealed: ByteArray, fileKey: SecretKey): ByteArray =
        AesGcm.decrypt(fileKey, SealedData.fromByteArray(sealed), AAD_METADATA)

    /**
     * Seals metadata that lives in the database rather than in a container.
     *
     * Sealed with the vault master key, because it has to be readable for list and search without
     * opening every file's container first.
     */
    fun sealDatabaseField(plaintext: ByteArray): ByteArray =
        AesGcm.encrypt(requireMasterKey(), plaintext, AAD_METADATA).toByteArray()

    fun openDatabaseField(sealed: ByteArray): ByteArray =
        AesGcm.decrypt(requireMasterKey(), SealedData.fromByteArray(sealed), AAD_METADATA)

    fun sealThumbnail(plaintext: ByteArray, fileKey: SecretKey): ByteArray =
        AesGcm.encrypt(fileKey, plaintext, AAD_THUMBNAIL).toByteArray()

    fun openThumbnail(sealed: ByteArray, fileKey: SecretKey): ByteArray =
        AesGcm.decrypt(fileKey, SealedData.fromByteArray(sealed), AAD_THUMBNAIL)

    /**
     * A keyed fingerprint of a file's content hash, for duplicate detection.
     *
     * A raw SHA-256 is stored nowhere: it identifies content exactly, so a stored hash could be
     * matched against a public corpus of known files without ever decrypting anything. An HMAC under
     * a key only this vault holds keeps equality comparison — all duplicate detection needs — while
     * making the value meaningless to anyone else.
     */
    fun fingerprint(contentHash: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val master = requireMasterKey()
        mac.init(SecretKeySpec(master.encoded, "HmacSHA256"))
        mac.update(FINGERPRINT_DOMAIN)
        return mac.doFinal(contentHash)
    }

    /** Streams [source] into [destination] as a vault container. */
    fun encryptFile(
        source: InputStream,
        destination: OutputStream,
        fileKey: SecretKey,
        wrappedFileKey: ByteArray,
        sealedMetadata: ByteArray,
        plaintextSize: Long,
        chunkSize: Int = VaultContainer.DEFAULT_CHUNK_SIZE,
        cancellationSignal: CancellationSignal = CancellationSignal.Never,
        progressListener: ByteProgressListener? = null,
    ): VaultContainerHeader = VaultFileCipher.encrypt(
        source = source,
        destination = destination,
        fileKey = fileKey,
        wrappedFileKey = wrappedFileKey,
        sealedMetadata = sealedMetadata,
        plaintextSize = plaintextSize,
        chunkSize = chunkSize,
        cancellationSignal = cancellationSignal,
        progressListener = progressListener,
    )

    /**
     * Decrypts a container.
     *
     * [wrappedFileKey] overrides the copy stored in the container header. The row's copy is the
     * authoritative one: after a backup is restored into a different vault, the header still carries
     * a key wrapped by the *original* vault's master key, which this vault cannot unwrap. The header
     * cannot simply be rewritten — it is the associated data for every chunk.
     */
    fun decryptFile(
        source: InputStream,
        destination: OutputStream,
        wrappedFileKey: ByteArray? = null,
        cancellationSignal: CancellationSignal = CancellationSignal.Never,
        progressListener: ByteProgressListener? = null,
    ): VaultContainerHeader = VaultFileCipher.decrypt(
        source = source,
        destination = destination,
        unwrapFileKey = { fromHeader -> unwrapFileKey(wrappedFileKey ?: fromHeader) },
        cancellationSignal = cancellationSignal,
        progressListener = progressListener,
    )

    /** Reads a container end to end and checks every tag, producing no plaintext. */
    fun verifyFile(
        source: InputStream,
        wrappedFileKey: ByteArray? = null,
        cancellationSignal: CancellationSignal = CancellationSignal.Never,
        progressListener: ByteProgressListener? = null,
    ): VaultContainerHeader = VaultFileCipher.verify(
        source = source,
        unwrapFileKey = { fromHeader -> unwrapFileKey(wrappedFileKey ?: fromHeader) },
        cancellationSignal = cancellationSignal,
        progressListener = progressListener,
    )

    val isUnlocked: Boolean get() = keyManager.masterKeyOrNull() != null

    private fun requireMasterKey(): SecretKey =
        keyManager.masterKeyOrNull() ?: throw VaultLockedException()
}
