package com.truevault.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LockThrottleTest {

    @Test
    fun `ordinary mistyping is not punished`() {
        (1..LockThrottle.FREE_ATTEMPTS).forEach { attempts ->
            assertThat(LockThrottle.delayMillisAfter(attempts)).isEqualTo(0L)
        }
    }

    @Test
    fun `the delay grows and then settles at thirty minutes`() {
        assertThat(LockThrottle.delayMillisAfter(5)).isEqualTo(30_000L)
        assertThat(LockThrottle.delayMillisAfter(6)).isEqualTo(60_000L)
        assertThat(LockThrottle.delayMillisAfter(7)).isEqualTo(300_000L)
        assertThat(LockThrottle.delayMillisAfter(8)).isEqualTo(900_000L)
        assertThat(LockThrottle.delayMillisAfter(9)).isEqualTo(1_800_000L)
        assertThat(LockThrottle.delayMillisAfter(500)).isEqualTo(1_800_000L)
    }

    private fun averageSearchDays(combinations: Long): Double {
        val perAttemptMillis = LockThrottle.delayMillisAfter(10)
        val worstCaseMillis = (combinations - LockThrottle.FREE_ATTEMPTS - 5) * perAttemptMillis
        return worstCaseMillis / 2.0 / (24 * 60 * 60 * 1000)
    }

    @Test
    fun `a four digit PIN costs months of continuous attack, not minutes`() {
        // The honest number, and the reason the UI warns about this choice rather than treating it
        // as equivalent to the others. Without throttling the same search is roughly ninety minutes.
        val days = averageSearchDays(10_000)

        assertThat(days).isGreaterThan(90.0)
        assertThat(days).isLessThan(120.0)
    }

    @Test
    fun `a six digit PIN is out of reach`() {
        val years = averageSearchDays(1_000_000) / 365.0

        assertThat(years).isGreaterThan(25.0)
    }

    @Test
    fun `waiting the full delay clears it`() {
        val remaining = LockThrottle.remainingMillis(
            consecutiveFailures = 5,
            lastFailedAtMillis = 1_000L,
            nowMillis = 1_000L + 30_000L,
        )

        assertThat(remaining).isEqualTo(0L)
    }

    @Test
    fun `partway through the delay reports what is left`() {
        val remaining = LockThrottle.remainingMillis(
            consecutiveFailures = 6,
            lastFailedAtMillis = 1_000L,
            nowMillis = 1_000L + 20_000L,
        )

        assertThat(remaining).isEqualTo(40_000L)
    }

    @Test
    fun `moving the clock backwards does not shorten the wait`() {
        // Otherwise the throttle would be defeated by changing the device date.
        val remaining = LockThrottle.remainingMillis(
            consecutiveFailures = 9,
            lastFailedAtMillis = 10_000_000L,
            nowMillis = 5L,
        )

        assertThat(remaining).isEqualTo(LockThrottle.delayMillisAfter(9))
    }

    @Test
    fun `no recorded failure means no wait`() {
        assertThat(LockThrottle.remainingMillis(9, null, 1_000L)).isEqualTo(0L)
    }
}
