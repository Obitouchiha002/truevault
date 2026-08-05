package com.truevault.core.model

/**
 * Where a vault item stands with respect to the user's privacy, not with respect to the file system.
 *
 * These values are shown to users, so each one must be something TrueVault can actually prove.
 */
enum class PrivacyStatus {
    /** Encrypted, verified, and the original is confirmed gone. */
    SECURED,

    /** Encrypted and verified, but a copy still exists outside TrueVault. */
    ORIGINAL_REMAINS,

    /** Deletion of the original was requested and is awaiting a system confirmation result. */
    DELETE_PENDING,

    /** A scan found at least one other accessible copy of this content. */
    DUPLICATE_FOUND,

    /** The import did not complete. Nothing outside TrueVault was modified. */
    IMPORT_FAILED,

    /** An integrity check is in progress. */
    VERIFYING,

    /** Authenticated decryption failed for the stored container. */
    CORRUPTED,
}

/** How the user chose to bring a file into the vault. */
enum class ImportMode {
    /** Encrypt a copy; leave the original exactly where it is. */
    SECURE_COPY,

    /** Encrypt, verify, commit, then ask the system to remove the original. */
    SECURE_MOVE,
}

/** The user's standing preference for [ImportMode], per file category. */
enum class ImportModePreference {
    ALWAYS_ASK,
    ALWAYS_COPY,
    ALWAYS_MOVE,
}

/** Broad file grouping used for vault categories and per-type preferences. */
enum class MimeCategory {
    PHOTO,
    VIDEO,
    DOCUMENT,
    AUDIO,
    ARCHIVE,
    OTHER,
}

/** Result of asking the platform to remove an original file. Never inferred, always observed. */
enum class DeletionOutcome {
    /** The system reported the delete succeeded. */
    DELETED,

    /** The user declined the system confirmation dialog. */
    USER_CANCELLED,

    /** The document provider has no delete capability. */
    PROVIDER_NOT_SUPPORTED,

    /** The URI grant was revoked or expired before deletion could run. */
    PERMISSION_LOST,

    /** The original was already gone when deletion was attempted. */
    ALREADY_MISSING,

    /** The delete call failed for a provider-specific reason. */
    FAILED,

    /** Deletion has not been attempted yet. */
    NOT_ATTEMPTED,
}

/** Lifecycle of the original file relative to a vault item. */
enum class OriginalDeletionState {
    NOT_REQUESTED,
    REQUESTED,
    CONFIRMED_DELETED,
    DECLINED_BY_USER,
    FAILED,
}

/** Integrity state of the encrypted container backing a vault item. */
enum class VerificationStatus {
    NOT_VERIFIED,
    VERIFIED,
    FAILED,
}

/** State machine for a recoverable import transaction. Persisted, so crash recovery can resume. */
enum class ImportTransactionState {
    /** Row created; nothing written to disk yet. */
    PENDING,

    /** Free space and source access checked. */
    VALIDATED,

    /** Streaming encryption into `<uuid>.vault.part` is in progress. */
    ENCRYPTING,

    /** Encryption finished; the temporary container is being read back and authenticated. */
    VERIFYING,

    /** Verified and atomically renamed to `<uuid>.vault`. */
    COMMITTED,

    /** Terminal failure. The original was never touched. */
    FAILED,

    /** Cancelled by the user. Temporary artefacts are removed. */
    CANCELLED,
}

/** What a scan match means. Confidence is reported separately and always shown to the user. */
enum class ScanMatchType {
    /** Identical size, MIME and SHA-256 content hash. */
    EXACT_DUPLICATE,

    /** Same size and type, content not fully compared. */
    POSSIBLE_DUPLICATE,

    /** The original file for a moved item is still readable. */
    ORIGINAL_REMAINS,

    /** The platform trash may still hold a copy; TrueVault cannot inspect it. */
    TRASH_STATUS_UNKNOWN,

    /** The file lives on a cloud-backed provider, so other copies may exist off-device. */
    CLOUD_COPY_POSSIBLE,

    /** The location is outside anything TrueVault has been granted access to. */
    UNSUPPORTED_LOCATION,
}

/**
 * What this device can actually do for the Private Apps feature.
 *
 * TrueVault never simulates app cloning or APK virtualisation; if the platform offers nothing,
 * [NOT_SUPPORTED] is shown honestly.
 */
enum class PrivateAppsCapability {
    /** Android Private Space exists; TrueVault can walk the user through system setup. */
    SUPPORTED_GUIDED_SETUP,

    /** A launcher-level integration point is available. */
    SUPPORTED_LAUNCHER_INTEGRATION,

    /** No supported system integration on this Android version or device. */
    NOT_SUPPORTED,

    /** A device policy (work profile / managed device) blocks the feature. */
    MANAGED_DEVICE_RESTRICTED,

    /** Capability has not been probed yet. */
    UNKNOWN,
}
