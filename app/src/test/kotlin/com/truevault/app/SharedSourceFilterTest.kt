package com.truevault.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The share entry point takes URIs from an app the user has not vouched for.
 *
 * Whatever arrives there was chosen by the sender, so this is the boundary where a hostile app gets
 * to name a file. These tests pin what is refused, because the refusals *are* the security property
 * — a later "let's also accept file:// for compatibility" would otherwise sail through review.
 */
class SharedSourceFilterTest {

    private fun accepts(scheme: String?, authority: String?) =
        SharedIntentReader.isAcceptableSource(scheme, authority)

    @Test
    fun `an ordinary content share from another app is accepted`() {
        assertThat(accepts("content", "com.android.providers.media.documents")).isTrue()
        assertThat(accepts("content", "com.whatsapp.provider.media")).isTrue()
    }

    @Test
    fun `a file uri is refused, whatever it points at`() {
        // No permission grant stands behind a path. An attacker can name anything this app can
        // read — and this app can read its own private storage.
        assertThat(accepts("file", null)).isFalse()
    }

    @Test
    fun `this app's own provider is refused`() {
        // Either a loop, or an attempt to walk decrypted plaintext back into the vault.
        assertThat(accepts("content", "com.truevault.app.fileprovider")).isFalse()
        assertThat(accepts("content", "com.truevault.app")).isFalse()
        assertThat(accepts("content", "com.truevault.app.debug.fileprovider")).isFalse()
    }

    @Test
    fun `an unrelated app whose name merely starts similarly is still accepted`() {
        // The check must match the authority or a dotted child of it, not any string with the same
        // prefix — otherwise a legitimate sender could be refused for its name.
        assertThat(accepts("content", "com.truevaultbackup.provider")).isTrue()
    }

    @Test
    fun `schemes with no grant behind them are refused`() {
        listOf("http", "https", "android.resource", "data", "javascript", null).forEach { scheme ->
            assertThat(accepts(scheme, "anything")).isFalse()
        }
    }
}
