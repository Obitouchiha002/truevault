package com.truevault.core.model

/** The two documents. They are separate on purpose: agreeing to Terms is not the same act as
 *  being told how your data is handled, and combining them would blur which one the user did. */
enum class LegalDocumentKind {
    TERMS_OF_SERVICE,
    PRIVACY_POLICY,
}

/**
 * What the user accepted, and when.
 *
 * Deliberately small. It records the fact of acceptance and nothing about the person: no file names,
 * no note contents, no password, no PIN, no biometric data, no recovery key, no advertising ID, and
 * no device identifier. There is also no online account created merely to hold it — for a local-only
 * app, storing a consent record on someone else's server would collect more data than the app
 * otherwise does, to prove the user was told the app collects nothing.
 */
data class LegalAcceptanceRecord(
    val termsVersion: String,
    val privacyPolicyVersion: String,
    /** Epoch milliseconds, UTC. */
    val termsAcceptedAtUtc: Long,
    /** Epoch milliseconds, UTC. */
    val privacyAcknowledgedAtUtc: Long,
    val appVersionCode: Long,
    val acceptanceFlowVersion: Int,
)

/** The versions and dates currently shipped in the APK, read from `assets/legal/metadata.json`. */
data class LegalDocumentVersions(
    val termsVersion: String,
    val privacyVersion: String,
    val termsEffectiveDate: String,
    val privacyEffectiveDate: String,
    /**
     * Set by a human in `legal/legal-config.json` when a change is material. It is never inferred
     * from a version-string comparison: a version bump for a typo must not interrupt anyone, and
     * only a person can tell the difference between a typo and a change in what happens to a
     * user's data.
     */
    val requiresReacceptance: Boolean,
    val acceptanceFlowVersion: Int,
)

/** Where the user stands relative to the documents currently in the app. */
sealed interface LegalAcceptanceStatus {

    /** Nothing stored — a first launch, or a reset. */
    data object Missing : LegalAcceptanceStatus

    /** Stored, but unreadable or incomplete. Treated exactly like [Missing]: the safe failure is
     *  to ask again, never to assume consent that cannot be evidenced. */
    data class Corrupted(val safeReason: String) : LegalAcceptanceStatus

    /** Current documents accepted. */
    data class Accepted(val record: LegalAcceptanceRecord) : LegalAcceptanceStatus

    /** Accepted an earlier version, and the change was marked material. */
    data class ReacceptanceRequired(
        val record: LegalAcceptanceRecord,
        val newVersions: LegalDocumentVersions,
        val termsChanged: Boolean,
        val privacyChanged: Boolean,
    ) : LegalAcceptanceStatus

    /** True only for [Accepted]. Everything else must stop at the legal gate. */
    val isSatisfied: Boolean get() = this is Accepted
}

/**
 * One rendered section of a legal document.
 *
 * The documents are authored in Markdown and parsed into this shape at load time, so the app renders
 * structured Compose text rather than putting a WebView in front of a document the user is being
 * asked to rely on.
 */
data class LegalSection(
    val id: String,
    val heading: String?,
    val headingLevel: Int,
    val blocks: List<LegalBlock>,
)

sealed interface LegalBlock {
    data class Paragraph(val text: String) : LegalBlock
    data class Bullets(val items: List<String>) : LegalBlock
    data class Numbered(val items: List<String>) : LegalBlock
    data class Quote(val text: String) : LegalBlock
    data class Table(val header: List<String>?, val rows: List<List<String>>) : LegalBlock
    data object Divider : LegalBlock
}

/** A whole document, ready to render. */
data class LegalDocument(
    val kind: LegalDocumentKind,
    val title: String,
    val version: String,
    val effectiveDate: String,
    val lastUpdated: String,
    val sections: List<LegalSection>,
) {
    /** Plain text of the whole document, used for in-document search. */
    val searchableText: String by lazy {
        buildString {
            sections.forEach { section ->
                section.heading?.let { appendLine(it) }
                section.blocks.forEach { block ->
                    when (block) {
                        is LegalBlock.Paragraph -> appendLine(block.text)
                        is LegalBlock.Bullets -> block.items.forEach(::appendLine)
                        is LegalBlock.Numbered -> block.items.forEach(::appendLine)
                        is LegalBlock.Quote -> appendLine(block.text)
                        is LegalBlock.Table -> {
                            block.header?.forEach(::appendLine)
                            block.rows.forEach { row -> row.forEach(::appendLine) }
                        }
                        LegalBlock.Divider -> Unit
                    }
                }
            }
        }
    }
}

/**
 * Unresolved placeholders found in a bundled document.
 *
 * A shipped policy reading "[PRIVACY EMAIL REQUIRED]" is worse than no policy: it looks like a legal
 * document and answers nothing. The release gate fails while any of these remain, and the app
 * surfaces them in debug builds so they are noticed long before a release build is attempted.
 */
data class LegalPlaceholderReport(val placeholders: List<String>) {
    val isReady: Boolean get() = placeholders.isEmpty()
}
