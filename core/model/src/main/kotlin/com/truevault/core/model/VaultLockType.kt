package com.truevault.core.model

/**
 * How the user chose to lock their vault.
 *
 * ## The honest trade-off
 *
 * A passphrase and a PIN are not equally strong, and the app says so rather than presenting them as
 * interchangeable options:
 *
 * | Type | Combinations | Attackable offline? | Exhaustive on-device search under [LockThrottle] |
 * |---|---|---|---|
 * | 4-digit PIN | 10,000 | No | ~3.5 months on average, ~7 months worst case |
 * | 6-digit PIN | 1,000,000 | No | ~28 years on average |
 * | Passphrase | Vastly more | No | Not reachable |
 *
 * "No" in the offline column is what makes any of this defensible. The stored key is sealed twice —
 * once by the secret, once by a non-exportable Android Keystore key — so an attacker who copies the
 * app's files cannot attack the PIN on hardware of their choosing. Every guess has to be made on
 * this device, through TrueVault, against the throttle.
 *
 * Those month figures are measured, not rhetorical: 9,991 attempts at the 30-minute ceiling. A
 * 4-digit PIN is therefore **not** equivalent to the others — it holds against someone who picks up
 * an unattended phone, and does not hold against someone who keeps the device for months. That is
 * why [PIN_6] is the default the UI offers and why choosing [PIN_4] shows the trade-off in plain
 * words instead of a reassuring shrug.
 */
enum class VaultLockType {
    /** Any characters, minimum 8. The strongest option. */
    PASSPHRASE,

    /** Exactly 4 digits. */
    PIN_4,

    /** Exactly 6 digits. */
    PIN_6,
    ;

    val isPin: Boolean get() = this == PIN_4 || this == PIN_6

    /** Required length for a PIN; null for a passphrase, which has a minimum rather than a length. */
    val pinLength: Int?
        get() = when (this) {
            PIN_4 -> 4
            PIN_6 -> 6
            PASSPHRASE -> null
        }

    companion object {
        fun forPinLength(length: Int): VaultLockType? = when (length) {
            4 -> PIN_4
            6 -> PIN_6
            else -> null
        }
    }
}

/**
 * Rate limiting for unlock attempts.
 *
 * This is what makes a short PIN safe enough to offer. Without it, 10,000 combinations at a couple
 * of guesses per second is about ninety minutes of work. With it, the same search takes years.
 *
 * The delay is a pure function of the consecutive-failure count, so it is trivially testable and has
 * no hidden state.
 */
object LockThrottle {

    /** Failures allowed before any delay. Covers ordinary mistyping. */
    const val FREE_ATTEMPTS: Int = 4

    /**
     * How long the vault refuses attempts after [consecutiveFailures].
     *
     * Deliberately not an ever-growing delay: past thirty minutes it stops adding security — an
     * attacker will wait either way — and starts being a way to lock a legitimate user out of their
     * own files for a day. Thirty minutes per attempt already puts a 4-digit PIN out of reach.
     */
    fun delayMillisAfter(consecutiveFailures: Int): Long = when {
        consecutiveFailures <= FREE_ATTEMPTS -> 0L
        consecutiveFailures == 5 -> 30_000L
        consecutiveFailures == 6 -> 60_000L
        consecutiveFailures == 7 -> 5 * 60_000L
        consecutiveFailures == 8 -> 15 * 60_000L
        else -> 30 * 60_000L
    }

    /**
     * Milliseconds still to wait, given when the last failure happened.
     *
     * A clock moved backwards is treated as "still waiting" rather than "free to try": trusting a
     * user-settable clock would turn the whole throttle into a settings toggle.
     */
    fun remainingMillis(
        consecutiveFailures: Int,
        lastFailedAtMillis: Long?,
        nowMillis: Long,
    ): Long {
        val delay = delayMillisAfter(consecutiveFailures)
        if (delay == 0L || lastFailedAtMillis == null) return 0L

        val elapsed = nowMillis - lastFailedAtMillis
        if (elapsed < 0) return delay
        return (delay - elapsed).coerceAtLeast(0L)
    }
}
