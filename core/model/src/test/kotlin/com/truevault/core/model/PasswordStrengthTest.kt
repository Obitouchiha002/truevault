package com.truevault.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordStrengthTest {

    private fun assess(password: String) = assessPassword(password.toCharArray())

    @Test
    fun `anything shorter than eight characters fails the minimum`() {
        val result = assess("short12")

        assertThat(result.strength).isEqualTo(PasswordStrength.TOO_SHORT)
        assertThat(result.isAcceptable).isFalse()
        assertThat(result.suggestions).contains(PasswordSuggestion.MAKE_IT_LONGER)
    }

    @Test
    fun `exactly eight characters meets the minimum`() {
        assertThat(assess("eight-ch").meetsMinimumLength).isTrue()
    }

    @Test
    fun `a top-of-the-list password is called out`() {
        val result = assess("password")

        assertThat(result.strength).isEqualTo(PasswordStrength.WEAK)
        assertThat(result.suggestions).contains(PasswordSuggestion.AVOID_COMMON_PASSWORD)
    }

    @Test
    fun `the common-password check ignores case`() {
        assertThat(assess("PASSWORD").suggestions).contains(PasswordSuggestion.AVOID_COMMON_PASSWORD)
    }

    @Test
    fun `a repeated character run scores weak and says why`() {
        val result = assess("aaaaaaaaaaaa")

        assertThat(result.strength).isEqualTo(PasswordStrength.WEAK)
        assertThat(result.suggestions).contains(PasswordSuggestion.AVOID_REPETITION)
    }

    @Test
    fun `a four word passphrase beats a short scramble`() {
        val passphrase = assess("correct horse battery staple")
        val scramble = assess("P@ss1!xY")

        assertThat(passphrase.estimatedBits).isGreaterThan(scramble.estimatedBits)
        assertThat(passphrase.strength).isEqualTo(PasswordStrength.STRONG)
    }

    @Test
    fun `a long passphrase is not told to add symbols`() {
        val result = assess("my dog ate the entire birthday cake")

        assertThat(result.suggestions).doesNotContain(PasswordSuggestion.MIX_CHARACTER_TYPES)
        assertThat(result.suggestions).doesNotContain(PasswordSuggestion.USE_A_PASSPHRASE)
    }

    @Test
    fun `a short single-case password is nudged toward mixing character types`() {
        val result = assess("abcdefghij")

        assertThat(result.suggestions).contains(PasswordSuggestion.MIX_CHARACTER_TYPES)
    }

    @Test
    fun `non-ASCII passwords are accepted and scored, never rejected`() {
        val hindi = assess("मेरा गुप्त पासवर्ड")
        val emoji = assess("🔐🌊🎸🚀🌙🔥🎯🍜")

        assertThat(hindi.isAcceptable).isTrue()
        assertThat(hindi.estimatedBits).isGreaterThan(0)
        assertThat(emoji.isAcceptable).isTrue()
        assertThat(emoji.estimatedBits).isGreaterThan(0)
    }

    @Test
    fun `spaces are treated as valid password characters`() {
        assertThat(assess("two words here").isAcceptable).isTrue()
    }

    @Test
    fun `strength rises monotonically as a passphrase grows`() {
        val bits = listOf(
            assess("river12x").estimatedBits,
            assess("river stone12x").estimatedBits,
            assess("river stone lantern12x").estimatedBits,
            assess("river stone lantern harbour12x").estimatedBits,
        )

        assertThat(bits).isInOrder()
    }

    @Test
    fun `assessment is deterministic`() {
        assertThat(assess("some-vault-passphrase")).isEqualTo(assess("some-vault-passphrase"))
    }
}
