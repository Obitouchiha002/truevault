package com.truevault.core.common.log

import android.util.Log

/**
 * The only logging entry point in TrueVault.
 *
 * Hard rules enforced here:
 *  - Nothing is logged unless [loggingEnabled] was switched on by the application at startup, and
 *    the application only does that for debuggable builds.
 *  - Callers pass short, fixed technical strings. File names, paths, URIs, passwords, recovery keys,
 *    key material, decrypted content and search queries must never be passed in.
 *  - [redactedLength] is the approved way to describe user data: report its size, never its value.
 *
 * This is a deliberate, reviewable choke point. Anything that wants to log user data has to be
 * changed here first, which makes that change visible in review.
 */
object SecureLog {

    @Volatile
    private var loggingEnabled: Boolean = false

    /** Called once from the Application. Release builds pass `false`. */
    fun configure(enabled: Boolean) {
        loggingEnabled = enabled
    }

    fun d(tag: String, message: String) {
        if (loggingEnabled) Log.d(tag.tv(), message)
    }

    fun i(tag: String, message: String) {
        if (loggingEnabled) Log.i(tag.tv(), message)
    }

    fun w(tag: String, message: String) {
        if (loggingEnabled) Log.w(tag.tv(), message)
    }

    /**
     * Errors are logged without the throwable's message, because provider exceptions routinely
     * embed file names and URIs. The class name is enough to locate the failure in code.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!loggingEnabled) return
        val suffix = throwable?.let { " (${it.javaClass.simpleName})" }.orEmpty()
        Log.e(tag.tv(), message + suffix)
    }

    /** Describes a sensitive value by size only, e.g. `bytes[len=1048576]`. */
    fun redactedLength(label: String, length: Long): String = "$label[len=$length]"

    private fun String.tv(): String = "TrueVault/$this"
}
