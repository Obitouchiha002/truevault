package com.truevault.core.crypto.vault

import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.common.log.SecureLog
import com.truevault.core.common.result.Outcome
import com.truevault.core.common.result.asFailure
import com.truevault.core.common.result.asSuccess
import com.truevault.core.common.time.TimeProvider
import com.truevault.core.crypto.aead.AesGcm
import com.truevault.core.crypto.aead.SealedData
import com.truevault.core.crypto.kdf.KdfParams
import com.truevault.core.crypto.kdf.PasswordKeyDerivation
import com.truevault.core.crypto.kdf.wipe
import com.truevault.core.crypto.keystore.BiometricKeyInvalidatedException
import com.truevault.core.crypto.keystore.HardwareKeyStore
import com.truevault.core.crypto.keystore.KeyStoreUnavailableException
import com.truevault.core.crypto.recovery.RecoveryKey
import com.truevault.core.crypto.session.VaultSession
import com.truevault.core.model.LockThrottle
import com.truevault.core.model.VaultError
import com.truevault.core.model.VaultLockType
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "VaultKeys"

/**
 * Why biometric enrolment can or cannot start.
 *
 * Each value maps to a different sentence the user can act on — which is the whole reason this is
 * not a nullable Cipher.
 */
sealed interface BiometricSetup {
    data class Ready(val cipher: Cipher) : BiometricSetup

    /** The vault must be open first: enabling biometrics cannot bypass proving the password. */
    data object VaultLocked : BiometricSetup

    /** No fingerprint or face is enrolled on the device. Fixable in system settings. */
    data object NoBiometricEnrolled : BiometricSetup

    /** The Keystore declined to create an auth-bound key on this device. Not fixable by the user. */
    data object KeystoreRefused : BiometricSetup
}

/** Associated data binds a sealed blob to its purpose, so blobs cannot be swapped between slots. */
private val AAD_PASSWORD_LAYER = "truevault.master.password.v1".toByteArray()
private val AAD_DEVICE_LAYER = "truevault.master.device.v1".toByteArray()
private val AAD_RECOVERY_LAYER = "truevault.master.recovery.v1".toByteArray()

/**
 * Owns the vault master key: creating it, opening it, and re-sealing it.
 *
 * ```
 * password ──Argon2id(salt)──▶ password key ──seals──▶ master key
 *                                                       │
 *                          Keystore device key ──seals──┘   (stored on disk)
 *
 * Keystore biometric key ──seals──▶ master key                (optional second path)
 * ```
 *
 * Why two layers rather than one: the password layer means the blob is useless without what the
 * user knows, and the device layer means it is useless without this device's Keystore. An attacker
 * who copies the file off the phone cannot mount an offline guessing attack at all, because the
 * outer layer needs a key that cannot leave the hardware.
 *
 * The master key never leaves this module. It is handed to [VaultSession], which keeps it in memory
 * only, and callers in other modules receive success or failure — never the key.
 */
