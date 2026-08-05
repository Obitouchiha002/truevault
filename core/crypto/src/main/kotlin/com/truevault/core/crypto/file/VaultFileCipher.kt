package com.truevault.core.crypto.file

import com.truevault.core.crypto.aead.AesGcm
import com.truevault.core.crypto.aead.SealedData
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.crypto.SecretKey

/** Progress callback. Called per chunk, not per byte — the caller throttles further if it needs to. */
fun interface ByteProgressListener {
    fun onProgress(bytesProcessed: Long, totalBytes: Long)
}

/** Raised when a caller cancels an in-flight encryption or decryption. */
class VaultStreamCancelledException : Exception("Stream cancelled")

/** Checked between chunks so a cancelled import stops promptly without a thread interrupt. */
fun interface CancellationSignal {
    fun isCancelled(): Boolean

    companion object {
        val Never = CancellationSignal { false }
    }
}

/**
 * Streaming encryption and decryption for vault files.
 *
 * Files are processed one chunk at a time and never held whole in memory: importing a 4 GB video
 * allocates one chunk buffer, not 4 GB.
 */
object VaultFileCipher {

    /**
     * Encrypts [source] into [destination].
     *
     * @param plaintextSize must be the exact source length. It is written into the header and
     * authenticated, which is what makes truncation detectable later; a wrong value here produces a
     * container that will refuse to open.
     */
    fun encrypt(
        source: InputStream,
        destination: OutputStream,
        fileKey: SecretKey,
        wrappedFileKey: ByteArray,
        sealedMetadata: ByteArray,
        plaintextSize: Long,
        chunkSize: Int = VaultContainer.DEFAULT_CHUNK_SIZE,
        cancellationSignal: CancellationSignal = CancellationSignal.Never,
        progressListener: ByteProgressListener? = null,
    ): VaultContainerHeader {
        require(chunkSize in VaultContainer.MIN_CHUNK_SIZE..VaultContainer.MAX_CHUNK_SIZE) {
            "Chunk size out of range"
        }
        require(plaintextSize >= 0) { "Plaintext size must not be negative" }

        val header = VaultContainerHeader(
            formatVersion = VaultContainer.CURRENT_FORMAT_VERSION,
            algorithm = VaultContainer.ALGORITHM_AES_256_GCM_CHUNKED,
            flags = 0,
            chunkSize = chunkSize,
            plaintextSize = plaintextSize,
            wrappedFileKey = wrappedFileKey,
            sealedMetadata = sealedMetadata,
        )

        val headerBytes = header.toByteArray()
        destination.write(headerBytes)

        val buffer = ByteArray(chunkSize)
        var chunkIndex = 0L
        var written = 0L

        while (written < plaintextSize) {
            if (cancellationSignal.isCancelled()) throw VaultStreamCancelledException()

            val remaining = plaintextSize - written
            val target = if (remaining < chunkSize) remaining.toInt() else chunkSize
            val read = source.readFullyUpTo(buffer, target)

            if (read < target) {
                // The source shrank while being read. Failing here is correct: writing a container
                // whose declared size does not match its contents would produce a file that always
                // fails to open later, with no explanation available at that point.
                throw EOFException("Source ended after $written of $plaintextSize bytes")
            }

            val isLast = written + read >= plaintextSize
            val aad = chunkAssociatedData(headerBytes, chunkIndex, isLast)
            val sealed = AesGcm.encrypt(fileKey, buffer.copyOf(read), aad)
            destination.write(sealed.toByteArray())

            written += read
            chunkIndex++
            progressListener?.onProgress(written, plaintextSize)
        }

        if (plaintextSize == 0L) {
            // An empty file still gets one authenticated chunk, so "empty" is a fact the container
            // proves rather than an absence a reader has to infer.
            val aad = chunkAssociatedData(headerBytes, chunkIndex = 0L, isLast = true)
            val sealed = AesGcm.encrypt(fileKey, ByteArray(0), aad)
            destination.write(sealed.toByteArray())
            progressListener?.onProgress(0L, 0L)
        }

        destination.flush()
        return header
    }

