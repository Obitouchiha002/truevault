package com.truevault.core.legal

import com.truevault.core.model.LegalBlock
import com.truevault.core.model.LegalDocument
import com.truevault.core.model.LegalDocumentKind
import com.truevault.core.model.LegalPlaceholderReport
import com.truevault.core.model.LegalSection

/**
 * Parses the bundled Markdown legal documents into a structure Compose can render.
 *
 * Why a parser at all, rather than a WebView pointed at the bundled HTML: a WebView is a browser
 * engine, with a JavaScript bridge, a cache and a network stack, placed in front of the one document
 * the user is being asked to rely on. There is nothing it renders here that structured text cannot,
 * and structured text also gives us screen-reader headings, selectable text, scroll progress and
 * in-document search for free.
 *
 * The grammar is deliberately the small subset the documents actually use — headings, paragraphs,
 * bullets, numbered lists, quotes, tables and rules. Anything unrecognised becomes a paragraph, so
 * a document can never fail to display because of a formatting construct nobody anticipated.
 */
object LegalDocumentParser {

    private val PLACEHOLDER = Regex("""\[[A-Z][A-Z /]*(REQUIRED|OPTIONAL)[A-Z ]*]""")
    private val METADATA = Regex("""^\*\*([^:*]+):\*\*\s*(.+)$""")
    private val BULLET = Regex("""^[-*]\s+(.*)$""")
    private val NUMBERED = Regex("""^\d+\.\s+(.*)$""")

    fun parse(kind: LegalDocumentKind, markdown: String): LegalDocument {
        val lines = markdown.replace("\r\n", "\n").split("\n")

        var title = when (kind) {
            LegalDocumentKind.TERMS_OF_SERVICE -> "Terms of Service"
            LegalDocumentKind.PRIVACY_POLICY -> "Privacy Policy"
        }
        val metadata = mutableMapOf<String, String>()

        val sections = mutableListOf<LegalSection>()
        var heading: String? = null
        var headingLevel = 0
        var blocks = mutableListOf<LegalBlock>()

        fun flush() {
            if (heading != null || blocks.isNotEmpty()) {
                sections += LegalSection(
                    id = heading?.slug() ?: "section-${sections.size}",
                    heading = heading,
                    headingLevel = headingLevel,
                    blocks = blocks.toList(),
                )
            }
            blocks = mutableListOf()
        }

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trim()

            if (line.isEmpty()) {
                i++
                continue
            }

            // Metadata lines (**Version:** 1.0) belong to the document, not to a section.
            METADATA.matchEntire(line)?.let { match ->
                metadata[match.groupValues[1].trim().lowercase()] = match.groupValues[2].trim()
                i++
                return@let
            }?.also { continue }

            if (line.startsWith("#")) {
                val level = line.takeWhile { it == '#' }.length
                val text = line.drop(level).trim().stripInline()
                if (level == 1 && sections.isEmpty() && blocks.isEmpty()) {
                    title = text
                    i++
                    continue
                }
                flush()
                heading = text
                headingLevel = level
                i++
                continue
            }

            if (line == "---" || line == "***" || line == "___") {
                blocks += LegalBlock.Divider
                i++
                continue
            }

            if (line.startsWith("|")) {
                val rows = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows += lines[i].trim()
                    i++
                }
                blocks += parseTable(rows)
                continue
            }

            if (line.startsWith(">")) {
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quote += lines[i].trim().removePrefix(">").trim()
                    i++
                }
                blocks += LegalBlock.Quote(quote.joinToString(" ").stripInline())
                continue
            }

            if (BULLET.matches(line) || NUMBERED.matches(line)) {
                val numbered = NUMBERED.matches(line)
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val candidate = lines[i].trim()
                    val match = if (numbered) NUMBERED.matchEntire(candidate) else BULLET.matchEntire(candidate)
                    if (match == null) break
                    items += match.groupValues[1].stripInline()
                    i++
                }
                blocks += if (numbered) LegalBlock.Numbered(items) else LegalBlock.Bullets(items)
                continue
            }

            val paragraph = mutableListOf(line)
            i++
            while (i < lines.size) {
                val next = lines[i].trim()
                if (next.isEmpty() ||
                    next.startsWith("#") || next.startsWith("|") || next.startsWith(">") ||
                    next == "---" || BULLET.matches(next) || NUMBERED.matches(next)
                ) {
                    break
                }
                paragraph += next
                i++
            }
            blocks += LegalBlock.Paragraph(paragraph.joinToString(" ").stripInline())
        }

        flush()

        return LegalDocument(
            kind = kind,
            title = title,
            version = metadata["version"].orEmpty(),
            effectiveDate = metadata["effective date"].orEmpty(),
            lastUpdated = metadata["last updated"].orEmpty(),
            sections = sections,
        )
    }

    /**
     * Finds unresolved `[SOMETHING REQUIRED]` placeholders.
     *
     * Shipping a policy that reads "[PRIVACY EMAIL REQUIRED]" is worse than shipping none: it has
     * the shape of a legal document and answers nothing, and the user cannot tell the difference
     * until they need the answer.
     */
    fun findPlaceholders(markdown: String): LegalPlaceholderReport =
        LegalPlaceholderReport(
            PLACEHOLDER.findAll(markdown).map { it.value }.distinct().sorted().toList(),
        )

    private fun parseTable(rows: List<String>): LegalBlock.Table {
        val cells = rows.map { row ->
            row.trim().trim('|').split("|").map { it.trim().stripInline() }
        }
        val separatorIndex = cells.indexOfFirst { row ->
            row.isNotEmpty() && row.all { cell -> cell.isNotEmpty() && cell.all { it == '-' || it == ':' } }
        }
        return if (separatorIndex == 1) {
            LegalBlock.Table(header = cells.first(), rows = cells.drop(2))
        } else {
            LegalBlock.Table(header = null, rows = cells)
        }
    }

    /**
     * Strips the inline Markdown the renderer does not style.
     *
     * Emphasis markers and link syntax are removed rather than rendered: a legal document whose
     * meaning depends on which words are bold is a badly written legal document, and a tappable link
     * inside bundled text is a navigation surface nobody audited.
     */
    private fun String.stripInline(): String =
        replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
            .replace("**", "")
            .replace("`", "")
            .replace(Regex("""(?<!\w)\*(?!\s)([^*]+)(?<!\s)\*(?!\w)"""), "$1")
            .trim()

    private fun String.slug(): String =
        lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-').take(48)
}
