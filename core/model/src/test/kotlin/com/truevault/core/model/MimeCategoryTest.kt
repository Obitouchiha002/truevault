package com.truevault.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MimeCategoryTest {

    @Test
    fun `MIME type is trusted first`() {
        assertThat(mimeCategoryOf("image/jpeg", "whatever.txt")).isEqualTo(MimeCategory.PHOTO)
        assertThat(mimeCategoryOf("video/mp4", null)).isEqualTo(MimeCategory.VIDEO)
        assertThat(mimeCategoryOf("application/pdf", null)).isEqualTo(MimeCategory.DOCUMENT)
        assertThat(mimeCategoryOf("audio/mpeg", null)).isEqualTo(MimeCategory.AUDIO)
        assertThat(mimeCategoryOf("application/zip", null)).isEqualTo(MimeCategory.ARCHIVE)
    }

    @Test
    fun `a generic MIME type falls back to the extension`() {
        // Providers report application/octet-stream constantly. Ignoring the extension here would
        // put a large share of a user's documents into "Other".
        assertThat(mimeCategoryOf("application/octet-stream", "contract.pdf"))
            .isEqualTo(MimeCategory.DOCUMENT)
        assertThat(mimeCategoryOf("application/octet-stream", "holiday.HEIC"))
            .isEqualTo(MimeCategory.PHOTO)
    }

    @Test
    fun `a missing MIME type still classifies by extension`() {
        assertThat(mimeCategoryOf(null, "clip.mkv")).isEqualTo(MimeCategory.VIDEO)
        assertThat(mimeCategoryOf(null, "song.flac")).isEqualTo(MimeCategory.AUDIO)
    }

    @Test
    fun `an unknown file is Other rather than rejected`() {
        assertThat(mimeCategoryOf(null, "mystery.xyz")).isEqualTo(MimeCategory.OTHER)
        assertThat(mimeCategoryOf(null, null)).isEqualTo(MimeCategory.OTHER)
    }

    @Test
    fun `unicode file names are classified normally`() {
        assertThat(mimeCategoryOf(null, "दस्तावेज़.pdf")).isEqualTo(MimeCategory.DOCUMENT)
        assertThat(mimeCategoryOf(null, "🎉photo.jpg")).isEqualTo(MimeCategory.PHOTO)
    }

    @Test
    fun `a source with no reported size is flagged rather than assumed`() {
        val source = SelectedSource(
            uriToken = "content://x/1",
            displayName = "a.bin",
            sizeBytes = null,
            mimeType = null,
            isFromPhotoPicker = false,
        )

        assertThat(source.hasKnownSize).isFalse()
        assertThat(source.describeSafely()).doesNotContain("a.bin")
    }
}
