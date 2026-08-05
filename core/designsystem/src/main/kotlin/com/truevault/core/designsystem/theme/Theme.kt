package com.truevault.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.truevault.core.model.ThemePreference

/**
 * The single theme entry point for the app.
 *
 * Dynamic colour is opt-in and off by default: TrueVault's palette carries meaning (emerald =
 * safe primary action, amber = attention, red = failure), and wallpaper-derived colours would break
 * that mapping.
 */
@Composable
fun TrueVaultTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamicColor && supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor && supportsDynamic && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> TrueVaultDarkColorScheme
        else -> TrueVaultLightColorScheme
    }

    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(
        LocalTvStatusColors provides statusColors,
        LocalReducedMotion provides rememberReducedMotion(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TrueVaultTypography,
            shapes = TrueVaultShapes,
            content = content,
        )
    }
}

/** Status colours that Material 3 has no slot for. */
object TrueVaultTheme {
    val statusColors: TvStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTvStatusColors.current
}
