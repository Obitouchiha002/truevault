package com.truevault.core.crypto.recovery

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecoveryKeyTest {

    @Test
    fun `a generated key has the documented length and grouping`() {
        val key = RecoveryKey.generate()

        assertThat(key).hasLength(RecoveryKey.LENGTH)
        assertThat(RecoveryKey.groups(key)).hasSize(RecoveryKey.GROUP_COUNT)
        assertThat(RecoveryKey.format(key)).matches("([A-Z2-9]{4}-){5}[A-Z2-9]{4}")
    }

    @Test
    fun `two generated keys differ`() {
        assertThat(RecoveryKey.generate()).isNotEqualTo(RecoveryKey.generate())
    }

    @Test
    fun `the alphabet excludes every confusable character`() {
        repeat(50) {
            val key = String(RecoveryKey.generate())
            assertThat(key).doesNotContain("I")
            assertThat(key).doesNotContain("L")
            assertThat(key).doesNotContain("O")
            assertThat(key).doesNotContain("U")
            assertThat(key).doesNotContain("0")
            assertThat(key).doesNotContain("1")
        }
    }

    @Test
    fun `dashes, spaces and case are all forgiven on entry`() {
        val key = RecoveryKey.generate()
        val formatted = RecoveryKey.format(key)

        assertThat(RecoveryKey.normalise(formatted)).isEqualTo(key)
        assertThat(RecoveryKey.normalise(formatted.lowercase())).isEqualTo(key)
        assertThat(RecoveryKey.normalise(formatted.replace("-", " "))).isEqualTo(key)
        assertThat(RecoveryKey.normalise(formatted.replace("-", ""))).isEqualTo(key)
    }

    @Test
    fun `a key of the wrong length is rejected before any work happens`() {
        assertThat(RecoveryKey.normalise("TOO-SHORT")).isNull()
        assertThat(RecoveryKey.normalise("")).isNull()
        assertThat(RecoveryKey.isValidFormat("not a key at all")).isFalse()
    }

    @Test
    fun `the check value is stable and differs between keys`() {
        val first = RecoveryKey.generate()
        val second = RecoveryKey.generate()

        assertThat(RecoveryKey.checkValue(first)).isEqualTo(RecoveryKey.checkValue(first))
        assertThat(RecoveryKey.checkValue(first)).isNotEqualTo(RecoveryKey.checkValue(second))
    }

    @Test
    fun `the check value does not reveal the key`() {
        val key = RecoveryKey.generate()

        val check = String(RecoveryKey.checkValue(key), Charsets.ISO_8859_1)

        assertThat(check).doesNotContain(String(key))
        assertThat(RecoveryKey.checkValue(key)).hasLength(8)
    }
}
