package com.truevault.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.truevault.core.common.log.SecureLog
import com.truevault.core.model.LegalAcceptanceRecord
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val TAG = "LegalAcceptance"

/**
 * Stores the fact that the user accepted the documents, and nothing else.
 *
 * What is written here is exhaustively: two version strings, two timestamps, the app version code
 * and the flow version. No file names, no note contents, no password, no PIN, no biometric data, no
 * recovery key, no advertising ID, no device identifier — and no account on anyone's server. For an
 * app whose whole claim is that nothing leaves the device, creating an online record to prove the
 * user was told that would be self-refuting.
 *
 * A record that cannot be read back in full is reported as absent rather than repaired. Consent that
 * cannot be evidenced is not consent, and the cost of asking again is one screen.
 */
@Singleton
class LegalAcceptanceDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val record: Flow<Result<LegalAcceptanceRecord?>> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                SecureLog.w(TAG, "Acceptance store unreadable")
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs -> prefs.toRecord() }

    suspend fun record(record: LegalAcceptanceRecord) {
        edit { prefs ->
            prefs[Keys.TERMS_VERSION] = record.termsVersion
            prefs[Keys.PRIVACY_VERSION] = record.privacyPolicyVersion
            prefs[Keys.TERMS_ACCEPTED_AT] = record.termsAcceptedAtUtc
            prefs[Keys.PRIVACY_ACKNOWLEDGED_AT] = record.privacyAcknowledgedAtUtc
            prefs[Keys.APP_VERSION_CODE] = record.appVersionCode
            prefs[Keys.FLOW_VERSION] = record.acceptanceFlowVersion
        }
    }

    /** Called by the reset flow. Returning to first launch means returning to the legal screen. */
    suspend fun clear() {
        edit { prefs -> Keys.all.forEach { key -> prefs.remove(key) } }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (e: IOException) {
            SecureLog.e(TAG, "Acceptance record could not be written", e)
            throw e
        }
    }

    private fun Preferences.toRecord(): Result<LegalAcceptanceRecord?> {
        val present = Keys.all.count { this[it] != null }
        if (present == 0) return Result.success(null)

        // Partially written: a crash between two of the six writes, or a store that lost part of
        // its contents. Neither is evidence of consent.
        if (present < Keys.all.size) {
            return Result.failure(
                IllegalStateException("Acceptance record incomplete ($present of ${Keys.all.size} fields)"),
            )
        }

        return Result.success(
            LegalAcceptanceRecord(
                termsVersion = this[Keys.TERMS_VERSION].orEmpty(),
                privacyPolicyVersion = this[Keys.PRIVACY_VERSION].orEmpty(),
                termsAcceptedAtUtc = this[Keys.TERMS_ACCEPTED_AT] ?: 0L,
                privacyAcknowledgedAtUtc = this[Keys.PRIVACY_ACKNOWLEDGED_AT] ?: 0L,
                appVersionCode = this[Keys.APP_VERSION_CODE] ?: 0L,
                acceptanceFlowVersion = this[Keys.FLOW_VERSION] ?: 0,
            ),
        )
    }

    private object Keys {
        val TERMS_VERSION = stringPreferencesKey("legal_terms_version")
        val PRIVACY_VERSION = stringPreferencesKey("legal_privacy_version")
        val TERMS_ACCEPTED_AT = longPreferencesKey("legal_terms_accepted_at")
        val PRIVACY_ACKNOWLEDGED_AT = longPreferencesKey("legal_privacy_acknowledged_at")
        val APP_VERSION_CODE = longPreferencesKey("legal_app_version_code")
        val FLOW_VERSION = intPreferencesKey("legal_flow_version")

        val all = listOf(
            TERMS_VERSION,
            PRIVACY_VERSION,
            TERMS_ACCEPTED_AT,
            PRIVACY_ACKNOWLEDGED_AT,
            APP_VERSION_CODE,
            FLOW_VERSION,
        )
    }
}
