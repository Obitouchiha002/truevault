package com.truevault.core.testing

import java.io.File
import java.security.SecureRandom

/**
 * Generates synthetic files for tests.
 *
 * **Nothing here ever touches a real user's storage.** Every file is created inside a caller-supplied
 * temporary directory and is expected to be deleted with [cleanUp]. No test in this project reads the
 * Gallery, Downloads or Documents — a privacy app whose own test suite rummages through personal
 * files would be its own worst counterexample.
 *
 * The awkward cases are here on purpose: zero bytes, one byte, unicode and emoji names, spaces, very
 * long names, no extension, duplicate content under different names, and different content at
 * identical size. Those are the inputs that break importers, and they are exactly what a hand-written
 * happy-path fixture never covers.
 */
object SyntheticTestData {

    private val random = SecureRandom()

    /** A PNG header followed by noise. Enough for MIME sniffing, not a decodable image. */
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    private val PDF_MAGIC = "%PDF-1.7\n".toByteArray()
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val MP4_MAGIC = byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70)

    /** One generated file, described for assertions. */
    data class SyntheticFile(
        val file: File,
        val label: String,
        val sizeBytes: Long,
        val expectedMimeHint: String?,
    )

    /**
     * Creates the standard corpus.
     *
     * [includeLarge] gates the 10 MB and 100 MB entries: they are worth running in a nightly job and
     * wasteful in a unit-test loop, so the caller decides rather than this function guessing.
     */
    fun createCorpus(directory: File, includeLarge: Boolean = false): List<SyntheticFile> {
        directory.mkdirs()
        val files = mutableListOf<SyntheticFile>()

        files += write(directory, "empty.bin", ByteArray(0), "zero-byte file", null)
        files += write(directory, "one-byte.bin", byteArrayOf(0x41), "one-byte file", null)
        files += write(directory, "notes.txt", "plain text contents\n".toByteArray(), "text", "text/plain")

        files += write(
            directory,
            "दस्तावेज़-🔐.txt",
            "unicode name".toByteArray(),
            "unicode and emoji filename",
            "text/plain",
        )
        files += write(
            directory,
            "file with spaces.txt",
            "spaced name".toByteArray(),
            "filename containing spaces",
            "text/plain",
        )
        files += write(
            directory,
            // 200 characters, comfortably inside the 255-byte limit even after UTF-8 expansion.
            "l".repeat(200) + ".txt",
            "long name".toByteArray(),
            "very long filename",
            "text/plain",
        )
        files += write(directory, "no-extension", randomBytes(512), "no extension", null)

        files += write(directory, "image.png", PNG_MAGIC + randomBytes(2048), "PNG", "image/png")
        files += write(directory, "photo.jpg", JPEG_MAGIC + randomBytes(4096), "JPEG", "image/jpeg")
        files += write(directory, "clip.mp4", MP4_MAGIC + randomBytes(8192), "MP4", "video/mp4")
        files += write(directory, "doc.pdf", PDF_MAGIC + randomBytes(2048), "PDF", "application/pdf")
        files += write(directory, "sound.mp3", randomBytes(4096), "audio", "audio/mpeg")
        files += write(directory, "bundle.zip", ZIP_MAGIC + randomBytes(1024), "ZIP", "application/zip")

        // Duplicate detection needs both halves of the problem: same bytes under different names,
        // and different bytes at identical length.
        val sharedContent = randomBytes(4096)
        files += write(directory, "duplicate-a.bin", sharedContent, "duplicate content A", null)
        files += write(directory, "duplicate-b.bin", sharedContent, "duplicate content B", null)
        files += write(directory, "same-size-1.bin", randomBytes(4096), "same size, different bytes", null)
        files += write(directory, "same-size-2.bin", randomBytes(4096), "same size, different bytes", null)

        if (includeLarge) {
            files += writeLarge(directory, "large-10mb.bin", 10L * 1024 * 1024, "10 MB")
            files += writeLarge(directory, "large-100mb.bin", 100L * 1024 * 1024, "100 MB")
        }

        return files
    }

    /**
     * A file that looks like a vault container but is damaged.
     *
     * Used to prove that a corrupt container is rejected rather than partially decrypted.
     */
    fun createCorruptedContainer(directory: File, name: String = "corrupt.vault"): File {
        directory.mkdirs()
        val target = File(directory, name)
        // Correct magic so it gets past the first check, then noise where the header should be.
        target.writeBytes("TVLT".toByteArray() + randomBytes(256))
        return target
    }

    /** Removes everything this object created. Call from `@After`. */
    fun cleanUp(directory: File) {
        if (directory.exists()) directory.deleteRecursively()
    }

    private fun write(
        directory: File,
        name: String,
        bytes: ByteArray,
        label: String,
        mimeHint: String?,
    ): SyntheticFile {
        val file = File(directory, name)
        file.writeBytes(bytes)
        return SyntheticFile(file, label, bytes.size.toLong(), mimeHint)
    }

    /** Streams the content so generating 100 MB does not allocate 100 MB. */
    private fun writeLarge(
        directory: File,
        name: String,
        sizeBytes: Long,
        label: String,
    ): SyntheticFile {
        val file = File(directory, name)
        val chunk = randomBytes(64 * 1024)
        file.outputStream().use { out ->
            var written = 0L
            while (written < sizeBytes) {
                val take = minOf(chunk.size.toLong(), sizeBytes - written).toInt()
                out.write(chunk, 0, take)
                written += take
            }
        }
        return SyntheticFile(file, label, sizeBytes, null)
    }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)
}
