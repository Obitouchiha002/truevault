package com.truevault.core.testing.crypto

import com.truevault.core.crypto.aead.AesGcm
import com.truevault.core.crypto.keystore.BiometricKeyInvalidatedException
import com.truevault.core.crypto.keystore.HardwareKeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * In-memory stand-in for the Android Keystore.
 *
 * The real Keystore is not available on the JVM, so the vault logic is tested against this. It
 * behaves like the real thing in the ways the logic depends on — distinct device and biometric
 * keys, and an invalidation state — while holding key bytes in memory, which the real one never
 * does.
 */
class FakeHardwareKeyStore(
    private var hardwareBacked: Boolean = true,
) : HardwareKeyStore {

    private val deviceKey: SecretKey = SecretKeySpec(AesGcm.randomBytes(32), "AES")
    private var biometricKey: SecretKey? = null
    var biometricInvalidated: Boolean = false

    override fun getOrCreateDeviceBoundKey(): SecretKey = deviceKey

    override fun getOrCreateBiometricBoundKey(): SecretKey =
        biometricKey ?: SecretKeySpec(AesGcm.randomBytes(32), "AES").also { biometricKey = it }

    override fun hasBiometricBoundKey(): Boolean = biometricKey != null

    override fun deleteBiometricBoundKey() {
        biometricKey = null
        biometricInvalidated = false
    }

    override fun biometricEncryptCipher(): Cipher =
        Cipher.getInstance(AesGcm.TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateBiometricBoundKey())
        }

    override fun biometricDecryptCipher(nonce: ByteArray): Cipher {
        if (biometricInvalidated) throw BiometricKeyInvalidatedException()
        val key = biometricKey ?: throw BiometricKeyInvalidatedException()
        return Cipher.getInstance(AesGcm.TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AesGcm.TAG_SIZE_BITS, nonce))
        }
    }

    override fun isHardwareBacked(): Boolean = hardwareBacked

    override fun isBiometricKeyInvalidated(): Boolean = biometricInvalidated
}
