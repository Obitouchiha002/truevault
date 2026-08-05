package com.truevault.core.crypto.file

import com.google.common.truth.Truth.assertThat
import com.truevault.core.crypto.aead.AesGcm
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The container format is the thing a user's files actually live inside. These tests exist to make
 * sure it fails closed: wrong key, tampered byte, reordered chunk, truncated file and unsupported
 * version must all refuse, and none of them may return partial plaintext.
 */
class VaultFileCipherTest {

    private val fileKey: SecretKey = SecretKeySpec(AesGcm.randomBytes(32), "AES")
    private val wrongKey: SecretKey = SecretKeySpec(AesGcm.randomBytes(32), "AES")
    private val wrappedKey = AesGcm.randomBytes(60)
    private val metadata = AesGcm.randomBytes(48)
    private val smallChunk = VaultContainer.MIN_CHUNK_SIZE

    private fun encrypt(
        plaintext: ByteArray,
        chunkSize: Int = smallChunk,
        declaredSize: Long = plaintext.size.toLong(),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        VaultFileCipher.encrypt(
            source = ByteArrayInputStream(plaintext),
            destination = out,
            fileKey = fileKey,
            wrappedFileKey = wrappedKey,
            sealedMetadata = metadata,
            plaintextSize = declaredSize,
            chunkSize = chunkSize,
        )
        return out.toByteArray()
    }

    private fun decrypt(container: ByteArray, key: SecretKey = fileKey): ByteArray {
        val out = ByteArrayOutputStream()
        VaultFileCipher.decrypt(
            source = ByteArrayInputStream(container),
            destination = out,
            unwrapFileKey = { key },
        )
        return out.toByteArray()
    }

    @Test
    fun `a single chunk file round trips`() {
        val plaintext = "a private document".toByteArray()

        assertThat(decrypt(encrypt(plaintext))).isEqualTo(plaintext)
    }

    @Test
    fun `a multi chunk file round trips`() {
        val plaintext = AesGcm.randomBytes(smallChunk * 3 + 517)

        assertThat(decrypt(encrypt(plaintext))).isEqualTo(plaintext)
    }

    @Test
    fun `a file exactly one chunk long round trips`() {
        val plaintext = AesGcm.randomBytes(smallChunk)

        assertThat(decrypt(encrypt(plaintext))).isEqualTo(plaintext)
    }

    @Test
    fun `a zero byte file round trips`() {
        assertThat(decrypt(encrypt(ByteArray(0)))).isEmpty()
    }

    @Test
    fun `encrypting the same file twice produces different ciphertext`() {
        val plaintext = AesGcm.randomBytes(4096)

        assertThat(encrypt(plaintext)).isNotEqualTo(encrypt(plaintext))
    }

    @Test
    fun `the header identifies the format and carries the exact plaintext size`() {
        val plaintext = AesGcm.randomBytes(5000)
        val container = encrypt(plaintext)

        val header = VaultContainerCodec.read(ByteArrayInputStream(container))

        assertThat(header.formatVersion).isEqualTo(VaultContainer.CURRENT_FORMAT_VERSION)
        assertThat(header.algorithm).isEqualTo(VaultContainer.ALGORITHM_AES_256_GCM_CHUNKED)
        assertThat(header.plaintextSize).isEqualTo(5000)
        assertThat(header.wrappedFileKey).isEqualTo(wrappedKey)
    }

    @Test
    fun `the wrong key produces no plaintext at all`() {
        val container = encrypt("secret".toByteArray())

        assertThrows(GeneralSecurityException::class.java) { decrypt(container, wrongKey) }
    }

    @Test
    fun `a flipped ciphertext byte is rejected`() {
        val container = encrypt(AesGcm.randomBytes(2048)).copyOf()
        container[container.size - 20] = (container[container.size - 20] + 1).toByte()

        assertThrows(GeneralSecurityException::class.java) { decrypt(container) }
    }

    @Test
    fun `altering the declared plaintext size in the header is rejected`() {
        val plaintext = AesGcm.randomBytes(3000)
        val container = encrypt(plaintext).copyOf()

        // plaintextSize sits at offset 4+2+1+1+4 = 12 and is authenticated by every chunk.
        container[19] = (container[19] + 1).toByte()

        assertThrows(Exception::class.java) { decrypt(container) }
    }

    @Test
    fun `swapping two chunks is rejected`() {
        val plaintext = AesGcm.randomBytes(smallChunk * 2)
        val container = encrypt(plaintext)

        val headerLength = container.size - 2 * (smallChunk + AesGcm.NONCE_SIZE_BYTES + AesGcm.TAG_SIZE_BYTES)
        val chunkLength = smallChunk + AesGcm.NONCE_SIZE_BYTES + AesGcm.TAG_SIZE_BYTES
        val swapped = container.copyOf()
        val first = container.copyOfRange(headerLength, headerLength + chunkLength)
        val second = container.copyOfRange(headerLength + chunkLength, headerLength + 2 * chunkLength)
        second.copyInto(swapped, headerLength)
        first.copyInto(swapped, headerLength + chunkLength)

        assertThrows(GeneralSecurityException::class.java) { decrypt(swapped) }
    }

    @Test
    fun `dropping the final chunk is detected rather than silently shortening the file`() {
        val plaintext = AesGcm.randomBytes(smallChunk * 2)
        val container = encrypt(plaintext)
        val chunkLength = smallChunk + AesGcm.NONCE_SIZE_BYTES + AesGcm.TAG_SIZE_BYTES

        val truncated = container.copyOf(container.size - chunkLength)

        assertThrows(VaultContainerException.Truncated::class.java) { decrypt(truncated) }
    }

