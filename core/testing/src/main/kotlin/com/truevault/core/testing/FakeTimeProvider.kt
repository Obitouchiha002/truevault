package com.truevault.core.testing

import com.truevault.core.common.time.TimeProvider

/**
 * Controllable clock for tests of session expiry, auto-lock and timestamps.
 *
 * Wall-clock and monotonic time advance independently, so a test can simulate a user changing the
 * device clock without the monotonic timer moving.
 */
class FakeTimeProvider(
    private var wallClockMillis: Long = 1_700_000_000_000L,
    private var elapsedMillis: Long = 0L,
) : TimeProvider {

    override fun currentTimeMillis(): Long = wallClockMillis

    override fun elapsedRealtimeMillis(): Long = elapsedMillis

    /** Advances both clocks, as real elapsed time would. */
    fun advanceBy(millis: Long) {
        wallClockMillis += millis
        elapsedMillis += millis
    }

    /** Moves only the wall clock, as a user changing the device date would. */
    fun setWallClock(millis: Long) {
        wallClockMillis = millis
    }
}
