package com.truevault.core.crypto.aead

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM primitives.
 *
 * GCM is authenticated encryption: if the tag does not verify, [decrypt] throws rather than
 * returning whatever plaintext happened to decrypt. Nothing in TrueVault ever consumes partially
 * decrypted output.
 *
 * A nonce is generated fresh from [SecureRandom] for every single encryption. Reusing a nonce with
 * the same key would destroy the security of GCM outright, so no API here accepts a caller-supplied
 * nonce for encryption.
 */
object AesGcm {

    const val TRANSFORMATION: String = "AES/GCM/NoPadding"
    const val KEY_SIZE_BITS: Int = 256
    const val NONCE_SIZE_BYTES: Int = 12
    const val TAG_SIZE_BITS: Int = 128
    const val TAG_SIZE_BYTES: Int = TAG_SIZE_BITS / 8

    private val secureRandom = SecureRandom()

    fun randomNonce(): ByteArray = ByteArray(NONCE_SIZE_BYTES).also(secureRandom::nextBytes)

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    /**
     * Encrypts [plaintext], optionally binding [associatedData] to the ciphertext.
     *
     * Associated data is authenticated but not encrypted: it is how a container states "this
     * ciphertext belongs to format version N, vault item X" in a way an attacker cannot alter
     * without the tag failing.
     */
    fun encrypt(
        key: SecretKey,
        plaintext: ByteArray,
        associatedData: ByteArray? = null,
    ): SealedData {
        val nonce = randomNonce()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, nonce))
            associatedData?.let(::updateAAD)
        }
        return SealedData(nonce = nonce, ciphertext = cipher.doFinal(plaintext))
    }

    /**
     * @throws GeneralSecurityException if the tag does not verify — wrong key, wrong associated
     * data, or tampered ciphertext. The caller must treat all three the same way: as a failure, not
     * as a reason to retry with looser checks.
     */
    fun decrypt(
        key: SecretKey,
        sealed: SealedData,
        associatedData: ByteArray? = null,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, sealed.nonce))
            associatedData?.let(::updateAAD)
        }
        return cipher.doFinal(sealed.ciphertext)
    }

    /** Encrypts with a caller-supplied [Cipher] that is already initialised — the BiometricPrompt path. */
    fun encryptWith(cipher: Cipher, plaintext: ByteArray): SealedData =
        SealedData(nonce = cipher.iv.copyOf(), ciphertext = cipher.doFinal(plaintext))

    /** Decrypts with a [Cipher] unlocked by BiometricPrompt. */
    fun decryptWith(cipher: Cipher, sealed: SealedData): ByteArray = cipher.doFinal(sealed.ciphertext)
}

/**
 * Nonce plus ciphertext-with-tag, kept together because they are meaningless apart.
 *
 * [toByteArray] is the on-disk and in-database layout: `nonce || ciphertext || tag`.
 */
class SealedData(val nonce: ByteArray, val ciphertext: ByteArray) {

    init {
        require(nonce.size == AesGcm.NONCE_SIZE_BYTES) {
            "GCM nonce must be ${AesGcm.NONCE_SIZE_BYTES} bytes"
        }
        require(ciphertext.size >= AesGcm.TAG_SIZE_BYTES) {
            "Ciphertext is shorter than the authentication tag"
        }
    }

    fun toByteArray(): ByteArray = nonce + ciphertext

    override fun equals(other: Any?): Boolean =
        other is SealedData && nonce.contentEquals(other.nonce) &&
            ciphertext.contentEquals(other.ciphertext)

    override fun hashCode(): Int = 31 * nonce.contentHashCode() + ciphertext.contentHashCode()

    /** Deliberately opaque: a stack trace or log line must never carry ciphertext. */
    override fun toString(): String = "SealedData(bytes=${nonce.size + ciphertext.size})"

    companion object {
        /**
         * Parses the `nonce || ciphertext || tag` layout.
         *
         * @throws IllegalArgumentException when [bytes] is too short to contain a nonce and a tag,
         * which is how a truncated blob is rejected before any crypto is attempted.
         */
        fun fromByteArray(bytes: ByteArray): SealedData {
            val minimum = AesGcm.NONCE_SIZE_BYTES + AesGcm.TAG_SIZE_BYTES
            require(bytes.size >= minimum) { "Sealed blob is truncated" }
            return SealedData(
                nonce = bytes.copyOfRange(0, AesGcm.NONCE_SIZE_BYTES),
                ciphertext = bytes.copyOfRange(AesGcm.NONCE_SIZE_BYTES, bytes.size),
            )
        }
    }
}
