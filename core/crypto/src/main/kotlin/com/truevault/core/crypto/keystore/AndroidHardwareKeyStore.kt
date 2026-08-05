package com.truevault.core.crypto.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import com.truevault.core.common.log.SecureLog
import com.truevault.core.crypto.aead.AesGcm
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "KeyStore"
private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val DEVICE_KEY_ALIAS = "truevault.device.v1"
private const val BIOMETRIC_KEY_ALIAS = "truevault.biometric.v1"

@Singleton
class AndroidHardwareKeyStore @Inject constructor() : HardwareKeyStore {

    private val keyStore: KeyStore by lazy {
        try {
            KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        } catch (e: Exception) {
            throw KeyStoreUnavailableException("Android Keystore could not be opened: ${e.javaClass.simpleName}")
        }
    }

    @Synchronized
    override fun getOrCreateDeviceBoundKey(): SecretKey =
        existingKey(DEVICE_KEY_ALIAS) ?: generateKey(DEVICE_KEY_ALIAS, requireUserAuth = false)

    @Synchronized
    override fun getOrCreateBiometricBoundKey(): SecretKey =
        existingKey(BIOMETRIC_KEY_ALIAS) ?: generateKey(BIOMETRIC_KEY_ALIAS, requireUserAuth = true)

    override fun hasBiometricBoundKey(): Boolean = runCatching {
        keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)
    }.getOrDefault(false)

    @Synchronized
    override fun deleteBiometricBoundKey() {
        try {
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
            }
        } catch (e: KeyStoreException) {
            SecureLog.e(TAG, "Could not delete the biometric key", e)
        }
    }

    override fun biometricEncryptCipher(): Cipher =
        Cipher.getInstance(AesGcm.TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateBiometricBoundKey())
        }

    override fun biometricDecryptCipher(nonce: ByteArray): Cipher {
        val key = existingKey(BIOMETRIC_KEY_ALIAS)
            ?: throw KeyStoreUnavailableException("No biometric key is configured")
        return try {
            Cipher.getInstance(AesGcm.TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AesGcm.TAG_SIZE_BITS, nonce))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            SecureLog.w(TAG, "Biometric key was invalidated by a new enrolment")
            throw BiometricKeyInvalidatedException()
        }
    }

    override fun isHardwareBacked(): Boolean = try {
        val key = getOrCreateDeviceBoundKey()
        val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEY_STORE)
        val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.securityLevel != KeyProperties.SECURITY_LEVEL_SOFTWARE
        } else {
            @Suppress("DEPRECATION")
            info.isInsideSecureHardware
        }
    } catch (e: Exception) {
        // Unknown is reported as "not hardware backed": the app must never overstate protection.
        SecureLog.w(TAG, "Could not determine Keystore backing (${e.javaClass.simpleName})")
        false
    }

    override fun isBiometricKeyInvalidated(): Boolean {
        val key = existingKey(BIOMETRIC_KEY_ALIAS) ?: return false
        return try {
            Cipher.getInstance(AesGcm.TRANSFORMATION).init(Cipher.ENCRYPT_MODE, key)
            false
        } catch (e: KeyPermanentlyInvalidatedException) {
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun existingKey(alias: String): SecretKey? = try {
        keyStore.getKey(alias, null) as? SecretKey
    } catch (e: Exception) {
        SecureLog.w(TAG, "Key lookup failed (${e.javaClass.simpleName})")
        null
    }

    private fun generateKey(alias: String, requireUserAuth: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AesGcm.KEY_SIZE_BITS)
            // Every encryption must use a fresh, Keystore-generated IV. Allowing a caller-supplied
            // IV would make nonce reuse possible from outside this class.
            .setRandomizedEncryptionRequired(true)
            .apply {
                if (requireUserAuth) {
                    setUserAuthenticationRequired(true)
                    // A newly enrolled fingerprint or face must not inherit access to the vault.
                    setInvalidatedByBiometricEnrollment(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // 0 seconds = authenticate for every single use, biometrics only.
                        setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // Ask for the secure element when the device has one. This is a request, not a
                    // guarantee; isHardwareBacked() reports what was actually granted.
                    setIsStrongBoxBacked(true)
                }
            }
            .build()

        return try {
            generateWith(spec)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // StrongBox is absent or full on this device; fall back to the TEE-backed Keystore.
                SecureLog.w(TAG, "StrongBox unavailable, falling back to the standard Keystore")
                generateWith(
                    KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(AesGcm.KEY_SIZE_BITS)
                        .setRandomizedEncryptionRequired(true)
                        .apply {
                            if (requireUserAuth) {
                                setUserAuthenticationRequired(true)
                                setInvalidatedByBiometricEnrollment(true)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    setUserAuthenticationParameters(
                                        0,
                                        KeyProperties.AUTH_BIOMETRIC_STRONG,
                                    )
                                }
                            }
                        }
                        .build(),
                )
            } else {
                throw KeyStoreUnavailableException("Key generation failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun generateWith(spec: KeyGenParameterSpec): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply { init(spec) }
            .generateKey()
}
