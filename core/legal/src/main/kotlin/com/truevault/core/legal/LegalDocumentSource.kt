package com.truevault.core.legal

import android.content.Context
import com.truevault.core.common.dispatcher.Dispatcher
import com.truevault.core.common.dispatcher.TrueVaultDispatcher
import com.truevault.core.common.log.SecureLog
import com.truevault.core.model.LegalDocument
import com.truevault.core.model.LegalDocumentKind
import com.truevault.core.model.LegalDocumentVersions
import com.truevault.core.model.LegalPlaceholderReport
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "Legal"
private const val ASSET_DIR = "legal"

/**
 * Reads the legal documents bundled in the APK.
 *
 * Bundled, not fetched. A user must be able to read what they are agreeing to on a plane, on a dead
 * SIM, or on the day the hosting provider is down — and an app that asks for consent it cannot
 * substantiate offline is asking for a signature on a blank page. The public web copies exist as
 * well, generated from the same Markdown by `scripts/generate-legal-html.py`, so the two texts
 * cannot drift.
 */
@Singleton
class LegalDocumentSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(TrueVaultDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val cache = mutableMapOf<LegalDocumentKind, LegalDocument>()

    /**
     * The versions and dates actually shipped in this APK.
     *
     * Read from the bundled metadata rather than from a constant in code, so a document can never be
     * updated without its version travelling with it.
     */
    suspend fun versions(): LegalDocumentVersions = withContext(ioDispatcher) {
        val raw = readAsset("metadata.json")
            ?: error("assets/legal/metadata.json is missing. Run scripts/generate-legal-html.py.")

        val obj = json.parseToJsonElement(raw)
        fun str(key: String) = obj.jsonObjectOrNull()?.get(key)?.jsonPrimitive?.content.orEmpty()
        fun bool(key: String) =
            obj.jsonObjectOrNull()?.get(key)?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        fun int(key: String) =
            obj.jsonObjectOrNull()?.get(key)?.jsonPrimitive?.content?.toIntOrNull() ?: 1

        LegalDocumentVersions(
            termsVersion = str("termsVersion"),
            privacyVersion = str("privacyVersion"),
            termsEffectiveDate = str("termsEffectiveDate"),
            privacyEffectiveDate = str("privacyEffectiveDate"),
            requiresReacceptance = bool("requiresReacceptance"),
            acceptanceFlowVersion = int("acceptanceFlowVersion"),
        )
    }

    /** Loads and parses a document. Parsed once per process; the text never changes at runtime. */
    suspend fun document(kind: LegalDocumentKind): LegalDocument = withContext(ioDispatcher) {
        cache[kind]?.let { return@withContext it }

        val versions = versions()
        val version = when (kind) {
            LegalDocumentKind.TERMS_OF_SERVICE -> versions.termsVersion
            LegalDocumentKind.PRIVACY_POLICY -> versions.privacyVersion
        }
        val markdown = readAsset("${kind.assetBaseName()}-v$version.md")
            ?: error("assets/legal/${kind.assetBaseName()}-v$version.md is missing.")

        LegalDocumentParser.parse(kind, markdown).also { cache[kind] = it }
    }

    /**
     * Every unresolved placeholder across both documents.
     *
     * Used by the legal-readiness check and surfaced in debug builds, so "[PRIVACY EMAIL REQUIRED]"
     * is noticed while it is still cheap to fix rather than after it ships.
     */
    suspend fun placeholders(): LegalPlaceholderReport = withContext(ioDispatcher) {
        val versions = versions()
        val found = LegalDocumentKind.entries.flatMap { kind ->
            val version = when (kind) {
                LegalDocumentKind.TERMS_OF_SERVICE -> versions.termsVersion
                LegalDocumentKind.PRIVACY_POLICY -> versions.privacyVersion
            }
            val markdown = readAsset("${kind.assetBaseName()}-v$version.md").orEmpty()
            LegalDocumentParser.findPlaceholders(markdown).placeholders
        }
        LegalPlaceholderReport(found.distinct().sorted())
    }

    /** The bundled HTML copy, for the export and print paths. Never rendered in a WebView. */
    suspend fun htmlAssetPath(kind: LegalDocumentKind): String = withContext(ioDispatcher) {
        val versions = versions()
        val version = when (kind) {
            LegalDocumentKind.TERMS_OF_SERVICE -> versions.termsVersion
            LegalDocumentKind.PRIVACY_POLICY -> versions.privacyVersion
        }
        "$ASSET_DIR/${kind.assetBaseName()}-v$version.html"
    }

    private fun readAsset(name: String): String? = try {
        context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        SecureLog.e(TAG, "Legal asset could not be read", e)
        null
    }

    private fun LegalDocumentKind.assetBaseName(): String = when (this) {
        LegalDocumentKind.TERMS_OF_SERVICE -> "terms"
        LegalDocumentKind.PRIVACY_POLICY -> "privacy-policy"
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull() =
        this as? kotlinx.serialization.json.JsonObject
}
