package com.truevault.core.crypto.file

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * The TrueVault encrypted-file format.
 *
 * ```
 * ┌──────────────── header (authenticated as associated data on every chunk) ─────────────────┐
 * │ magic            4 bytes   "TVLT"                                                          │
 * │ formatVersion    2 bytes   u16                                                             │
 * │ algorithm        1 byte    1 = AES-256-GCM, chunked                                        │
 * │ flags            1 byte    reserved, must be 0                                             │
 * │ chunkSize        4 bytes   u32, plaintext bytes per chunk                                  │
 * │ plaintextSize    8 bytes   u64, exact size of the original file                            │
 * │ wrappedKeyLen    2 bytes   u16                                                             │
 * │ wrappedKey       n bytes   file key, sealed by the vault master key (nonce ‖ ct ‖ tag)     │
 * │ metadataLen      4 bytes   u32                                                             │
 * │ metadata         n bytes   sealed metadata blob (nonce ‖ ct ‖ tag)                          │
 * └────────────────────────────────────────────────────────────────────────────────────────────┘
 * then, repeated until plaintextSize bytes have been produced:
 *   nonce 12 bytes ‖ ciphertext+tag
 * ```
 *
 * Design points that matter:
 *
 *  - **The header is not encrypted, but it is authenticated.** Every chunk uses the serialised
 *    header as associated data, so altering the version, the chunk size or the declared plaintext
 *    length makes every chunk fail to open. There is no separate header MAC to get out of sync.
 *  - **Chunks are bound to their position.** The chunk index and an end-of-file marker are part of
 *    the associated data, so chunks cannot be reordered, duplicated, dropped, or spliced in from a
 *    different file — each of which would otherwise be undetectable with per-chunk GCM alone.
 *  - **Truncation is detectable.** `plaintextSize` is authenticated, so a file cut short fails
 *    rather than silently decrypting to a shorter document.
 *  - **The file key is stored only in wrapped form.** Opening a container requires the vault master
 *    key, which only exists in memory during an unlocked session.
 */
object VaultContainer {

    val MAGIC: ByteArray = byteArrayOf('T'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())

    const val FORMAT_VERSION_1: Int = 1
    const val CURRENT_FORMAT_VERSION: Int = FORMAT_VERSION_1

    const val ALGORITHM_AES_256_GCM_CHUNKED: Int = 1

    /**
     * 1 MiB of plaintext per chunk.
     *
     * Large enough that the 16-byte tag and 12-byte nonce per chunk cost ~0.003% overhead, small
     * enough that a chunk always fits comfortably in memory on a low-end device — the two
     * constraints that decide this number.
     */
    const val DEFAULT_CHUNK_SIZE: Int = 1024 * 1024

    const val MIN_CHUNK_SIZE: Int = 16 * 1024
    const val MAX_CHUNK_SIZE: Int = 8 * 1024 * 1024

    /** Refuses obviously malformed sizes before any allocation happens. */
    const val MAX_WRAPPED_KEY_LENGTH: Int = 1024
    const val MAX_METADATA_LENGTH: Int = 64 * 1024

    /** The extension of a container still being written. Never a finished vault item. */
    const val PART_EXTENSION: String = ".vault.part"

    /** The extension of a committed container. */
    const val FINAL_EXTENSION: String = ".vault"
}

/** Everything the header carries, already validated. */
data class VaultContainerHeader(
    val formatVersion: Int,
    val algorithm: Int,
    val flags: Int,
    val chunkSize: Int,
    val plaintextSize: Long,
    val wrappedFileKey: ByteArray,
    val sealedMetadata: ByteArray,
) {
    /** The exact bytes that every chunk authenticates. Recomputed, never trusted from the file. */
    fun toByteArray(): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.write(VaultContainer.MAGIC)
            out.writeShort(formatVersion)
            out.writeByte(algorithm)
            out.writeByte(flags)
            out.writeInt(chunkSize)
            out.writeLong(plaintextSize)
            out.writeShort(wrappedFileKey.size)
            out.write(wrappedFileKey)
            out.writeInt(sealedMetadata.size)
            out.write(sealedMetadata)
        }
        return buffer.toByteArray()
    }

    /** How many chunks a container of this size contains. */
    val chunkCount: Long
        get() = if (plaintextSize == 0L) 1L else (plaintextSize + chunkSize - 1) / chunkSize

    override fun equals(other: Any?): Boolean = other is VaultContainerHeader &&
        formatVersion == other.formatVersion &&
        algorithm == other.algorithm &&
        flags == other.flags &&
        chunkSize == other.chunkSize &&
        plaintextSize == other.plaintextSize &&
        wrappedFileKey.contentEquals(other.wrappedFileKey) &&
        sealedMetadata.contentEquals(other.sealedMetadata)

    override fun hashCode(): Int {
        var result = formatVersion
        result = 31 * result + algorithm
        result = 31 * result + flags
        result = 31 * result + chunkSize
        result = 31 * result + plaintextSize.hashCode()
        result = 31 * result + wrappedFileKey.contentHashCode()
        result = 31 * result + sealedMetadata.contentHashCode()
        return result
    }

    override fun toString(): String =
        "VaultContainerHeader(v=$formatVersion, alg=$algorithm, chunk=$chunkSize, size=$plaintextSize)"
}

