package com.truevault.core.storage

import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SHA-256 over a stream.
 *
 * Used for exact-duplicate detection. It reads in chunks and never holds the file in memory, and it
 * is always called from a background dispatcher — hashing a 4 GB video on the main thread would
 * freeze the app for minutes.
 *
 * The hash of a user's file is itself sensitive: it identifies the content. It is stored encrypted
 * and never logged.
 */
@Singleton
class ContentHasher @Inject constructor() {

    fun sha256(input: InputStream, cancellationSignal: () -> Boolean = { false }): ByteArray? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)

        while (true) {
            if (cancellationSignal()) return null
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }

        return digest.digest()
    }

    /** Lowercase hex, for comparison and storage. */
    fun toHex(hash: ByteArray): String = hash.joinToString("") { byte ->
        val value = byte.toInt() and 0xFF
        HEX[value ushr 4].toString() + HEX[value and 0x0F]
    }

    private companion object {
        const val HEX = "0123456789abcdef"
    }
}
