package com.truevault.core.model

import kotlin.math.ln
import kotlin.math.min

/**
 * How good a vault password is, and why.
 *
 * Two rules shape this:
 *
 *  - **Nothing is silently rejected.** Every character the user types is accepted, including emoji,
 *    Devanagari, Cyrillic and spaces. Strength is advice; the only hard rule is the 8-character
 *    minimum.
 *  - **A long passphrase beats a short scramble.** `correct horse battery staple` scores higher than
 *    `P@ss1!`, because it is genuinely harder to guess and far easier to remember — and a password
 *    the user cannot remember becomes a permanently lost vault.
 */
enum class PasswordStrength {
    TOO_SHORT,
    WEAK,
    FAIR,
    GOOD,
    STRONG,
}

enum class PasswordSuggestion {
    /** Below the 8-character minimum. */
    MAKE_IT_LONGER,

    /** The single highest-value change for most users. */
    USE_A_PASSPHRASE,

    /** "aaaaaaaa", "12341234". */
    AVOID_REPETITION,

    /** "123456", "qwerty", "password" and friends. */
    AVOID_COMMON_PASSWORD,

    /** Only for passwords that are short *and* single-class. */
    MIX_CHARACTER_TYPES,
}

data class PasswordAssessment(
    val strength: PasswordStrength,
    val meetsMinimumLength: Boolean,
    val estimatedBits: Int,
    val suggestions: List<PasswordSuggestion>,
) {
    /** Whether the app will let the user proceed. Only the length minimum is enforced. */
    val isAcceptable: Boolean get() = meetsMinimumLength
}

const val MINIMUM_PASSWORD_LENGTH: Int = 8

/**
 * The most-guessed passwords, kept deliberately short.
 *
 * A full breach corpus belongs on a server, and TrueVault has no server. This catches the handful
 * that appear at the top of every leaked-password list; it is a nudge, not a filter.
 */
private val COMMON_PASSWORDS = setOf(
    "password", "12345678", "123456789", "1234567890", "qwertyui", "iloveyou",
    "password1", "admin123", "letmein1", "welcome1", "abc12345", "11111111",
    "sunshine", "princess", "football", "trustno1", "passw0rd", "qwerty123",
)

fun assessPassword(password: CharArray): PasswordAssessment {
    val length = password.size

    if (length < MINIMUM_PASSWORD_LENGTH) {
        return PasswordAssessment(
            strength = PasswordStrength.TOO_SHORT,
            meetsMinimumLength = false,
            estimatedBits = 0,
            suggestions = listOf(PasswordSuggestion.MAKE_IT_LONGER),
        )
    }

    val lowered = String(password).lowercase()
    val suggestions = mutableListOf<PasswordSuggestion>()

    if (lowered in COMMON_PASSWORDS) {
        return PasswordAssessment(
            strength = PasswordStrength.WEAK,
            meetsMinimumLength = true,
            estimatedBits = 0,
            suggestions = listOf(
                PasswordSuggestion.AVOID_COMMON_PASSWORD,
                PasswordSuggestion.USE_A_PASSPHRASE,
            ),
        )
    }

    val distinctRatio = password.toSet().size.toDouble() / length
    val alphabet = estimateAlphabetSize(password)
    val wordCount = String(password).trim().split(" ", "-", "_").count { it.isNotBlank() }

    // Entropy of a random string over the observed alphabet, then discounted for how much of the
    // password is actually distinct. "aaaaaaaaaa" has ten characters and almost no entropy.
    var bits = (length * ln(alphabet.toDouble()) / ln(2.0)) * (0.45 + 0.55 * distinctRatio)

    // A multi-word passphrase is credited for its words rather than only its characters.
    if (wordCount >= 3) bits += 8.0
    if (wordCount >= 4) bits += 8.0

    // Long, simple and memorable should not be punished for lacking symbols.
    if (length >= 20) bits += 6.0

    val estimatedBits = min(bits, 160.0).toInt()

    if (distinctRatio < 0.5) suggestions += PasswordSuggestion.AVOID_REPETITION
    if (wordCount < 2 && length < 16) suggestions += PasswordSuggestion.USE_A_PASSPHRASE
    if (alphabet <= 26 && length < 14) suggestions += PasswordSuggestion.MIX_CHARACTER_TYPES

    val strength = when {
        estimatedBits >= 90 -> PasswordStrength.STRONG
        estimatedBits >= 64 -> PasswordStrength.GOOD
        estimatedBits >= 45 -> PasswordStrength.FAIR
        else -> PasswordStrength.WEAK
    }

    return PasswordAssessment(
        strength = strength,
        meetsMinimumLength = true,
        estimatedBits = estimatedBits,
        suggestions = suggestions.distinct(),
    )
}

/** The size of the character space the password draws from, per observed class. */
private fun estimateAlphabetSize(password: CharArray): Int {
    var size = 0
    var hasLower = false
    var hasUpper = false
    var hasDigit = false
    var hasSymbol = false
    var hasOtherScript = false

    for (c in password) {
        when {
            c in 'a'..'z' -> hasLower = true
            c in 'A'..'Z' -> hasUpper = true
            c in '0'..'9' -> hasDigit = true
            c.code < 128 -> hasSymbol = true
            // Non-ASCII characters are counted generously and never rejected: a Devanagari or
            // Cyrillic passphrase is a perfectly good password.
            else -> hasOtherScript = true
        }
    }

    if (hasLower) size += 26
    if (hasUpper) size += 26
    if (hasDigit) size += 10
    if (hasSymbol) size += 33
    if (hasOtherScript) size += 128

    return size.coerceAtLeast(2)
}
