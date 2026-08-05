package com.truevault.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrivacyScoreTest {

    @Test
    fun `fully configured vault with no leaks scores 100`() {
        val score = calculatePrivacyScore(
            PrivacyScoreInputs(backupConfigured = true, recoveryKeyConfigured = true),
        )

        assertThat(score.score).isEqualTo(100)
        assertThat(score.deductions).isEmpty()
        assertThat(score.hasIssues).isFalse()
    }

    @Test
    fun `missing backup and recovery key cost five points each`() {
        val score = calculatePrivacyScore(PrivacyScoreInputs())

        assertThat(score.score).isEqualTo(90)
        assertThat(score.deductions.map { it.reason }).containsExactly(
            PrivacyDeductionReason.BACKUP_NOT_CONFIGURED,
            PrivacyDeductionReason.RECOVERY_KEY_NOT_CONFIGURED,
        )
    }

    @Test
    fun `one remaining original costs twenty points`() {
        val score = calculatePrivacyScore(
            PrivacyScoreInputs(
                itemsWithOriginalRemaining = 1,
                backupConfigured = true,
                recoveryKeyConfigured = true,
            ),
        )

        assertThat(score.score).isEqualTo(80)
    }

    @Test
    fun `remaining originals are capped so the score stays actionable`() {
        val score = calculatePrivacyScore(
            PrivacyScoreInputs(
                itemsWithOriginalRemaining = 50,
                backupConfigured = true,
                recoveryKeyConfigured = true,
            ),
        )

        assertThat(score.score).isEqualTo(100 - PrivacyDeductionReason.ORIGINAL_REMAINS.maxPoints)
        assertThat(score.deductions.single().affectedItemCount).isEqualTo(50)
    }

    @Test
    fun `score never drops below zero when every category is at its cap`() {
        val score = calculatePrivacyScore(
            PrivacyScoreInputs(
                itemsWithOriginalRemaining = 100,
                itemsWithExactDuplicate = 100,
                unresolvedFailedImports = 100,
                itemsFailingIntegrity = 100,
                backupConfigured = false,
                recoveryKeyConfigured = false,
            ),
        )

        assertThat(score.score).isEqualTo(0)
    }

    @Test
    fun `every deduction reports how many items caused it`() {
        val score = calculatePrivacyScore(
            PrivacyScoreInputs(
                itemsWithOriginalRemaining = 2,
                itemsWithExactDuplicate = 3,
                backupConfigured = true,
                recoveryKeyConfigured = true,
            ),
        )

        assertThat(score.itemsNeedingAttention).isEqualTo(5)
        assertThat(score.score).isEqualTo(100 - 40 - 30)
    }

    @Test
    fun `calculation is deterministic for identical inputs`() {
        val inputs = PrivacyScoreInputs(itemsWithOriginalRemaining = 3, itemsWithExactDuplicate = 1)

        assertThat(calculatePrivacyScore(inputs)).isEqualTo(calculatePrivacyScore(inputs))
    }
}
