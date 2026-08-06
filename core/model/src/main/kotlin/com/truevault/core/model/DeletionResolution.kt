package com.truevault.core.model

/**
 * What a reported deletion outcome means for an item's stored state.
 *
 * This mapping is the single most consequential lookup table in the app. "Original deleted" is a
 * claim TrueVault makes to a user about a file they can no longer see, and the only thing standing
 * between that claim and a lie is this table. It lives in `:core:model` rather than inside the
 * import engine so it can be exercised for every [DeletionOutcome] without a database, a content
 * provider or a device — an engine-shaped test would need all three and would still be testing the
 * same six lines.
 *
 * The rule it encodes: only [DeletionOutcome.DELETED] and [DeletionOutcome.ALREADY_MISSING] are
 * evidence that no copy remains. Everything else — declined, unsupported, permission lost, failed,
 * not attempted — leaves the item as [PrivacyStatus.ORIGINAL_REMAINS].
 */
data class DeletionResolution(
    val deletionState: OriginalDeletionState,
    val privacyStatus: PrivacyStatus,
)

fun DeletionOutcome.resolve(): DeletionResolution = when (this) {
    // The system reported success, or the file was already gone. Both mean there is nothing left
    // at that location, and both are observations rather than assumptions.
    DeletionOutcome.DELETED,
    DeletionOutcome.ALREADY_MISSING,
    -> DeletionResolution(OriginalDeletionState.CONFIRMED_DELETED, PrivacyStatus.SECURED)

    // The user said no. That is a decision to record, not a failure to retry silently.
    DeletionOutcome.USER_CANCELLED ->
        DeletionResolution(OriginalDeletionState.DECLINED_BY_USER, PrivacyStatus.ORIGINAL_REMAINS)

    DeletionOutcome.PROVIDER_NOT_SUPPORTED,
    DeletionOutcome.PERMISSION_LOST,
    DeletionOutcome.FAILED,
    -> DeletionResolution(OriginalDeletionState.FAILED, PrivacyStatus.ORIGINAL_REMAINS)

    DeletionOutcome.NOT_ATTEMPTED ->
        DeletionResolution(OriginalDeletionState.NOT_REQUESTED, PrivacyStatus.ORIGINAL_REMAINS)
}
