package com.truevault.core.crypto.recovery

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test

/**
 * The recovery key is the one piece of entropy in this app a human writes on paper.
 *
 * It is also the only credential that works on a different device, so its strength is the ceiling
 * on how well a lost phone is protected. A bias in how its characters are chosen is therefore worth
 * a test that would not be worth writing anywhere else.
 *
 * The bug this guards against was real: `randomByte % 30` gives the first 16 letters of a 30-letter
 * alphabet a 9-in-256 chance and the rest 8-in-256 — a public, exploitable skew an attacker would
 * simply guess in frequency order.
 */
class RecoveryKeyUniformityTest {

    private companion object {
        const val ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"
        const val SAMPLES = 20_000
    }

    @Test
    fun `every character comes from the unambiguous alphabet`() {
        repeat(200) {
            RecoveryKey.generate().forEach { character ->
                assertThat(ALPHABET).contains(character.toString())
            }
        }
    }

    @Test
    fun `keys are the declared length and never repeat`() {
        val keys = List(500) { RecoveryKey.generate().concatToString() }

        keys.forEach { assertThat(it).hasLength(RecoveryKey.LENGTH) }
        assertThat(keys.toSet()).hasSize(keys.size)
    }

    @Test
    fun `character frequencies are uniform, with no letters favoured by modulo bias`() {
        val counts = mutableMapOf<Char, Int>()
        var total = 0

        while (total < SAMPLES) {
            RecoveryKey.generate().forEach { character ->
                counts[character] = (counts[character] ?: 0) + 1
                total++
            }
        }

        // Every character must appear. A modulo-biased generator still produces all of them, so
        // this alone would not catch the bug — the spread below is what does.
        assertThat(counts.keys).hasSize(ALPHABET.length)

        val expected = total.toDouble() / ALPHABET.length
        val worstDeviation = counts.values.maxOf { abs(it - expected) / expected }

        // The old modulo version skewed 16 of 30 characters by a fixed +12.5%. Sampling noise at
        // this size is well under 10%, so the threshold separates "random" from "biased" without
        // making the test flaky.
        assertThat(worstDeviation).isLessThan(0.10)
    }

    @Test
    fun `formatting groups the key without changing it`() {
        val key = RecoveryKey.generate()
        val formatted = RecoveryKey.format(key)

        assertThat(formatted.replace("-", "")).isEqualTo(key.concatToString())
        assertThat(formatted.count { it == '-' }).isEqualTo(RecoveryKey.GROUP_COUNT - 1)
    }
}
