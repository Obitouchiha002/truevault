package com.truevault.core.common.format

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class ByteFormatTest {

    @Test
    fun `bytes below one thousand are shown raw`() {
        assertThat(formatBytes(0, Locale.US)).isEqualTo("0 B")
        assertThat(formatBytes(999, Locale.US)).isEqualTo("999 B")
    }

    @Test
    fun `kilobytes and megabytes use one decimal place`() {
        assertThat(formatBytes(1_500, Locale.US)).isEqualTo("1.5 kB")
        assertThat(formatBytes(2_400_000, Locale.US)).isEqualTo("2.4 MB")
    }

    @Test
    fun `large values drop the decimal to stay compact`() {
        assertThat(formatBytes(250_000_000, Locale.US)).isEqualTo("250 MB")
    }

    @Test
    fun `multi gigabyte video sizes are handled`() {
        assertThat(formatBytes(4_700_000_000L, Locale.US)).isEqualTo("4.7 GB")
    }
}