/**
 * Why a container was rejected.
 *
 * Every one of these is a refusal to proceed. The parser never repairs, guesses, or falls back to a
 * more permissive reading — a container that does not parse exactly is not opened at all.
 */
sealed class VaultContainerException(message: String) : Exception(message) {
    class BadMagic : VaultContainerException("Not a TrueVault container")
    class UnsupportedVersion(val found: Int, val maxSupported: Int) :
        VaultContainerException("Container format version $found is newer than $maxSupported")
    class UnsupportedAlgorithm(val found: Int) :
        VaultContainerException("Unknown algorithm identifier $found")
    class InvalidField(val field: String) :
        VaultContainerException("Container field out of range: $field")
    class Truncated : VaultContainerException("Container ends before its declared length")
}

/** Serialises and parses [VaultContainerHeader]. */
object VaultContainerCodec {

    fun write(out: OutputStream, header: VaultContainerHeader) {
        out.write(header.toByteArray())
    }

    /**
     * Reads and validates a header.
     *
     * Every length is bounds-checked *before* it is used to allocate, so a corrupt or hostile file
     * cannot make the app reserve gigabytes on the strength of a four-byte field.
     */
    fun read(input: InputStream): VaultContainerHeader {
        val data = DataInputStream(input)

        val magic = ByteArray(VaultContainer.MAGIC.size)
        data.readFullyOrThrow(magic)
        if (!magic.contentEquals(VaultContainer.MAGIC)) throw VaultContainerException.BadMagic()

        val formatVersion = data.readUnsignedShortOrThrow()
        if (formatVersion > VaultContainer.CURRENT_FORMAT_VERSION || formatVersion < 1) {
            throw VaultContainerException.UnsupportedVersion(
                found = formatVersion,
                maxSupported = VaultContainer.CURRENT_FORMAT_VERSION,
            )
        }

        val algorithm = data.readUnsignedByteOrThrow()
        if (algorithm != VaultContainer.ALGORITHM_AES_256_GCM_CHUNKED) {
            throw VaultContainerException.UnsupportedAlgorithm(algorithm)
        }

        val flags = data.readUnsignedByteOrThrow()
        if (flags != 0) throw VaultContainerException.InvalidField("flags")

        val chunkSize = data.readIntOrThrow()
        if (chunkSize < VaultContainer.MIN_CHUNK_SIZE || chunkSize > VaultContainer.MAX_CHUNK_SIZE) {
            throw VaultContainerException.InvalidField("chunkSize")
        }

        val plaintextSize = data.readLongOrThrow()
        if (plaintextSize < 0) throw VaultContainerException.InvalidField("plaintextSize")

        val wrappedKeyLength = data.readUnsignedShortOrThrow()
        if (wrappedKeyLength <= 0 || wrappedKeyLength > VaultContainer.MAX_WRAPPED_KEY_LENGTH) {
            throw VaultContainerException.InvalidField("wrappedKeyLength")
        }
        val wrappedFileKey = ByteArray(wrappedKeyLength)
        data.readFullyOrThrow(wrappedFileKey)

        val metadataLength = data.readIntOrThrow()
        if (metadataLength < 0 || metadataLength > VaultContainer.MAX_METADATA_LENGTH) {
            throw VaultContainerException.InvalidField("metadataLength")
        }
        val sealedMetadata = ByteArray(metadataLength)
        data.readFullyOrThrow(sealedMetadata)

        return VaultContainerHeader(
            formatVersion = formatVersion,
            algorithm = algorithm,
            flags = flags,
            chunkSize = chunkSize,
            plaintextSize = plaintextSize,
            wrappedFileKey = wrappedFileKey,
            sealedMetadata = sealedMetadata,
        )
    }

    private fun DataInputStream.readFullyOrThrow(target: ByteArray) = try {
        readFully(target)
    } catch (e: EOFException) {
        throw VaultContainerException.Truncated()
    }

    private fun DataInputStream.readUnsignedShortOrThrow(): Int = try {
        readUnsignedShort()
    } catch (e: EOFException) {
        throw VaultContainerException.Truncated()
    }

    private fun DataInputStream.readUnsignedByteOrThrow(): Int = try {
        readUnsignedByte()
    } catch (e: EOFException) {
        throw VaultContainerException.Truncated()
    }

    private fun DataInputStream.readIntOrThrow(): Int = try {
        readInt()
    } catch (e: EOFException) {
        throw VaultContainerException.Truncated()
    }

    private fun DataInputStream.readLongOrThrow(): Long = try {
        readLong()
    } catch (e: EOFException) {
        throw VaultContainerException.Truncated()
    }
}
