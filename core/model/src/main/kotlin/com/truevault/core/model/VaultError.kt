package com.truevault.core.model

/**
 * Every failure TrueVault can surface to a user.
 *
 * Rules:
 *  - No exception, stack trace, file path, URI or file name is ever carried in here.
 *  - [Unknown.safeMessage] must already be safe to display; callers must not put raw throwable
 *    messages into it.
 *  - The UI maps these to human copy; the model layer stays free of Android resources.
 */
sealed interface VaultError {

    /** The selected source no longer exists, or the app never had access to it. */
    data object SourceNotFound : VaultError

    /** A URI permission grant was never given, was revoked, or has expired. */
    data object PermissionDenied : VaultError

    /** Not enough free space for the encrypted copy plus its thumbnail and safety buffer. */
    data class InsufficientStorage(
        val requiredBytes: Long,
        val availableBytes: Long,
    ) : VaultError

    /**
     * The device has room, but the vault has reached the ceiling the user set.
     *
     * Kept separate from [InsufficientStorage] because the two need different words and different
     * buttons: a full phone is solved in system settings, a full budget is solved in TrueVault in
     * two taps. Collapsing them would send a user to free up space they already have.
     */
    data class StorageBudgetReached(
        val budget: StorageBudget,
        val requiredBytes: Long,
        val usedBytes: Long,
    ) : VaultError

    /** The vault session is locked or expired; the caller must authenticate again. */
    data object AuthenticationRequired : VaultError

    /**
     * Too many consecutive failed unlocks; the vault is refusing attempts for [waitMillis] longer.
     *
     * Carries the remaining wait so the screen can count it down rather than leaving the user
     * guessing whether the app is broken.
     */
    data class TooManyAttempts(val waitMillis: Long) : VaultError

    /** Encryption could not complete. The original file is untouched when this is reported. */
    data object EncryptionFailed : VaultError

    /** Decryption could not complete: wrong key, or the stored ciphertext is not readable. */
    data object DecryptionFailed : VaultError

    /**
     * Authenticated decryption rejected the data. Treat as corruption or tampering — never fall
     * back to returning partial plaintext.
     */
    data object IntegrityCheckFailed : VaultError

    /** The encrypted container is a format version this build cannot read. */
    data class UnsupportedFormatVersion(
        val foundVersion: Int,
        val maxSupportedVersion: Int,
    ) : VaultError

    /** The user declined the system delete confirmation for the original file. */
    data object UserCancelledDeletion : VaultError

    /** The document provider does not support deletion (common for read-only cloud providers). */
    data object DeleteNotSupported : VaultError

    /** The file type cannot be imported or previewed by this build. */
    data object UnsupportedFile : VaultError

    /** The user cancelled an in-flight operation. Not an error state to apologise for. */
    data object Cancelled : VaultError

    /** A database write failed and was rolled back. No partial vault entry remains. */
    data object DatabaseTransactionFailed : VaultError

    /** A backup archive failed validation before anything was written to the active vault. */
    data object BackupInvalid : VaultError

    /** Last resort. [safeMessage] must be pre-sanitised, user-presentable text. */
    data class Unknown(val safeMessage: String) : VaultError
}

/** True when retrying the same operation could plausibly succeed without user changes. */
val VaultError.isRetryable: Boolean
    get() = when (this) {
        is VaultError.InsufficientStorage,
        VaultError.EncryptionFailed,
        VaultError.DecryptionFailed,
        VaultError.DatabaseTransactionFailed,
        is VaultError.Unknown,
        -> true

        VaultError.SourceNotFound,
        VaultError.PermissionDenied,
        VaultError.AuthenticationRequired,
        is VaultError.TooManyAttempts,
        VaultError.IntegrityCheckFailed,
        is VaultError.UnsupportedFormatVersion,
        VaultError.UserCancelledDeletion,
        VaultError.DeleteNotSupported,
        VaultError.UnsupportedFile,
        VaultError.Cancelled,
        VaultError.BackupInvalid,
        // Retrying the identical import cannot succeed: the ceiling is where the user put it, and
        // only they can move it. The screen offers that, rather than a Retry button that would do
        // the same thing and fail the same way.
        is VaultError.StorageBudgetReached,
        -> false
    }
