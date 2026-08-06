package com.truevault.core.data

import com.google.common.truth.Truth.assertThat
import com.truevault.core.model.ScanMatchType
import com.truevault.core.model.SelectedSource
import org.junit.Test

/**
 * Scanner narrowing and reporting.
 *
 * The scanner is the feature most able to mislead: it looks like an antivirus, so whatever it says
 * gets believed. These checks pin the two places where it could overstate itself — the pre-filter
 * that decides which files are examined at all, and the counters the summary screen reads.
 */
class PrivacyScanReportTest {

    private fun source(name: String, size: Long?) = SelectedSource(
        uriToken = "content://tree/$name",
        displayName = name,
        sizeBytes = size,
        mimeType = "image/jpeg",
        isFromPhotoPicker = false,
    )

    private fun finding(
        matchType: ScanMatchType,
        confidence: Int = 100,
        resolved: Boolean = false,
    ) = ScanFinding(
        id = "f-${matchType.name}-$confidence-$resolved",
        scanId = "scan",
        vaultItemId = "item",
        vaultItemName = "name",
        matchType = matchType,
        matchedUriToken = "content://tree/x",
        matchedDisplayName = "x",
        matchedSizeBytes = 10,
        confidence = confidence,
        resolved = resolved,
    )

    @Test
    fun `only files whose size matches something in the vault are examined`() {
        val sources = listOf(source("a.jpg", 100), source("b.jpg", 999), source("c.jpg", 250))

        val candidates = candidateSources(sources, setOf(100L, 250L))

        assertThat(candidates.map { it.displayName }).containsExactly("a.jpg", "c.jpg")
    }

    @Test
    fun `a file whose size the provider did not report is never treated as a match`() {
        val sources = listOf(source("unknown.jpg", null))

        // Excluded rather than hashed-just-in-case: reporting a file the scanner has no evidence
        // about is exactly the kind of scary-and-wrong result this app must not produce.
        assertThat(candidateSources(sources, setOf(100L))).isEmpty()
    }

    @Test
    fun `an empty vault produces no candidates at all`() {
        val sources = listOf(source("a.jpg", 100), source("b.jpg", 200))

        assertThat(candidateSources(sources, emptySet())).isEmpty()
    }

    @Test
    fun `a zero byte file matches only when the vault holds a zero byte item`() {
        val sources = listOf(source("empty.bin", 0))

        assertThat(candidateSources(sources, setOf(0L))).hasSize(1)
        assertThat(candidateSources(sources, setOf(1L))).isEmpty()
    }

    @Test
    fun `the report counts each match type separately`() {
        val report = ScanReport(
            scanId = "scan",
            filesExamined = 40,
            findings = listOf(
                finding(ScanMatchType.EXACT_DUPLICATE),
                finding(ScanMatchType.EXACT_DUPLICATE, confidence = 99),
                finding(ScanMatchType.ORIGINAL_REMAINS),
                finding(ScanMatchType.CLOUD_COPY_POSSIBLE),
                finding(ScanMatchType.POSSIBLE_DUPLICATE),
            ),
            truncated = false,
        )

        assertThat(report.exactDuplicates).isEqualTo(2)
        assertThat(report.originalsRemaining).isEqualTo(1)
        assertThat(report.cloudCopies).isEqualTo(1)
        // A possible duplicate is deliberately not counted as an exact one. The distinction is the
        // difference between "there is another copy" and "there might be".
        assertThat(report.exactDuplicates).isNotEqualTo(report.findings.size)
    }

    @Test
    fun `a scan that found nothing reports zero rather than an empty-looking failure`() {
        val report = ScanReport(scanId = "scan", filesExamined = 0, findings = emptyList(), truncated = false)

        assertThat(report.exactDuplicates).isEqualTo(0)
        assertThat(report.originalsRemaining).isEqualTo(0)
        assertThat(report.cloudCopies).isEqualTo(0)
        assertThat(report.truncated).isFalse()
    }

    @Test
    fun `a truncated scan keeps its flag so the UI can say the list is incomplete`() {
        val report = ScanReport(
            scanId = "scan",
            filesExamined = 5_000,
            findings = listOf(finding(ScanMatchType.EXACT_DUPLICATE)),
            truncated = true,
        )

        assertThat(report.truncated).isTrue()
    }
}
