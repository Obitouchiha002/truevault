package com.truevault.core.model

/**
 * A single, named reason the privacy score is below 100.
 *
 * The UI always shows the breakdown, never a bare number: a score the user cannot explain is not
 * information, it is decoration.
 */
data class PrivacyDeduction(
    val reason: PrivacyDeductionReason,
    val points: Int,
    val affectedItemCount: Int,
) {
    init {
        require(points >= 0) { "Deduction points must be non-negative" }
        require(affectedItemCount >= 0) { "Affected item count must be non-negative" }
    }
}

enum class PrivacyDeductionReason(val pointsEach: Int, val maxPoints: Int) {
    /** A vault item's original file is still readable outside TrueVault. */
    ORIGINAL_REMAINS(pointsEach = 20, maxPoints = 40),

    /** A scan confirmed another byte-identical copy is still accessible. */
    EXACT_DUPLICATE_EXISTS(pointsEach = 10, maxPoints = 30),

    /** No encrypted backup has ever been exported. */
    BACKUP_NOT_CONFIGURED(pointsEach = 5, maxPoints = 5),

    /** No recovery key has been generated, so a forgotten password means permanent data loss. */
    RECOVERY_KEY_NOT_CONFIGURED(pointsEach = 5, maxPoints = 5),

    /** An import failed and was never retried, so the user may believe a file is secured. */
    FAILED_IMPORT_UNRESOLVED(pointsEach = 5, maxPoints = 15),

    /** A stored container failed its integrity check. */
    INTEGRITY_FAILURE(pointsEach = 15, maxPoints = 30),
}

/**
 * The complete, explainable privacy score.
 *
 * [score] is always `100 - sum(deductions)`, clamped to `0..100`.
 */
data class PrivacyScore(
    val score: Int,
    val deductions: List<PrivacyDeduction>,
) {
    init {
        require(score in 0..100) { "Score must be within 0..100" }
    }

    val hasIssues: Boolean get() = deductions.isNotEmpty()

    val itemsNeedingAttention: Int
        get() = deductions.sumOf { it.affectedItemCount }

    companion object {
        val Perfect = PrivacyScore(score = 100, deductions = emptyList())
    }
}

/** Inputs the score is derived from. Everything here is something TrueVault can observe locally. */
data class PrivacyScoreInputs(
    val itemsWithOriginalRemaining: Int = 0,
    val itemsWithExactDuplicate: Int = 0,
    val unresolvedFailedImports: Int = 0,
    val itemsFailingIntegrity: Int = 0,
    val backupConfigured: Boolean = false,
    val recoveryKeyConfigured: Boolean = false,
)

/**
 * Pure, deterministic score calculation.
 *
 * Each reason is capped so that a single category cannot drive the score to zero on its own — a
 * user with 200 un-deleted originals is in the same situation as one with 20, and a permanently
 * zeroed score stops being actionable.
 */
fun calculatePrivacyScore(inputs: PrivacyScoreInputs): PrivacyScore {
    val deductions = buildList {
        addDeduction(PrivacyDeductionReason.ORIGINAL_REMAINS, inputs.itemsWithOriginalRemaining)
        addDeduction(PrivacyDeductionReason.EXACT_DUPLICATE_EXISTS, inputs.itemsWithExactDuplicate)
        addDeduction(PrivacyDeductionReason.INTEGRITY_FAILURE, inputs.itemsFailingIntegrity)
        addDeduction(PrivacyDeductionReason.FAILED_IMPORT_UNRESOLVED, inputs.unresolvedFailedImports)
        if (!inputs.backupConfigured) {
            addDeduction(PrivacyDeductionReason.BACKUP_NOT_CONFIGURED, count = 1)
        }
        if (!inputs.recoveryKeyConfigured) {
            addDeduction(PrivacyDeductionReason.RECOVERY_KEY_NOT_CONFIGURED, count = 1)
        }
    }

    val total = deductions.sumOf { it.points }
    return PrivacyScore(score = (100 - total).coerceIn(0, 100), deductions = deductions)
}

private fun MutableList<PrivacyDeduction>.addDeduction(
    reason: PrivacyDeductionReason,
    count: Int,
) {
    if (count <= 0) return
    val points = (reason.pointsEach * count).coerceAtMost(reason.maxPoints)
    add(PrivacyDeduction(reason = reason, points = points, affectedItemCount = count))
}
