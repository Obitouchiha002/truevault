package com.truevault.core.storage

import com.truevault.core.common.log.SecureLog
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VaultReset"

/**
 * Removes everything TrueVault wrote to disk.
 *
 * Scoped by construction: it only ever touches the app's own directories, obtained from
 * [VaultFileSystem]. It cannot reach a user's originals, their gallery, their Downloads folder or
 * anything else outside the app's private storage — and that is the property that matters here, not
 * the thoroughness. A reset that could delete a file the user kept outside the vault would be a
 * far worse bug than one that left a stray thumbnail behind.
 */
@Singleton
class VaultFileSystemReset @Inject constructor(
    private val fileSystem: VaultFileSystem,
) {

    /**
     * @return a report of what was removed and what refused to go, so the UI can say "some data
     * could not be removed" instead of claiming a clean sweep it did not achieve.
     */
    fun deleteEverything(): ResetReport {
        var deleted = 0
        val failures = mutableListOf<String>()

        // Directory names, never file names: a failure message that quotes a path would put a
        // decrypted file name into a log line, which is exactly what SecureLog exists to prevent.
        listOf(
            "items" to fileSystem.itemsDir,
            "thumbnails" to fileSystem.thumbnailsDir,
            "temp" to fileSystem.tempDir,
            "cache" to fileSystem.plaintextCacheDir,
        ).forEach { (label, dir) ->
            val result = clear(dir)
            deleted += result.first
            if (result.second > 0) failures += "$label (${result.second} remaining)"
        }

        SecureLog.i(TAG, "Reset removed $deleted file(s), ${failures.size} directory failure(s)")

        return ResetReport(filesDeleted = deleted, failedAreas = failures)
    }

    /** @return deleted count to failed count. */
    private fun clear(directory: File): Pair<Int, Int> {
        if (!directory.exists()) return 0 to 0

        var deleted = 0
        var failed = 0

        directory.listFiles()?.forEach { file ->
            val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (ok) deleted++ else failed++
        }

        return deleted to failed
    }
}

data class ResetReport(
    val filesDeleted: Int,
    val failedAreas: List<String>,
) {
    val isComplete: Boolean get() = failedAreas.isEmpty()
}
