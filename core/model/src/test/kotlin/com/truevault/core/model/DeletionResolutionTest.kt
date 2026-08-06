package com.truevault.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The "Original deleted" claim.
 *
 * TrueVault tells a user their original file is gone. If that sentence is ever shown when a copy
 * still exists, the app has lied about the one thing it was installed to do. This class checks the
 * mapping for **every** [DeletionOutcome] — including any added later, via the exhaustive sweep at
 * the bottom — so a new outcome cannot quietly default into "secured".
 */
class DeletionResolutionTest {

    @Test
    fun `a confirmed system delete is the only ordinary route to SECURED`() {
        val resolution = DeletionOutcome.DELETED.resolve()

        assertThat(resolution.deletionState).isEqualTo(OriginalDeletionState.CONFIRMED_DELETED)
        assertThat(resolution.privacyStatus).isEqualTo(PrivacyStatus.SECURED)
    }

    @Test
    fun `an already missing original counts as confirmed, because nothing is there to find`() {
        val resolution = DeletionOutcome.ALREADY_MISSING.resolve()

        assertThat(resolution.deletionState).isEqualTo(OriginalDeletionState.CONFIRMED_DELETED)
        assertThat(resolution.privacyStatus).isEqualTo(PrivacyStatus.SECURED)
    }

    @Test
    fun `a user who declines keeps their file and the app says so`() {
        val resolution = DeletionOutcome.USER_CANCELLED.resolve()

        assertThat(resolution.deletionState).isEqualTo(OriginalDeletionState.DECLINED_BY_USER)
        assertThat(resolution.privacyStatus).isEqualTo(PrivacyStatus.ORIGINAL_REMAINS)
        // Declined is distinct from failed: the user made a choice, and a retry prompt that treats
        // it as an error would be nagging them about a decision they already made.
        assertThat(resolution.deletionState).isNotEqualTo(OriginalDeletionState.FAILED)
    }

    @Test
    fun `a provider that cannot delete leaves the item as original-remains`() {
        val resolution = DeletionOutcome.PROVIDER_NOT_SUPPORTED.resolve()

        assertThat(resolution.deletionState).isEqualTo(OriginalDeletionState.FAILED)
        assertThat(resolution.privacyStatus).isEqualTo(PrivacyStatus.ORIGINAL_REMAINS)
    }

    @Test
    fun `a lost permission is a failure, never a silent success`() {
        val resolution = DeletionOutcome.PERMISSION_LOST.resolve()

        assertThat(resolution.deletionState).isEqualTo(OriginalDeletionState.FAILED)
        assertThat(resolution.privacyStatus).isEqualTo(PrivacyStatus.ORIGINAL_REMAINS)
    }

    @Test
    fun `an unattempted deletion stays not-requested`() {
        val resolution = DeletionOutcome.NOT_ATTEMPTED.resolve()

        assertThat(resolution.deletionState).isEqualTo(OriginalDeletionState.NOT_REQUESTED)
        assertThat(resolution.privacyStatus).isEqualTo(PrivacyStatus.ORIGINAL_REMAINS)
    }

    @Test
    fun `only observed removal ever yields SECURED, for every outcome that exists`() {
        val evidenceOfRemoval = setOf(DeletionOutcome.DELETED, DeletionOutcome.ALREADY_MISSING)

        DeletionOutcome.entries.forEach { outcome ->
            val resolution = outcome.resolve()
            val claimsSecured = resolution.privacyStatus == PrivacyStatus.SECURED

            assertThat(claimsSecured).isEqualTo(outcome in evidenceOfRemoval)

            // The two fields must never disagree — a CONFIRMED_DELETED item shown as
            // ORIGINAL_REMAINS (or the reverse) would put two different answers on two screens.
            assertThat(resolution.deletionState == OriginalDeletionState.CONFIRMED_DELETED)
                .isEqualTo(claimsSecured)
        }
    }

    @Test
    fun `no outcome resolves to a pending state, because pending is not an answer`() {
        // DELETE_PENDING is set when the request goes out. Once an outcome comes back, the item
        // must leave that state in every case, or an interrupted flow would sit as "pending"
        // forever and the user would never learn what happened.
        DeletionOutcome.entries.forEach { outcome ->
            assertThat(outcome.resolve().privacyStatus).isNotEqualTo(PrivacyStatus.DELETE_PENDING)
            assertThat(outcome.resolve().deletionState).isNotEqualTo(OriginalDeletionState.REQUESTED)
        }
    }
}