    /**
     * Decrypts a container into [destination].
     *
     * @param unwrapFileKey turns the header's wrapped key into the file key. Passed in so this
     * object never touches the vault master key.
     * @return the parsed header, once every chunk has authenticated.
     */
    fun decrypt(
        source: InputStream,
        destination: OutputStream,
        unwrapFileKey: (ByteArray) -> SecretKey,
        cancellationSignal: CancellationSignal = CancellationSignal.Never,
        progressListener: ByteProgressListener? = null,
    ): VaultContainerHeader {
        val header = VaultContainerCodec.read(source)
        val headerBytes = header.toByteArray()
        val fileKey = unwrapFileKey(header.wrappedFileKey)

        var produced = 0L
        var chunkIndex = 0L

        while (produced < header.plaintextSize || (header.plaintextSize == 0L && chunkIndex == 0L)) {
            if (cancellationSignal.isCancelled()) throw VaultStreamCancelledException()

            val remaining = header.plaintextSize - produced
            val expectedPlain =
                if (remaining < header.chunkSize) remaining.toInt() else header.chunkSize
            val sealedLength = AesGcm.NONCE_SIZE_BYTES + expectedPlain + AesGcm.TAG_SIZE_BYTES

            val sealedBytes = ByteArray(sealedLength)
            val read = source.readFullyUpTo(sealedBytes, sealedLength)
            if (read < sealedLength) throw VaultContainerException.Truncated()

            val isLast = produced + expectedPlain >= header.plaintextSize
            val aad = chunkAssociatedData(headerBytes, chunkIndex, isLast)

            // A wrong key, a reordered chunk, a tampered header or a spliced-in chunk all surface
            // here as an authentication failure, and all of them stop the read.
            val plain = AesGcm.decrypt(fileKey, SealedData.fromByteArray(sealedBytes), aad)
            destination.write(plain)

            produced += plain.size
            chunkIndex++
            progressListener?.onProgress(produced, header.plaintextSize)

            if (header.plaintextSize == 0L) break
        }

        if (produced != header.plaintextSize) throw VaultContainerException.Truncated()

        destination.flush()
        return header
    }

    /**
     * Reads a container without writing the plaintext anywhere.
     *
     * This is the verification step of Secure Move: the encrypted copy is decrypted end to end and
     * every tag is checked, but the plaintext goes nowhere. Only after this succeeds is the user
     * asked about deleting the original.
     */
    fun verify(
        source: InputStream,
        unwrapFileKey: (ByteArray) -> SecretKey,
        cancellationSignal: CancellationSignal = CancellationSignal.Never,
        progressListener: ByteProgressListener? = null,
    ): VaultContainerHeader = decrypt(
        source = source,
        destination = NullOutputStream,
        unwrapFileKey = unwrapFileKey,
        cancellationSignal = cancellationSignal,
        progressListener = progressListener,
    )

    /**
     * Associated data for one chunk: the whole header, the chunk's index, and whether it is last.
     *
     * Including the index stops reordering and duplication. Including the end marker stops a file
     * from being silently shortened by dropping trailing chunks.
     */
    private fun chunkAssociatedData(
        headerBytes: ByteArray,
        chunkIndex: Long,
        isLast: Boolean,
    ): ByteArray = ByteBuffer.allocate(headerBytes.size + Long.SIZE_BYTES + 1)
        .put(headerBytes)
        .putLong(chunkIndex)
        .put(if (isLast) 1 else 0)
        .array()
}

/** Reads until [length] bytes are filled or the stream ends. Returns how many were read. */
internal fun InputStream.readFullyUpTo(buffer: ByteArray, length: Int): Int {
    var total = 0
    while (total < length) {
        val read = read(buffer, total, length - total)
        if (read < 0) break
        total += read
    }
    return total
}

/** Discards everything. Used by verification, which needs the tags checked but not the plaintext. */
private object NullOutputStream : OutputStream() {
    override fun write(b: Int) = Unit
    override fun write(b: ByteArray) = Unit
    override fun write(b: ByteArray, off: Int, len: Int) = Unit
}