    @Test
    fun `a file cut off mid-chunk is detected`() {
        val container = encrypt(AesGcm.randomBytes(smallChunk * 2))

        val truncated = container.copyOf(container.size - 100)

        assertThrows(VaultContainerException.Truncated::class.java) { decrypt(truncated) }
    }

    @Test
    fun `a container with the wrong magic bytes is refused`() {
        val container = encrypt("x".toByteArray()).copyOf()
        container[0] = 'X'.code.toByte()

        assertThrows(VaultContainerException.BadMagic::class.java) { decrypt(container) }
    }

    @Test
    fun `a newer format version is refused instead of guessed at`() {
        val container = encrypt("x".toByteArray()).copyOf()
        container[5] = (VaultContainer.CURRENT_FORMAT_VERSION + 1).toByte()

        val error = assertThrows(VaultContainerException.UnsupportedVersion::class.java) {
            decrypt(container)
        }
        assertThat(error.maxSupported).isEqualTo(VaultContainer.CURRENT_FORMAT_VERSION)
    }

    @Test
    fun `an unknown algorithm identifier is refused`() {
        val container = encrypt("x".toByteArray()).copyOf()
        container[6] = 99

        assertThrows(VaultContainerException.UnsupportedAlgorithm::class.java) { decrypt(container) }
    }

    @Test
    fun `an absurd chunk size in the header is refused before allocating`() {
        val container = encrypt("x".toByteArray()).copyOf()
        // chunkSize occupies bytes 8..11.
        container[8] = 0x7F
        container[9] = 0x7F.toByte()

        assertThrows(VaultContainerException.InvalidField::class.java) { decrypt(container) }
    }

    @Test
    fun `an empty file is refused as a container`() {
        assertThrows(VaultContainerException.Truncated::class.java) { decrypt(ByteArray(0)) }
    }

    @Test
    fun `verification checks every tag and produces nothing`() {
        val plaintext = AesGcm.randomBytes(smallChunk + 128)
        val container = encrypt(plaintext)

        val header = VaultFileCipher.verify(
            source = ByteArrayInputStream(container),
            unwrapFileKey = { fileKey },
        )

        assertThat(header.plaintextSize).isEqualTo(plaintext.size.toLong())
    }

    @Test
    fun `verification fails on a tampered container`() {
        val container = encrypt(AesGcm.randomBytes(2048)).copyOf()
        container[container.size - 1] = (container[container.size - 1] + 1).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            VaultFileCipher.verify(ByteArrayInputStream(container), { fileKey })
        }
    }

    @Test
    fun `progress is reported up to the exact file size`() {
        val plaintext = AesGcm.randomBytes(smallChunk * 2 + 7)
        val seen = mutableListOf<Long>()

        val out = ByteArrayOutputStream()
        VaultFileCipher.encrypt(
            source = ByteArrayInputStream(plaintext),
            destination = out,
            fileKey = fileKey,
            wrappedFileKey = wrappedKey,
            sealedMetadata = metadata,
            plaintextSize = plaintext.size.toLong(),
            chunkSize = smallChunk,
            progressListener = { processed, _ -> seen += processed },
        )

        assertThat(seen).isInOrder()
        assertThat(seen.last()).isEqualTo(plaintext.size.toLong())
    }

    @Test
    fun `cancelling stops the stream and reports it`() {
        val plaintext = AesGcm.randomBytes(smallChunk * 4)
        var chunks = 0

        assertThrows(VaultStreamCancelledException::class.java) {
            VaultFileCipher.encrypt(
                source = ByteArrayInputStream(plaintext),
                destination = ByteArrayOutputStream(),
                fileKey = fileKey,
                wrappedFileKey = wrappedKey,
                sealedMetadata = metadata,
                plaintextSize = plaintext.size.toLong(),
                chunkSize = smallChunk,
                cancellationSignal = { chunks++ >= 2 },
            )
        }
    }

    @Test
    fun `a source that shrinks mid-read fails instead of writing a lying container`() {
        val declared = 10_000L
        val actual = AesGcm.randomBytes(4_000)

        assertThrows(Exception::class.java) {
            VaultFileCipher.encrypt(
                source = ByteArrayInputStream(actual),
                destination = ByteArrayOutputStream(),
                fileKey = fileKey,
                wrappedFileKey = wrappedKey,
                sealedMetadata = metadata,
                plaintextSize = declared,
                chunkSize = smallChunk,
            )
        }
    }

    @Test
    fun `chunks from one file cannot be spliced into another`() {
        // In production every container has its own wrapped file key, and the wrap uses a fresh
        // random nonce — so no two headers are ever byte-identical. Since the header is the
        // associated data for every chunk, that alone makes cross-file splicing fail. The second
        // container here is built the way a real second import would be.
        val first = encrypt(AesGcm.randomBytes(smallChunk * 2))

        val secondOut = ByteArrayOutputStream()
        VaultFileCipher.encrypt(
            source = ByteArrayInputStream(AesGcm.randomBytes(smallChunk * 2)),
            destination = secondOut,
            fileKey = fileKey,
            wrappedFileKey = AesGcm.randomBytes(wrappedKey.size),
            sealedMetadata = metadata,
            plaintextSize = (smallChunk * 2).toLong(),
            chunkSize = smallChunk,
        )
        val second = secondOut.toByteArray()

        val chunkLength = smallChunk + AesGcm.NONCE_SIZE_BYTES + AesGcm.TAG_SIZE_BYTES
        val headerLength = first.size - 2 * chunkLength

        val spliced = first.copyOf()
        second.copyOfRange(headerLength, headerLength + chunkLength)
            .copyInto(spliced, headerLength)

        assertThrows(GeneralSecurityException::class.java) { decrypt(spliced) }
    }
}
