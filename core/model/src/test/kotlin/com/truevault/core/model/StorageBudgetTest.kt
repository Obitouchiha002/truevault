package com.truevault.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val MB = 1024L * 1024
private const val GB = 1024L * MB

/**
 * The storage ceiling the user sets.
 *
 * The property that matters most is what the budget is *not* allowed to do: it can refuse a new
 * import, and it can never remove, evict or shrink anything already in the vault. Every test here is
 * about a refusal, and there is deliberately no code path to test that would delete something to
 * make room.
 */
class StorageBudgetTest {

    @Test
    fun `an import that fits both the device and the budget is allowed`() {
        val allowance = StorageBudgetPolicy.evaluate(
            budget = StorageBudget.GB_2,
            usedBytes = 500 * MB,
            requiredBytes = 100 * MB,
            deviceFreeBytes = 10 * GB,
        )

        assertThat(allowance).isEqualTo(StorageAllowance.Allowed)
        assertThat(allowance.isAllowed).isTrue()
    }

    @Test
    fun `a full device is reported as a full device, not as a budget problem`() {
        // The user has a 20 GB ceiling and 19 GB of room left in it — telling them to raise the
        // budget would send them to fix something that is not broken.
        val allowance = StorageBudgetPolicy.evaluate(
            budget = StorageBudget.GB_20,
            usedBytes = 1 * GB,
            requiredBytes = 2 * GB,
            deviceFreeBytes = 500 * MB,
        )

        assertThat(allowance).isInstanceOf(StorageAllowance.DeviceFull::class.java)
        val full = allowance as StorageAllowance.DeviceFull
        assertThat(full.shortfallBytes).isEqualTo(2 * GB - 500 * MB)
    }

    @Test
    fun `the device is checked before the budget`() {
        // Both are exceeded. The device wins, because no change to the budget would help.
        val allowance = StorageBudgetPolicy.evaluate(
            budget = StorageBudget.MB_500,
            usedBytes = 490 * MB,
            requiredBytes = 5 * GB,
            deviceFreeBytes = 100 * MB,
        )

        assertThat(allowance).isInstanceOf(StorageAllowance.DeviceFull::class.java)
    }

    @Test
    fun `a budget that would be exceeded refuses the import and says by how much`() {
        val allowance = StorageBudgetPolicy.evaluate(
            budget = StorageBudget.GB_1,
            usedBytes = 900 * MB,
            requiredBytes = 200 * MB,
            deviceFreeBytes = 50 * GB,
        )

        assertThat(allowance).isInstanceOf(StorageAllowance.BudgetExceeded::class.java)
        val exceeded = allowance as StorageAllowance.BudgetExceeded
        assertThat(exceeded.remainingBytes).isEqualTo(124 * MB)
        assertThat(exceeded.shortfallBytes).isEqualTo(200 * MB - 124 * MB)
    }

    @Test
    fun `an import that exactly fills the budget is allowed`() {
        // The ceiling is a limit, not a margin. Refusing the file that lands exactly on it would be
        // a rounding error the user experiences as the app lying about its own number.
        val allowance = StorageBudgetPolicy.evaluate(
            budget = StorageBudget.GB_1,
            usedBytes = 1 * GB - 100,
            requiredBytes = 100,
            deviceFreeBytes = 50 * GB,
        )

        assertThat(allowance).isEqualTo(StorageAllowance.Allowed)
    }

    @Test
    fun `unlimited means the device is the only ceiling`() {
        val allowed = StorageBudgetPolicy.evaluate(
            budget = StorageBudget.UNLIMITED,
            usedBytes = 500 * GB,
            requiredBytes = 1 * GB,
            deviceFreeBytes = 2 * GB,
        )
        val refused = StorageBudgetPolicy.evaluate(
            budget = StorageBudget.UNLIMITED,
            usedBytes = 0,
            requiredBytes = 1 * GB,
            deviceFreeBytes = 500 * MB,
        )

        assertThat(allowed).isEqualTo(StorageAllowance.Allowed)
        assertThat(refused).isInstanceOf(StorageAllowance.DeviceFull::class.java)
    }

    @Test
    fun `a vault already over budget refuses new imports but keeps what it holds`() {
        // Reachable if the user lowers the ceiling below what is stored. The import is refused; the
        // stored items are untouched, and nothing in this policy can express removing them.
        val allowance = StorageBudgetPolicy.evaluate(
            budget = StorageBudget.MB_500,
            usedBytes = 2 * GB,
            requiredBytes = 1,
            deviceFreeBytes = 50 * GB,
        )

        assertThat(allowance).isInstanceOf(StorageAllowance.BudgetExceeded::class.java)
        assertThat((allowance as StorageAllowance.BudgetExceeded).remainingBytes).isEqualTo(0)
    }

    @Test
    fun `the meter fraction is clamped and absent for unlimited`() {
        assertThat(StorageBudgetPolicy.usedFraction(StorageBudget.GB_1, 512 * MB))
            .isWithin(1e-4f).of(0.5f)
        assertThat(StorageBudgetPolicy.usedFraction(StorageBudget.GB_1, 5 * GB))
            .isEqualTo(1.0f)
        assertThat(StorageBudgetPolicy.usedFraction(StorageBudget.UNLIMITED, 5 * GB)).isNull()
    }

    @Test
    fun `settings never offers a ceiling below what is already stored`() {
        assertThat(StorageBudget.smallestFitting(700 * MB)).isEqualTo(StorageBudget.GB_1)
        assertThat(StorageBudget.smallestFitting(0)).isEqualTo(StorageBudget.MB_500)
        // Larger than every option: the only honest offer left is no ceiling.
        assertThat(StorageBudget.smallestFitting(100 * GB)).isEqualTo(StorageBudget.UNLIMITED)
    }

    @Test
    fun `an unknown or missing stored value falls back to the default rather than to a small cap`() {
        // A preferences file written by a future version must not silently shrink someone's vault
        // ceiling to 500 MB.
        assertThat(StorageBudget.fromName(null)).isEqualTo(StorageBudget.UNLIMITED)
        assertThat(StorageBudget.fromName("GB_100")).isEqualTo(StorageBudget.UNLIMITED)
        assertThat(StorageBudget.fromName("GB_5")).isEqualTo(StorageBudget.GB_5)
    }

    @Test
    fun `a budget refusal is never retryable`() {
        val error = VaultError.StorageBudgetReached(StorageBudget.GB_1, 1 * GB, 900 * MB)

        // Retrying the identical import hits the identical ceiling. Offering Retry would be an
        // action the app knows will fail.
        assertThat(error.isRetryable).isFalse()
    }
}
