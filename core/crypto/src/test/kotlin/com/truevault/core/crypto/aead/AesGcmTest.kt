package com.truevault.core.crypto.aead

import com.google.common.truth.Truth.assertThat
import java.security.GeneralSecurityException
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertThrows
import org.junit.Test

class AesGcmTest {

    private val key = SecretKeySpec(AesGcm.randomBytes(32), "AES")
    private val otherKey = SecretKeySpec(AesGcm.randomBytes(32), "AES")
    private val plaintext = "the quick brown fox".toByteArray()

    @Test
    fun `round trip returns the original bytes`() {
        val sealed = AesGcm.encrypt(key, plaintext)

        assertThat(AesGcm.decrypt(key, sealed)).isEqualTo(plaintext)
    }

    @Test
    fun `the same plaintext produces different ciphertext every time`() {
        val first = AesGcm.encrypt(key, plaintext)
        val second = AesGcm.encrypt(key, plaintext)

        assertThat(first.nonce).isNotEqualTo(second.nonce)
        assertThat(first.ciphertext).isNotEqualTo(second.ciphertext)
    }

    @Test
    fun `the wrong key is rejected rather than returning garbage`() {
        val sealed = AesGcm.encrypt(key, plaintext)

        assertThrows(GeneralSecurityException::class.java) {
            AesGcm.decrypt(otherKey, sealed)
        }
    }

    @Test
    fun `a flipped ciphertext bit fails authentication`() {
        val sealed = AesGcm.encrypt(key, plaintext)
        val tampered = sealed.ciphertext.copyOf().also { it[0] = (it[0] + 1).toByte() }

        assertThrows(GeneralSecurityException::class.java) {
            AesGcm.decrypt(key, SealedData(sealed.nonce, tampered))
        }
    }

    @Test
    fun `a flipped nonce bit fails authentication`() {
        val sealed = AesGcm.encrypt(key, plaintext)
        val tampered = sealed.nonce.copyOf().also { it[0] = (it[0] + 1).toByte() }

        assertThrows(GeneralSecurityException::class.java) {
            AesGcm.decrypt(key, SealedData(tampered, sealed.ciphertext))
        }
    }

    @Test
    fun `associated data must match on decryption`() {
        val sealed = AesGcm.encrypt(key, plaintext, associatedData = "context-a".toByteArray())

        assertThrows(GeneralSecurityException::class.java) {
            AesGcm.decrypt(key, sealed, associatedData = "context-b".toByteArray())
        }
        assertThat(AesGcm.decrypt(key, sealed, associatedData = "context-a".toByteArray()))
            .isEqualTo(plaintext)
    }

    @Test
    fun `serialised form survives a round trip through bytes`() {
        val sealed = AesGcm.encrypt(key, plaintext)

        val restored = SealedData.fromByteArray(sealed.toByteArray())

        assertThat(AesGcm.decrypt(key, restored)).isEqualTo(plaintext)
    }

    @Test
    fun `a truncated blob is rejected before any crypto runs`() {
        val sealed = AesGcm.encrypt(key, plaintext)
        val truncated = sealed.toByteArray().copyOf(10)

        assertThrows(IllegalArgumentException::class.java) {
            SealedData.fromByteArray(truncated)
        }
    }

    @Test
    fun `an empty payload still round trips`() {
        val sealed = AesGcm.encrypt(key, ByteArray(0))

        assertThat(AesGcm.decrypt(key, sealed)).isEmpty()
    }

    @Test
    fun `toString never reveals ciphertext`() {
        val sealed = AesGcm.encrypt(key, plaintext)

        assertThat(sealed.toString()).doesNotContain("ciphertext=")
        assertThat(sealed.toString()).contains("bytes=")
    }
}
