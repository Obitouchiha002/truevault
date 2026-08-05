package com.truevault.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.truevault.core.designsystem.theme.TrueVaultTheme
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.ThemePreference

/**
 * Preview annotations used across the codebase, so every component is checked in both themes and at
 * a large font scale without each file repeating the configuration.
 */
@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0A0E13)
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFF4F7FA, uiMode = 0x10)
annotation class TvThemePreviews

@Preview(name = "Font 200%", fontScale = 2.0f, showBackground = true, backgroundColor = 0xFF0A0E13)
annotation class TvFontScalePreview

/** Wraps preview content in the app theme plus standard screen padding. */
@Composable
fun TvPreviewSurface(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    TrueVaultTheme(
        themePreference = if (darkTheme) ThemePreference.DARK else ThemePreference.LIGHT,
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(TvSpacing.screenHorizontal)) {
                content()
            }
        }
    }
}
