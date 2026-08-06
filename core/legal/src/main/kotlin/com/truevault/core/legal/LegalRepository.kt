package com.truevault.core.legal

import com.truevault.core.common.time.TimeProvider
import com.truevault.core.datastore.LegalAcceptanceDataSource
import com.truevault.core.model.LegalAcceptanceRecord
import com.truevault.core.model.LegalAcceptanceStatus
import com.truevault.core.model.LegalDocument
import com.truevault.core.model.LegalDocumentKind
import com.truevault.core.model.LegalDocumentVersions
import com.truevault.core.model.LegalPlaceholderReport
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Whether the user may proceed, and what they still have to be shown.
 *
 * The gate this backs stands in front of everything: onboarding, permissions, vault creation and any
 * file access. Nothing calls into the rest of the app until [status] reports
 * [LegalAcceptanceStatus.Accepted].
 */
@Singleton
class LegalRepository @Inject constructor(
    private val documentSource: LegalDocumentSource,
    private val acceptanceStore: LegalAcceptanceDataSource,
    private val appVersion: AppVersionProvider,
    private val timeProvider: TimeProvider,
) {

    fun status(): Flow<LegalAcceptanceStatus> = acceptanceStore.record.map { result ->
        val versions = documentSource.versions()

        result.fold(
            onSuccess = { record ->
                when {
                    record == null -> LegalAcceptanceStatus.Missing
                    else -> evaluate(record, versions)
                }
            },
            // Unreadable is treated exactly like absent. The alternative — assuming acceptance from
            // a record we could not read — would be inventing consent.
            onFailure = { error ->
                LegalAcceptanceStatus.Corrupted(error.message ?: "Acceptance record unreadable")
            },
        )
    }

    suspend fun currentStatus(): LegalAcceptanceStatus = status().first()

    suspend fun versions(): LegalDocumentVersions = documentSource.versions()

    suspend fun document(kind: LegalDocumentKind): LegalDocument = documentSource.document(kind)

    suspend fun placeholders(): LegalPlaceholderReport = documentSource.placeholders()

    /**
     * Records that both required controls were ticked.
     *
     * Both timestamps are taken at the moment of the tap, and both are stored — acknowledging the
     * Privacy Policy and agreeing to the Terms are two different acts, and collapsing them into one
     * timestamp would lose which one the user actually performed.
     */
    suspend fun recordAcceptance() {
        val versions = documentSource.versions()
        val now = timeProvider.currentTimeMillis()

        acceptanceStore.record(
            LegalAcceptanceRecord(
                termsVersion = versions.termsVersion,
                privacyPolicyVersion = versions.privacyVersion,
                termsAcceptedAtUtc = now,
                privacyAcknowledgedAtUtc = now,
                appVersionCode = appVersion.versionCode,
                acceptanceFlowVersion = versions.acceptanceFlowVersion,
            ),
        )
    }

    /** Called by the reset flow. */
    suspend fun clearAcceptance() = acceptanceStore.clear()

    /**
     * Decides whether the stored record still covers the shipped documents.
     *
     * The rule that matters is what does **not** trigger a prompt. A version bump alone is not
     * enough: `requiresReacceptance` is set by a human in `legal/legal-config.json` when the change
     * is material. A typo fix, a reformat or a new contact address ships with a new version and no
     * interruption — because a prompt that fires for a comma teaches people to dismiss the prompt
     * that fires for a new data-sharing arrangement.
     */
    private fun evaluate(
        record: LegalAcceptanceRecord,
        versions: LegalDocumentVersions,
    ): LegalAcceptanceStatus {
        val termsChanged = record.termsVersion != versions.termsVersion
        val privacyChanged = record.privacyPolicyVersion != versions.privacyVersion
        val flowChanged = record.acceptanceFlowVersion != versions.acceptanceFlowVersion

        val mustAskAgain = (termsChanged || privacyChanged) && versions.requiresReacceptance

        return when {
            mustAskAgain -> LegalAcceptanceStatus.ReacceptanceRequired(
                record = record,
                newVersions = versions,
                termsChanged = termsChanged,
                privacyChanged = privacyChanged,
            )

            // A changed flow version means the acceptance UI itself changed in a way that alters
            // what was asked. Rare, and deliberately treated as material.
            flowChanged -> LegalAcceptanceStatus.ReacceptanceRequired(
                record = record,
                newVersions = versions,
                termsChanged = termsChanged,
                privacyChanged = privacyChanged,
            )

            else -> LegalAcceptanceStatus.Accepted(record)
        }
    }
}

/** Supplies the app's own version code, so `:core:legal` does not depend on `:app`. */
interface AppVersionProvider {
    val versionCode: Long
    val versionName: String
}
