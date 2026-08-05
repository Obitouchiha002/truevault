package com.truevault.core.crypto.vault

import kotlinx.coroutines.flow.Flow

/**
 * Everything persisted about the vault lock.
 *
 * What is **not** here is as important as what is: no password, no PIN, no recovery phrase, no
 * plain key, and no password verifier. Verification happens by unwrapping [sealedMasterKey] — if the
 * GCM tag fails, the password was wrong. That removes the need for a separate hash to attack.
 */
data class VaultLockRecord(
    /** Which [com.truevault.core.crypto.kdf.KdfParams] version produced the wrapping key. */
    val kdfVersion: Int,

    /** Random per-vault salt. Public by design; its job is to defeat precomputation. */
    val salt: ByteArray,

    /**
     * The vault master key, sealed twice:
     *
     * ```
     * sealedMasterKey = deviceBoundKey( passwordDerivedKey( masterKey ) )
     * ```
     *
     * The inner layer means the blob is worthless without the password. The outer layer means it is
     * worthless off this device, because the device-bound key cannot leave the Android Keystore —
     * so the password cannot be attacked on a machine of the attacker's choosing.
     */
    val sealedMasterKey: ByteArray,

    /** The same master key sealed by the biometric-bound key. Null unless biometrics are enabled. */
    val biometricSealedMasterKey: ByteArray?,

    val createdAtMillis: Long,

    val updatedAtMillis: Long,
) {
    val biometricUnlockEnabled: Boolean get() = biometricSealedMasterKey != null

    override fun equals(other: Any?): Boolean = other is VaultLockRecord &&
        kdfVersion == other.kdfVersion &&
        salt.contentEquals(other.salt) &&
        sealedMasterKey.contentEquals(other.sealedMasterKey) &&
        biometricSealedMasterKey.contentEqualsNullable(other.biometricSealedMasterKey) &&
        createdAtMillis == other.createdAtMillis &&
        updatedAtMillis == other.updatedAtMillis

    override fun hashCode(): Int {
        var result = kdfVersion
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + sealedMasterKey.contentHashCode()
        result = 31 * result + (biometricSealedMasterKey?.contentHashCode() ?: 0)
        result = 31 * result + createdAtMillis.hashCode()
        result = 31 * result + updatedAtMillis.hashCode()
        return result
    }

    /** Never print the blobs. */
    override fun toString(): String =
        "VaultLockRecord(kdfVersion=$kdfVersion, biometric=$biometricUnlockEnabled)"
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this == null && other == null -> true
    this == null || other == null -> false
    else -> contentEquals(other)
}

/**
 * Persistence for [VaultLockRecord].
 *
 * Declared here and implemented in `:core:datastore`, so the crypto module stays free of storage
 * concerns and can be unit tested against an in-memory implementation.
 */
interface VaultLockStore {
    val record: Flow<VaultLockRecord?>

    suspend fun read(): VaultLockRecord?

    suspend fun write(record: VaultLockRecord)

    /** Removes the lock record entirely. Used by "reset vault", never as part of normal unlock. */
    suspend fun clear()
}
