package com.truevault.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.IntentCompat

/**
 * Turns an incoming share into a list of URIs the import flow can read.
 *
 * This is how a file gets into TrueVault from anywhere on the phone: the gallery, a file manager, a
 * chat app, a browser download. The app appears in the system share sheet and receives whatever the
 * user picked, so they never have to come here first and go looking for it.
 *
 * Two things this deliberately does not do:
 *
 *  - It does not act on shared **text**. A share of plain text has no file behind it, and inventing
 *    one would put a note in a file vault.
 *  - It does not delete anything. A share is a copy handed over by another app; whether the original
 *    is removed is the Secure Move question, asked later, with the platform's own confirmation.
 */
object SharedIntentReader {

    /** @return the shared URIs, or an empty list when the intent carries nothing importable. */
    fun read(intent: Intent?): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java),
        )

        Intent.ACTION_SEND_MULTIPLE -> IntentCompat
            .getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            .orEmpty()
            .filterNotNull()

        // A share sheet can also send a single item as a VIEW with a content URI, which is what some
        // file managers do for "Open with".
        Intent.ACTION_VIEW -> listOfNotNull(intent.data)

        else -> emptyList()
    }.filter { it.scheme == "content" || it.scheme == "file" }

    /**
     * Takes what read access the sender granted, for as long as it is available.
     *
     * A share sheet grants read permission to the receiving *activity*, and that grant dies with the
     * task. Import can happen much later — after an unlock, after a lock-screen detour — so a
     * persistable grant is requested where the sender offered one. Where it did not, the URI still
     * works for the life of this task, which covers the ordinary case.
     *
     * Failing to take a grant is not an error worth surfacing: the import will report the file as
     * unreadable at the point where that actually matters, with the file it applies to named.
     */
    fun takeReadPermission(intent: Intent, uris: List<Uri>, contentResolver: android.content.ContentResolver) {
        val persistable = intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
        if (!persistable) return

        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    /** True when this intent is a share TrueVault should act on. */
    fun isShare(intent: Intent?): Boolean = when (intent?.action) {
        Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> read(intent).isNotEmpty()
        else -> false
    }

    @Suppress("unused")
    private val supportsPhotoPickerShare: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
