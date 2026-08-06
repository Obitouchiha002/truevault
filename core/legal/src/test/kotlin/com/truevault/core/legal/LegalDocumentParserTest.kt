package com.truevault.core.legal

import com.google.common.truth.Truth.assertThat
import com.truevault.core.model.LegalBlock
import com.truevault.core.model.LegalDocumentKind
import org.junit.Test

/**
 * The parser that turns the bundled Markdown into what the user actually reads.
 *
 * Two properties matter more than formatting fidelity. First, a document must never fail to display:
 * whatever the parser does not recognise has to come out as readable text, because a blank legal
 * screen is a consent flow with nothing to consent to. Second, unresolved placeholders must be
 * detectable, so a policy that still says "[PRIVACY EMAIL REQUIRED]" cannot ship quietly.
 */
class LegalDocumentParserTest {

    private val sample = """
        # Privacy Policy

        **Version:** 1.0
        **Effective date:** 2026-01-01
        **Last updated:** 2026-01-02

        ## 1. Introduction

        TrueVault is an app that encrypts files.
        It keeps them on your device.

        ## 2. What it accesses

        - Files you select
        - Folders you grant

        > This is a note.

        | Data | Collected |
        |---|---|
        | Photos | No |
        | Files | No |

        ---

        ## 3. Steps

        1. Encrypt
        2. Verify
        3. Commit
    """.trimIndent()

    @Test
    fun `document metadata is taken from the document, not from a constant`() {
        val doc = LegalDocumentParser.parse(LegalDocumentKind.PRIVACY_POLICY, sample)

        assertThat(doc.title).isEqualTo("Privacy Policy")
        assertThat(doc.version).isEqualTo("1.0")
        assertThat(doc.effectiveDate).isEqualTo("2026-01-01")
        assertThat(doc.lastUpdated).isEqualTo("2026-01-02")
    }

    @Test
    fun `each heading starts a section that a screen reader can navigate`() {
        val doc = LegalDocumentParser.parse(LegalDocumentKind.PRIVACY_POLICY, sample)

        val headings = doc.sections.mapNotNull { it.heading }
        assertThat(headings).containsExactly(
            "1. Introduction",
            "2. What it accesses",
            "3. Steps",
        ).inOrder()
        assertThat(doc.sections.map { it.headingLevel }.distinct()).containsExactly(2)
    }

    @Test
    fun `sections get stable ids so a link or a search result can target them`() {
        val doc = LegalDocumentParser.parse(LegalDocumentKind.PRIVACY_POLICY, sample)

        val ids = doc.sections.map { it.id }
        assertThat(ids).containsNoDuplicates()
        assertThat(ids.first()).isEqualTo("1-introduction")
    }

    @Test
    fun `consecutive lines join into one paragraph rather than becoming separate ones`() {
        val doc = LegalDocumentParser.parse(LegalDocumentKind.PRIVACY_POLICY, sample)
        val intro = doc.sections.first { it.heading == "1. Introduction" }

        assertThat(intro.blocks).hasSize(1)
        assertThat((intro.blocks.single() as LegalBlock.Paragraph).text)
            .isEqualTo("TrueVault is an app that encrypts files. It keeps them on your device.")
    }

    @Test
    fun `bullets, quotes, tables and rules are all recognised`() {
        val doc = LegalDocumentParser.parse(LegalDocumentKind.PRIVACY_POLICY, sample)
        val section = doc.sections.first { it.heading == "2. What it accesses" }

        val bullets = section.blocks.filterIsInstance<LegalBlock.Bullets>().single()
        assertThat(bullets.items).containsExactly("Files you select", "Folders you grant").inOrder()

        val quote = section.blocks.filterIsInstance<LegalBlock.Quote>().single()
        assertThat(quote.text).isEqualTo("This is a note.")

        val table = section.blocks.filterIsInstance<LegalBlock.Table>().single()
        assertThat(table.header).containsExactly("Data", "Collected").inOrder()
        assertThat(table.rows).hasSize(2)
        assertThat(table.rows.first()).containsExactly("Photos", "No").inOrder()

        assertThat(section.blocks).contains(LegalBlock.Divider)
    }

    @Test
    fun `numbered lists stay numbered`() {
        val doc = LegalDocumentParser.parse(LegalDocumentKind.PRIVACY_POLICY, sample)
        val steps = doc.sections.first { it.heading == "3. Steps" }

        val numbered = steps.blocks.filterIsInstance<LegalBlock.Numbered>().single()
        assertThat(numbered.items).containsExactly("Encrypt", "Verify", "Commit").inOrder()
    }

    @Test
    fun `inline emphasis and links are reduced to plain readable text`() {
        val doc = LegalDocumentParser.parse(
            LegalDocumentKind.TERMS_OF_SERVICE,
            "## X\n\nSee **this** and [the policy](privacy-policy.md) and `code`.",
        )

        val text = (doc.sections.single().blocks.single() as LegalBlock.Paragraph).text
        assertThat(text).isEqualTo("See this and the policy and code.")
    }

    @Test
    fun `an unrecognised construct still renders as text rather than disappearing`() {
        val weird = "## X\n\n<<< something nobody planned for >>>"

        val doc = LegalDocumentParser.parse(LegalDocumentKind.TERMS_OF_SERVICE, weird)

        val text = (doc.sections.single().blocks.single() as LegalBlock.Paragraph).text
        assertThat(text).contains("something nobody planned for")
    }

    @Test
    fun `an empty document produces an empty but valid result instead of throwing`() {
        val doc = LegalDocumentParser.parse(LegalDocumentKind.TERMS_OF_SERVICE, "")

        assertThat(doc.sections).isEmpty()
        assertThat(doc.searchableText).isEmpty()
    }

    @Test
    fun `searchable text covers headings, paragraphs, lists and table cells`() {
        val doc = LegalDocumentParser.parse(LegalDocumentKind.PRIVACY_POLICY, sample)

        assertThat(doc.searchableText).contains("What it accesses")
        assertThat(doc.searchableText).contains("Folders you grant")
        assertThat(doc.searchableText).contains("Photos")
        assertThat(doc.searchableText).contains("Commit")
    }

    @Test
    fun `unresolved placeholders are found so they cannot ship silently`() {
        val withPlaceholders = """
            # Privacy Policy
            Contact [PRIVACY EMAIL REQUIRED] at [LEGAL BUSINESS NAME REQUIRED].
            Governed by [GOVERNING LAW REQUIRED]. See [PRIVACY EMAIL REQUIRED] again.
        """.trimIndent()

        val report = LegalDocumentParser.findPlaceholders(withPlaceholders)

        assertThat(report.isReady).isFalse()
        assertThat(report.placeholders).containsExactly(
            "[GOVERNING LAW REQUIRED]",
            "[LEGAL BUSINESS NAME REQUIRED]",
            "[PRIVACY EMAIL REQUIRED]",
        )
    }

    @Test
    fun `a fully resolved document reports ready`() {
        val resolved = "# Terms\nContact support@example.com in India."

        assertThat(LegalDocumentParser.findPlaceholders(resolved).isReady).isTrue()
    }

    @Test
    fun `ordinary bracketed text is not mistaken for a placeholder`() {
        // "[see section 4]" and "[LEGAL REVIEW]" are prose, not unresolved configuration. Only the
        // REQUIRED/OPTIONAL marker makes something a placeholder.
        val prose = "# Terms\nSee [see section 4] and the clause marked [LEGAL REVIEW]."

        assertThat(LegalDocumentParser.findPlaceholders(prose).isReady).isTrue()
    }
}
