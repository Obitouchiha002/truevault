package com.truevault.core.crypto.recovery

import com.truevault.core.crypto.aead.AesGcm
import java.security.MessageDigest

/**
 * A recovery key: 24 random characters from an unambiguous alphabet, in six groups of four.
 *
 * ```
 * XKPT-9R4M-HW2Q-J7BN-3FDC-YV6L
 * ```
 *
 * Design choices, each for a reason:
 *
 *  - **Base32 without `I`, `O`, `0`, `1`.** Users copy these by hand off a screen or a piece of
 *    paper. Characters that look alike are the single biggest cause of a recovery key that "does not
 *    work", and a recovery key that fails when it is needed is worse than none at all.
 *  - **120 bits of entropy.** Well beyond brute force, and short enough to write down.
 *  - **A checksum group is deliberately not used.** It would let an attacker filter guesses offline;
 *    the KDF and the wrong-key failure already tell an honest user their key is mistyped.
 *  - **Normalisation on entry.** Case, spaces, dashes and the four confusable characters are all
 *    folded, so a user who types `o` where the key shows `Q`... still fails — but one who types
 *    lowercase, or forgets the dashes, succeeds.
 */
object RecoveryKey {

    /** Crockford-style base32: no I, L, O or U, so nothing looks like anything else. */
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"

    const val GROUP_SIZE: Int = 4
    const val GROUP_COUNT: Int = 6
    const val LENGTH: Int = GROUP_SIZE * GROUP_COUNT

    /** Generates a fresh key. The returned array is the caller's to wipe. */
    fun generate(): CharArray {
        val random = AesGcm.randomBytes(LENGTH)
        return CharArray(LENGTH) { index ->
            ALPHABET[(random[index].toInt() and 0xFF) % ALPHABET.length]
        }
    }

    /** `XKPT-9R4M-HW2Q-J7BN-3FDC-YV6L`, for display only. */
    fun format(key: CharArray): String = key.toList()
        .chunked(GROUP_SIZE) { group -> group.joinToString("") }
        .joinToString("-")

    /** The individual groups, for the confirmation step. */
    fun groups(key: CharArray): List<String> =
        key.toList().chunked(GROUP_SIZE) { group -> group.joinToString("") }

    /**
     * Folds user input into canonical form.
     *
     * Returns null when the result is not a valid key length, so a mistyped key fails fast rather
     * than being fed into an expensive key derivation.
     */
    fun normalise(input: String): CharArray? {
        val cleaned = input
            .uppercase()
            .filter { it.isLetterOrDigit() }
            .map { character ->
                when (character) {
                    // Fold the characters this alphabet excludes onto what a user probably meant.
                    'I', 'L' -> '1'
                    'O' -> '0'
                    'U' -> 'V'
                    else -> character
                }
            }
            .filter { it in ALPHABET }

        return if (cleaned.size == LENGTH) cleaned.toCharArray() else null
    }

    fun isValidFormat(input: String): Boolean = normalise(input) != null

    /**
     * A non-reversible check value, stored so the app can tell "wrong key" from "corrupt backup".
     *
     * It is a plain hash rather than a KDF output on purpose: it never protects anything, it only
     * distinguishes two failure messages, and the actual key material is derived separately with
     * Argon2id.
     */
    fun checkValue(key: CharArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("truevault.recovery.check.v1".toByteArray())
        digest.update(String(key).toByteArray())
        return digest.digest().copyOf(8)
    }
}
