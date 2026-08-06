package com.truevault.core.model

/**
 * How much of the phone's storage the user is willing to give the vault.
 *
 * The point is not to save space — it is to stop TrueVault from being the reason a phone fills up.
 * A vault that quietly grows until the camera stops working is a bad neighbour, and the user is the
 * only one who can say where the line is.
 *
 * The budget is a **ceiling on new imports**, never a licence to delete. When the vault reaches it,
 * TrueVault refuses to take in more files and says so. It does not evict old items, does not
 * compress them, and does not touch anything the user already secured. A storage setting that could
 * silently destroy data would be far worse than a full vault.
 */
enum class StorageBudget(
    /** Null means "no ceiling beyond the device's own free space". */
    val limitBytes: Long?,
) {
    MB_500(500L * 1024 * 1024),
    GB_1(1L * 1024 * 1024 * 1024),
    GB_2(2L * 1024 * 1024 * 1024),
    GB_5(5L * 1024 * 1024 * 1024),
    GB_10(10L * 1024 * 1024 * 1024),
    GB_20(20L * 1024 * 1024 * 1024),

    /** The default. The device's free space is the only limit, which is what most people expect. */
    UNLIMITED(null),
    ;

    val isUnlimited: Boolean get() = limitBytes == null

    companion object {

        val DEFAULT = UNLIMITED

        fun fromName(name: String?): StorageBudget =
            entries.firstOrNull { it.name == name } ?: DEFAULT

        /**
         * The smallest budget that still fits what is already stored.
         *
         * Used to stop the settings screen offering a ceiling **below** the current vault size.
         * Picking one would put the vault permanently over budget with no way back except deleting
         * files — a setting that creates a problem the user cannot solve is not a setting.
         */
        fun smallestFitting(currentBytes: Long): StorageBudget =
            entries.firstOrNull { it.limitBytes != null && it.limitBytes >= currentBytes }
                ?: UNLIMITED
    }
}

/**
 * The answer to "can this import proceed?".
 *
 * Both limits are checked, and which one bites is reported separately, because the two need
 * different words on screen: a full device is the user's problem to solve in Settings, a full budget
 * is one they can solve in TrueVault in two taps.
 */
sealed interface StorageAllowance {

    data object Allowed : StorageAllowance

    /** The device itself has no room. */
    data class DeviceFull(val requiredBytes: Long, val availableBytes: Long) : StorageAllowance {
        val shortfallBytes: Long get() = (requiredBytes - availableBytes).coerceAtLeast(0)
    }

    /** The device has room; the user's own ceiling does not. */
    data class BudgetExceeded(
        val budget: StorageBudget,
        val requiredBytes: Long,
        val usedBytes: Long,
    ) : StorageAllowance {
        val limitBytes: Long get() = budget.limitBytes ?: Long.MAX_VALUE
        val remainingBytes: Long get() = (limitBytes - usedBytes).coerceAtLeast(0)
        val shortfallBytes: Long get() = (requiredBytes - remainingBytes).coerceAtLeast(0)
    }

    val isAllowed: Boolean get() = this is Allowed
}

/** Pure budget arithmetic, so it can be tested without a file system. */
object StorageBudgetPolicy {

    fun evaluate(
        budget: StorageBudget,
        usedBytes: Long,
        requiredBytes: Long,
        deviceFreeBytes: Long,
    ): StorageAllowance {
        // The device is checked first. There is no point telling someone their self-imposed ceiling
        // is the problem when the phone has no room either way.
        if (deviceFreeBytes < requiredBytes) {
            return StorageAllowance.DeviceFull(requiredBytes, deviceFreeBytes)
        }

        val limit = budget.limitBytes ?: return StorageAllowance.Allowed

        return if (usedBytes + requiredBytes > limit) {
            StorageAllowance.BudgetExceeded(budget, requiredBytes, usedBytes)
        } else {
            StorageAllowance.Allowed
        }
    }

    /** 0..1, for the settings meter. Unlimited has no fraction to show. */
    fun usedFraction(budget: StorageBudget, usedBytes: Long): Float? {
        val limit = budget.limitBytes ?: return null
        if (limit <= 0) return null
        return (usedBytes.toFloat() / limit).coerceIn(0f, 1f)
    }
}
