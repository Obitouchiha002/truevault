package com.truevault.core.crypto.kdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordKeyDerivationTest {

    // A deliberately cheap parameter set: these tests check behaviour, not cost. The production
    // cost of KdfParams.CURRENT is exercised once, in `current parameters are memory hard`.
    private val cheap = KdfParams(
        version = 1,
        memoryKib = 256,
        iterations = 1,
        parallelism = 1,
        outputLengthBytes = 32,
    )

    @Test
    fun `the same password and salt always derive the same key`() {
        val salt = PasswordKeyDerivation.randomSalt()

        val first = PasswordKeyDerivation.deriveKey("correct horse".toCharArray(), salt, cheap)
        val second = PasswordKeyDerivation.deriveKey("correct horse".toCharArray(), salt, cheap)

        assertThat(first.encoded).isEqualTo(second.encoded)
    }

    @Test
    fun `a different salt derives a different key from the same password`() {
        val password = "correct horse".toCharArray()

        val first = PasswordKeyDerivation.deriveKey(password, PasswordKeyDerivation.randomSalt(), cheap)
        val second = PasswordKeyDerivation.deriveKey(password, PasswordKeyDerivation.randomSalt(), cheap)

        assertThat(first.encoded).isNotEqualTo(second.encoded)
    }

    @Test
    fun `a one character difference derives a completely different key`() {
        val salt = PasswordKeyDerivation.randomSalt()

        val first = PasswordKeyDerivation.deriveKey("passphrase".toCharArray(), salt, cheap)
        val second = PasswordKeyDerivation.deriveKey("passphrasf".toCharArray(), salt, cheap)

        assertThat(first.encoded).isNotEqualTo(second.encoded)
    }

    @Test
    fun `derived keys are the full AES-256 length`() {
        val key = PasswordKeyDerivation.deriveKey("x".toCharArray(), PasswordKeyDerivation.randomSalt(), cheap)

        assertThat(key.encoded).hasLength(32)
    }

    @Test
    fun `unicode and emoji passwords are accepted, not silently mangled`() {
        val salt = PasswordKeyDerivation.randomSalt()
        val unicode = "पासवर्ड-🔐-ключ".toCharArray()

        val first = PasswordKeyDerivation.deriveKey(unicode, salt, cheap)
        val second = PasswordKeyDerivation.deriveKey("पासवर्ड-🔐-ключ".toCharArray(), salt, cheap)

        assertThat(first.encoded).isEqualTo(second.encoded)
    }

    @Test
    fun `a salt shorter than the required length is rejected`() {
        val tooShort = ByteArray(4)

        val error = runCatching {
            PasswordKeyDerivation.deriveKey("x".toCharArray(), tooShort, cheap)
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `current parameters are memory hard and versioned`() {
        assertThat(KdfParams.CURRENT.version).isEqualTo(1)
        assertThat(KdfParams.CURRENT.memoryKib).isAtLeast(32 * 1024)
        assertThat(KdfParams.CURRENT.iterations).isAtLeast(3)
        assertThat(KdfParams.forVersion(1)).isEqualTo(KdfParams.V1)
    }

    @Test
    fun `an unknown parameter version is reported rather than guessed`() {
        assertThat(KdfParams.forVersion(99)).isNull()
    }

    @Test
    fun `wiping a password buffer leaves no characters behind`() {
        val password = "secret".toCharArray()

        password.wipe()

        assertThat(password.none { it != '\u0000' }).isTrue()
    }
}
