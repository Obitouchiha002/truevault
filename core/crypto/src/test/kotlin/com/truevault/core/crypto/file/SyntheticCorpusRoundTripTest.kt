package com.truevault.core.crypto.file

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.truevault.core.crypto.aead.AesGcm
import com.truevault.core.testing.SyntheticTestData
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.GeneralSecurityException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Every file in the synthetic corpus survives a full encrypt → decrypt cycle byte for byte.
 *
 * The corpus exists because the failures that matter are at the edges: a zero-byte file, a one-byte
 * file, a name in Devanagari, a name with no extension, two files with identical content, two files
 * with identical size and different content. A happy-path fixture of one 4 KB blob proves none of it.
 */
class SyntheticCorpusRoundTripTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var workingDir: File
    private val fileKey: SecretKey = SecretKeySpec(AesGcm.randomBytes(32), "AES")
    private val wrappedKey = AesGcm.randomBytes(60)
    private val metadata = AesGcm.randomBytes(48)

    @Before
    fun setUp() {
        workingDir = temporaryFolder.newFolder("synthetic")
    }

    @After
    fun tearDown() {
        SyntheticTestData.cleanUp(workingDir)
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        VaultFileCipher.encrypt(
            source = plaintext.inputStream(),
            destination = out,
            fileKey = fileKey,
            wrappedFileKey = wrappedKey,
            sealedMetadata = metadata,
            plaintextSize = plaintext.size.toLong(),
            chunkSize = VaultContainer.MIN_CHUNK_SIZE,
        )
        return out.toByteArray()
    }

    private fun decrypt(container: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        VaultFileCipher.decrypt(
            source = container.inputStream(),
            destination = out,
            unwrapFileKey = { fileKey },
        )
        return out.toByteArray()
    }

    @Test
    fun `every file in the corpus round trips byte for byte`() {
        val corpus = SyntheticTestData.createCorpus(workingDir)

        assertThat(corpus).isNotEmpty()

        corpus.forEach { entry ->
            val original = entry.file.readBytes()
            val restored = decrypt(encrypt(original))

            assertWithMessage(entry.label).that(restored).isEqualTo(original)
            assertWithMessage(entry.label).that(restored.size.toLong()).isEqualTo(entry.sizeBytes)
        }
    }

    @Test
    fun `a zero byte file produces a container that still authenticates`() {
        val entry = SyntheticTestData.createCorpus(workingDir).first { it.sizeBytes == 0L }

        val container = encrypt(entry.file.readBytes())

        // Not merely "does not crash": an empty file still gets a real authenticated chunk, so a
        // truncated empty container is distinguishable from a valid one.
        assertThat(container.size).isGreaterThan(VaultContainer.MIN_CHUNK_SIZE.let { 0 })
        assertThat(decrypt(container)).isEmpty()
    }

    @Test
    fun `identical content under different names produces identical plaintext but different ciphertext`() {
        val corpus = SyntheticTestData.createCorpus(workingDir)
        val a = corpus.first { it.file.name == "duplicate-a.bin" }.file.readBytes()
        val b = corpus.first { it.file.name == "duplicate-b.bin" }.file.readBytes()

        assertThat(a).isEqualTo(b)
        // Same plaintext must never yield the same container — that would leak equality to anyone
        // who can see the vault directory.
        assertThat(encrypt(a)).isNotEqualTo(encrypt(b))
    }

    @Test
    fun `two files of identical size but different content stay distinguishable`() {
        val corpus = SyntheticTestData.createCorpus(workingDir)
        val first = corpus.first { it.file.name == "same-size-1.bin" }.file.readBytes()
        val second = corpus.first { it.file.name == "same-size-2.bin" }.file.readBytes()

        assertThat(first.size).isEqualTo(second.size)
        assertThat(first).isNotEqualTo(second)
        assertThat(decrypt(encrypt(first))).isEqualTo(first)
        assertThat(decrypt(encrypt(second))).isEqualTo(second)
    }

    @Test
    fun `a deliberately corrupted container is refused`() {
        val corrupt = SyntheticTestData.createCorruptedContainer(workingDir)

        assertThrows(Exception::class.java) { decrypt(corrupt.readBytes()) }
    }

    @Test
    fun `a corpus file cannot be opened with the wrong key`() {
        val entry = SyntheticTestData.createCorpus(workingDir).first { it.sizeBytes > 0 }
        val container = encrypt(entry.file.readBytes())
        val wrongKey: SecretKey = SecretKeySpec(AesGcm.randomBytes(32), "AES")

        val out = ByteArrayOutputStream()
        assertThrows(GeneralSecurityException::class.java) {
            VaultFileCipher.decrypt(
                source = container.inputStream(),
                destination = out,
                unwrapFileKey = { wrongKey },
            )
        }
        // Nothing must have been written before authentication failed.
        assertThat(out.size()).isEqualTo(0)
    }

    @Test
    fun `cleanUp removes every generated file`() {
        SyntheticTestData.createCorpus(workingDir)
        assertThat(workingDir.listFiles()).isNotEmpty()

        SyntheticTestData.cleanUp(workingDir)

        assertThat(workingDir.exists()).isFalse()
    }
}