@Singleton
class VaultKeyManager @Inject constructor(
    private val keyStore: HardwareKeyStore,
    private val lockStore: VaultLockStore,
    private val session: VaultSession,
    private val timeProvider: TimeProvider,
    @param:Dispatcher(TrueVaultDispatcher.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    suspend fun isVaultConfigured(): Boolean = lockStore.read() != null

    /**
     * Creates the vault lock for the first time and opens the session.
     *
     * The caller owns [password] and must wipe it; this function does not, because the caller may
     * still need it to compare against a confirmation field.
     */
    suspend fun createLock(
        password: CharArray,
        lockType: VaultLockType = VaultLockType.PASSPHRASE,
    ): Outcome<Unit> = withContext(defaultDispatcher) {
        if (lockStore.read() != null) {
            return@withContext VaultError.Unknown("A vault already exists on this device.").asFailure()
        }

        val masterKeyBytes = AesGcm.randomBytes(AesGcm.KEY_SIZE_BITS / 8)
        val salt = PasswordKeyDerivation.randomSalt()

        try {
            val params = KdfParams.CURRENT
            val passwordKey = PasswordKeyDerivation.deriveKey(password, salt, params)
            val inner = AesGcm.encrypt(passwordKey, masterKeyBytes, AAD_PASSWORD_LAYER)
            val outer = AesGcm.encrypt(
                keyStore.getOrCreateDeviceBoundKey(),
                inner.toByteArray(),
                AAD_DEVICE_LAYER,
            )

            val now = timeProvider.currentTimeMillis()
            lockStore.write(
                VaultLockRecord(
                    kdfVersion = params.version,
                    salt = salt,
                    sealedMasterKey = outer.toByteArray(),
                    biometricSealedMasterKey = null,
                    recoverySealedMasterKey = null,
                    recoverySalt = null,
                    recoveryCheckValue = null,
                    lockType = lockType,
                    failedAttempts = 0,
                    lastFailedAtMillis = null,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )

            session.setConfigured(true)
            session.open(SecretKeySpec(masterKeyBytes, "AES"))
            SecureLog.i(TAG, "Vault lock created")
            Unit.asSuccess()
        } catch (e: KeyStoreUnavailableException) {
            SecureLog.e(TAG, "Keystore unavailable while creating the lock", e)
            VaultError.EncryptionFailed.asFailure()
        } catch (e: GeneralSecurityException) {
            SecureLog.e(TAG, "Failed to seal the master key", e)
            VaultError.EncryptionFailed.asFailure()
        } finally {
            masterKeyBytes.wipe()
        }
    }

    /**
     * Opens the vault with the password.
     *
     * A wrong password shows up as a GCM authentication failure, which is reported as
     * [VaultError.AuthenticationRequired] — the same error for "wrong password" and "tampered blob",
     * because distinguishing them for the user would also distinguish them for an attacker.
     */
    suspend fun unlockWithPassword(password: CharArray): Outcome<Unit> =
        withContext(defaultDispatcher) {
            val record = lockStore.read()
                ?: return@withContext VaultError.Unknown("No vault has been created yet.").asFailure()

            val params = KdfParams.forVersion(record.kdfVersion)
                ?: return@withContext VaultError.UnsupportedFormatVersion(
                    foundVersion = record.kdfVersion,
                    maxSupportedVersion = KdfParams.CURRENT.version,
                ).asFailure()

            // The throttle is checked before the KDF runs, so a locked-out attacker cannot even
            // spend the device's CPU, and a legitimate user is told how long is left.
            val waitMillis = LockThrottle.remainingMillis(
                consecutiveFailures = record.failedAttempts,
                lastFailedAtMillis = record.lastFailedAtMillis,
                nowMillis = timeProvider.currentTimeMillis(),
            )
            if (waitMillis > 0) {
                return@withContext VaultError.TooManyAttempts(waitMillis).asFailure()
            }

            var masterKeyBytes: ByteArray? = null
            try {
                val innerBytes = AesGcm.decrypt(
                    keyStore.getOrCreateDeviceBoundKey(),
                    SealedData.fromByteArray(record.sealedMasterKey),
                    AAD_DEVICE_LAYER,
                )
                val passwordKey = PasswordKeyDerivation.deriveKey(password, record.salt, params)
                masterKeyBytes = AesGcm.decrypt(
                    passwordKey,
                    SealedData.fromByteArray(innerBytes),
                    AAD_PASSWORD_LAYER,
                )
                innerBytes.wipe()

                session.open(SecretKeySpec(masterKeyBytes, "AES"))
                clearFailedAttempts(record)
                Unit.asSuccess()
            } catch (e: GeneralSecurityException) {
                // Expected on every wrong secret. Not an error worth logging in detail.
                SecureLog.d(TAG, "Unlock rejected")
                recordFailedAttempt(record)
                VaultError.AuthenticationRequired.asFailure()
            } catch (e: IllegalArgumentException) {
                SecureLog.e(TAG, "Stored lock record is malformed", e)
                VaultError.IntegrityCheckFailed.asFailure()
            } catch (e: KeyStoreUnavailableException) {
                SecureLog.e(TAG, "Keystore unavailable during unlock", e)
                VaultError.DecryptionFailed.asFailure()
            } finally {
                masterKeyBytes?.wipe()
            }
        }

    /**
     * A [Cipher] for `BiometricPrompt.CryptoObject`, or null when biometric unlock is not set up.
     *
     * Returns null rather than throwing when the key was invalidated by a new biometric enrolment;
     * the caller then falls back to the password, which is the correct behaviour.
     */
    suspend fun biometricUnlockCipher(): Cipher? {
        val record = lockStore.read() ?: return null
        val sealed = record.biometricSealedMasterKey ?: return null
        return try {
            keyStore.biometricDecryptCipher(SealedData.fromByteArray(sealed).nonce)
        } catch (e: BiometricKeyInvalidatedException) {
            SecureLog.w(TAG, "Biometric key invalidated; falling back to password")
            null
        } catch (e: Exception) {
            SecureLog.w(TAG, "Biometric cipher unavailable (${e.javaClass.simpleName})")
            null
        }
    }

    /** Opens the vault with a [Cipher] that BiometricPrompt has already authenticated. */
    suspend fun unlockWithBiometric(cipher: Cipher): Outcome<Unit> =
        withContext(defaultDispatcher) {
            val record = lockStore.read()
            val sealed = record?.biometricSealedMasterKey
                ?: return@withContext VaultError.AuthenticationRequired.asFailure()

            var masterKeyBytes: ByteArray? = null
            try {
                masterKeyBytes = AesGcm.decryptWith(cipher, SealedData.fromByteArray(sealed))
                session.open(SecretKeySpec(masterKeyBytes, "AES"))
                Unit.asSuccess()
            } catch (e: GeneralSecurityException) {
                SecureLog.w(TAG, "Biometric unlock failed")
                VaultError.AuthenticationRequired.asFailure()
            } finally {
                masterKeyBytes?.wipe()
            }
        }

    /**
     * A [Cipher] for sealing the master key behind biometrics. Requires an unlocked session, because
     * enabling biometrics must never be a way to bypass proving you know the password.
     */
    fun biometricEnrolCipher(): Cipher? =
        (prepareBiometricEnrolment() as? BiometricSetup.Ready)?.cipher

    /**
     * Prepares biometric enrolment, reporting **why** it cannot proceed.
     *
     * The previous version returned a bare null, so a device that refused to create the key looked
     * identical to a user who had not opted in: the switch flipped back and nothing was said. A
     * silent failure in an authentication feature is worse than no feature, because the user walks
     * away believing their vault is protected by a fingerprint that was never set up.
     */
    fun prepareBiometricEnrolment(): BiometricSetup {
        if (!session.isUnlocked) return BiometricSetup.VaultLocked

        return try {
            BiometricSetup.Ready(keyStore.biometricEncryptCipher())
        } catch (e: KeyStoreUnavailableException) {
            SecureLog.w(TAG, "Keystore refused to create the biometric key")
            BiometricSetup.KeystoreRefused
        } catch (e: IllegalStateException) {
            // Android's wording varies by version; the cause is always the same one.
            SecureLog.w(TAG, "Biometric key needs an enrolled biometric")
            BiometricSetup.NoBiometricEnrolled
        } catch (e: InvalidAlgorithmParameterException) {
            SecureLog.w(TAG, "Biometric key parameters rejected by this device")
            BiometricSetup.NoBiometricEnrolled
        } catch (e: Exception) {
            SecureLog.w(TAG, "Biometric enrolment unavailable (${e.javaClass.simpleName})")
            BiometricSetup.KeystoreRefused
        }
    }

    /** Stores the biometric-sealed copy of the master key, after BiometricPrompt authenticated. */
    suspend fun enableBiometricUnlock(cipher: Cipher): Outcome<Unit> =
        withContext(defaultDispatcher) {
            val record = lockStore.read()
                ?: return@withContext VaultError.Unknown("No vault has been created yet.").asFailure()
            val masterKey = session.masterKeyOrNull()
                ?: return@withContext VaultError.AuthenticationRequired.asFailure()

            try {
                val sealed = AesGcm.encryptWith(cipher, masterKey.encoded)
                lockStore.write(
                    record.copy(
                        biometricSealedMasterKey = sealed.toByteArray(),
                        updatedAtMillis = timeProvider.currentTimeMillis(),
                    ),
                )
                SecureLog.i(TAG, "Biometric unlock enabled")
                Unit.asSuccess()
            } catch (e: GeneralSecurityException) {
                SecureLog.e(TAG, "Could not seal the master key for biometrics", e)
                VaultError.EncryptionFailed.asFailure()
            }
        }

    suspend fun disableBiometricUnlock(): Outcome<Unit> = withContext(defaultDispatcher) {
        val record = lockStore.read()
            ?: return@withContext VaultError.Unknown("No vault has been created yet.").asFailure()

        lockStore.write(
            record.copy(
                biometricSealedMasterKey = null,
                updatedAtMillis = timeProvider.currentTimeMillis(),
            ),
        )
        keyStore.deleteBiometricBoundKey()
        SecureLog.i(TAG, "Biometric unlock disabled")
        Unit.asSuccess()
    }

    /**
     * Re-seals the existing master key under a new password.
     *
     * The master key itself is unchanged, so no vault file has to be re-encrypted — which is the
     * whole reason a separate master key exists instead of encrypting files with the password key.
     * Any biometric copy is dropped, because the user must re-confirm that pairing.
     */
    suspend fun changePassword(
        currentPassword: CharArray,
        newPassword: CharArray,
    ): Outcome<Unit> = withContext(defaultDispatcher) {
        when (val unlock = unlockWithPassword(currentPassword)) {
            is Outcome.Failure -> return@withContext unlock
            is Outcome.Success -> Unit
        }

        val record = lockStore.read()
            ?: return@withContext VaultError.Unknown("No vault has been created yet.").asFailure()
        val masterKey = session.masterKeyOrNull()
            ?: return@withContext VaultError.AuthenticationRequired.asFailure()

        try {
            val params = KdfParams.CURRENT
            val salt = PasswordKeyDerivation.randomSalt()
            val passwordKey = PasswordKeyDerivation.deriveKey(newPassword, salt, params)
            val inner = AesGcm.encrypt(passwordKey, masterKey.encoded, AAD_PASSWORD_LAYER)
            val outer = AesGcm.encrypt(
                keyStore.getOrCreateDeviceBoundKey(),
                inner.toByteArray(),
                AAD_DEVICE_LAYER,
            )

            lockStore.write(
                record.copy(
                    kdfVersion = params.version,
                    salt = salt,
                    sealedMasterKey = outer.toByteArray(),
                    biometricSealedMasterKey = null,
                    updatedAtMillis = timeProvider.currentTimeMillis(),
                ),
            )
            keyStore.deleteBiometricBoundKey()
            Unit.asSuccess()
        } catch (e: GeneralSecurityException) {
            SecureLog.e(TAG, "Password change failed", e)
            VaultError.EncryptionFailed.asFailure()
        }
    }

    /**
     * Generates a recovery key and seals the master key under it.
     *
     * Requires an unlocked session: generating a way back in must never be possible without first
     * having proved a way in. The returned characters are the caller's to display once and wipe —
     * they are not stored anywhere, which is exactly why losing them is unrecoverable.
     */
    suspend fun generateRecoveryKey(): Outcome<CharArray> = withContext(defaultDispatcher) {
        val record = lockStore.read()
            ?: return@withContext VaultError.Unknown("No vault has been created yet.").asFailure()
        val masterKey = session.masterKeyOrNull()
            ?: return@withContext VaultError.AuthenticationRequired.asFailure()

        val recoveryKey = RecoveryKey.generate()

        try {
            val params = KdfParams.CURRENT
            val salt = PasswordKeyDerivation.randomSalt()
            val derived = PasswordKeyDerivation.deriveKey(recoveryKey, salt, params)
            val sealed = AesGcm.encrypt(derived, masterKey.encoded, AAD_RECOVERY_LAYER)

            lockStore.write(
                record.copy(
                    recoverySealedMasterKey = sealed.toByteArray(),
                    recoverySalt = salt,
                    recoveryCheckValue = RecoveryKey.checkValue(recoveryKey),
                    updatedAtMillis = timeProvider.currentTimeMillis(),
                ),
            )
            SecureLog.i(TAG, "Recovery key generated")
            recoveryKey.asSuccess()
        } catch (e: GeneralSecurityException) {
            recoveryKey.wipe()
            SecureLog.e(TAG, "Recovery key generation failed", e)
            VaultError.EncryptionFailed.asFailure()
        }
    }

    /**
     * Opens the vault with a recovery key.
     *
     * The check value distinguishes a mistyped key from a damaged record, so the user gets an
     * actionable message rather than a generic failure at the worst possible moment.
     */
    suspend fun unlockWithRecoveryKey(input: String): Outcome<Unit> =
        withContext(defaultDispatcher) {
            val record = lockStore.read()
                ?: return@withContext VaultError.Unknown("No vault has been created yet.").asFailure()

            val sealed = record.recoverySealedMasterKey
            val salt = record.recoverySalt
            if (sealed == null || salt == null) {
                return@withContext VaultError.Unknown(
                    "No recovery key was ever generated for this vault.",
                ).asFailure()
            }

            val normalised = RecoveryKey.normalise(input)
                ?: return@withContext VaultError.AuthenticationRequired.asFailure()

            record.recoveryCheckValue?.let { expected ->
                if (!RecoveryKey.checkValue(normalised).contentEquals(expected)) {
                    normalised.wipe()
                    return@withContext VaultError.AuthenticationRequired.asFailure()
                }
            }

            var masterKeyBytes: ByteArray? = null
            try {
                val params = KdfParams.forVersion(record.kdfVersion) ?: KdfParams.CURRENT
                val derived = PasswordKeyDerivation.deriveKey(normalised, salt, params)
                masterKeyBytes = AesGcm.decrypt(
                    derived,
                    SealedData.fromByteArray(sealed),
                    AAD_RECOVERY_LAYER,
                )
                session.open(SecretKeySpec(masterKeyBytes, "AES"))
                Unit.asSuccess()
            } catch (e: GeneralSecurityException) {
                VaultError.AuthenticationRequired.asFailure()
            } catch (e: IllegalArgumentException) {
                VaultError.IntegrityCheckFailed.asFailure()
            } finally {
                normalised.wipe()
                masterKeyBytes?.wipe()
            }
        }

    suspend fun hasRecoveryKey(): Boolean = lockStore.read()?.recoveryKeyConfigured == true

    /** How the vault is locked, so the unlock screen can show a keypad or a text field. */
    suspend fun lockType(): VaultLockType? = lockStore.read()?.lockType

    /** Milliseconds the user must wait before the next attempt is accepted. Zero when free. */
    suspend fun throttleRemainingMillis(): Long {
        val record = lockStore.read() ?: return 0L
        return LockThrottle.remainingMillis(
            consecutiveFailures = record.failedAttempts,
            lastFailedAtMillis = record.lastFailedAtMillis,
            nowMillis = timeProvider.currentTimeMillis(),
        )
    }

    private suspend fun recordFailedAttempt(record: VaultLockRecord) {
        lockStore.write(
            record.copy(
                failedAttempts = record.failedAttempts + 1,
                lastFailedAtMillis = timeProvider.currentTimeMillis(),
                updatedAtMillis = timeProvider.currentTimeMillis(),
            ),
        )
    }

    private suspend fun clearFailedAttempts(record: VaultLockRecord) {
        if (record.failedAttempts == 0 && record.lastFailedAtMillis == null) return
        lockStore.write(
            record.copy(
                failedAttempts = 0,
                lastFailedAtMillis = null,
                updatedAtMillis = timeProvider.currentTimeMillis(),
            ),
        )
    }

    /** The master key, for modules that legitimately need to wrap per-file keys. Null when locked. */
    internal fun masterKeyOrNull(): SecretKey? = session.masterKeyOrNull()
}
