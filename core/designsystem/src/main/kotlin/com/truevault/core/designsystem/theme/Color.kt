package com.truevault.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * TrueVault palette.
 *
 * Direction: calm, high-contrast, privacy-serious. A deep navy-black base with a single emerald
 * accent, so the accent only ever means "this is the safe, primary action". Status colours
 * (success / warning / error) are reserved for privacy state and never used decoratively.
 *
 * The neutrals sit slightly deeper and cooler than they used to, and the accent slightly brighter,
 * so text keeps its contrast against the larger corner radii and softer surfaces. Contrast ratios
 * were the constraint, not the mood: an app people read warnings in cannot trade legibility for
 * atmosphere.
 */

// --- Dark ----------------------------------------------------------------------------------------
private val DarkBackground = Color(0xFF090C11)
private val DarkSurface = Color(0xFF121820)
private val DarkSurfaceLow = Color(0xFF0D1219)
private val DarkSurfaceHigh = Color(0xFF19212B)
private val DarkSurfaceHighest = Color(0xFF202A36)
private val DarkSurfaceVariant = Color(0xFF1C2530)
private val DarkOutline = Color(0xFF32404E)
private val DarkOutlineVariant = Color(0xFF26313D)
private val DarkOnBackground = Color(0xFFE7EDF3)
private val DarkOnSurfaceVariant = Color(0xFF9AA9B8)

private val EmeraldDark = Color(0xFF45E0B4)
private val EmeraldOnDark = Color(0xFF00281E)
private val EmeraldContainerDark = Color(0xFF10523F)
private val EmeraldOnContainerDark = Color(0xFFA9F2DC)

private val BlueDark = Color(0xFF86ACFF)
private val BlueOnDark = Color(0xFF08204C)
private val BlueContainerDark = Color(0xFF1D3566)
private val BlueOnContainerDark = Color(0xFFD9E4FF)

private val VioletDark = Color(0xFFB6A6FF)
private val VioletOnDark = Color(0xFF221049)
private val VioletContainerDark = Color(0xFF362766)
private val VioletOnContainerDark = Color(0xFFE5DDFF)

private val RedDark = Color(0xFFFF8A80)
private val RedOnDark = Color(0xFF450F0B)
private val RedContainerDark = Color(0xFF6B221C)
private val RedOnContainerDark = Color(0xFFFFDAD5)

// --- Light ---------------------------------------------------------------------------------------
private val LightBackground = Color(0xFFF6F8FB)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceLow = Color(0xFFF0F4F9)
private val LightSurfaceHigh = Color(0xFFE9EFF5)
private val LightSurfaceHighest = Color(0xFFE2EAF2)
private val LightSurfaceVariant = Color(0xFFE7EDF3)
private val LightOutline = Color(0xFFB9C6D3)
private val LightOutlineVariant = Color(0xFFDCE4EC)
private val LightOnBackground = Color(0xFF0E1A26)
private val LightOnSurfaceVariant = Color(0xFF556676)

private val TealLight = Color(0xFF0A7A6B)
private val TealOnLight = Color(0xFFFFFFFF)
private val TealContainerLight = Color(0xFFB5EFE2)
private val TealOnContainerLight = Color(0xFF00201B)

private val BlueLight = Color(0xFF2B57A7)
private val BlueOnLight = Color(0xFFFFFFFF)
private val BlueContainerLight = Color(0xFFD9E4FF)
private val BlueOnContainerLight = Color(0xFF0A1F4A)

private val VioletLight = Color(0xFF5B45B0)
private val VioletOnLight = Color(0xFFFFFFFF)
private val VioletContainerLight = Color(0xFFE6DEFF)
private val VioletOnContainerLight = Color(0xFF1E1046)

private val RedLight = Color(0xFFB3261E)
private val RedOnLight = Color(0xFFFFFFFF)
private val RedContainerLight = Color(0xFFFFDAD5)
private val RedOnContainerLight = Color(0xFF410E0A)

internal val TrueVaultDarkColorScheme = darkColorScheme(
    primary = EmeraldDark,
    onPrimary = EmeraldOnDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldOnContainerDark,
    inversePrimary = TealLight,
    secondary = BlueDark,
    onSecondary = BlueOnDark,
    secondaryContainer = BlueContainerDark,
    onSecondaryContainer = BlueOnContainerDark,
    tertiary = VioletDark,
    onTertiary = VioletOnDark,
    tertiaryContainer = VioletContainerDark,
    onTertiaryContainer = VioletOnContainerDark,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF070B0F),
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    surfaceTint = EmeraldDark,
    inverseSurface = Color(0xFFE7EDF3),
    inverseOnSurface = Color(0xFF10161E),
    error = RedDark,
    onError = RedOnDark,
    errorContainer = RedContainerDark,
    onErrorContainer = RedOnContainerDark,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = Color(0xFF000000),
)

internal val TrueVaultLightColorScheme = lightColorScheme(
    primary = TealLight,
    onPrimary = TealOnLight,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealOnContainerLight,
    inversePrimary = EmeraldDark,
    secondary = BlueLight,
    onSecondary = BlueOnLight,
    secondaryContainer = BlueContainerLight,
    onSecondaryContainer = BlueOnContainerLight,
    tertiary = VioletLight,
    onTertiary = VioletOnLight,
    tertiaryContainer = VioletContainerLight,
    onTertiaryContainer = VioletOnContainerLight,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceLow,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    surfaceTint = TealLight,
    inverseSurface = Color(0xFF17222D),
    inverseOnSurface = Color(0xFFEDF2F7),
    error = RedLight,
    onError = RedOnLight,
    errorContainer = RedContainerLight,
    onErrorContainer = RedOnContainerLight,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color(0xFF000000),
)

/**
 * Colours Material 3 has no slot for, but that TrueVault needs to state privacy status honestly.
 *
 * `success` is not the same as `primary`: primary means "do this", success means "this is proven
 * done". Conflating them is how vault apps end up implying a file is safe when it is not.
 */
@Immutable
data class TvStatusColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfoContainer: Color,
    val infoContainer: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
)

internal val DarkStatusColors = TvStatusColors(
    success = Color(0xFF4ADE9B),
    onSuccess = Color(0xFF00301D),
    successContainer = Color(0xFF11402E),
    onSuccessContainer = Color(0xFFB6F5D6),
    warning = Color(0xFFF6BD52),
    onWarning = Color(0xFF3A2504),
    warningContainer = Color(0xFF4C360D),
    onWarningContainer = Color(0xFFFFE1AC),
    info = BlueDark,
    infoContainer = BlueContainerDark,
    onInfoContainer = BlueOnContainerDark,
    neutralContainer = Color(0xFF1E2833),
    onNeutralContainer = Color(0xFFAEBDCB),
)

internal val LightStatusColors = TvStatusColors(
    success = Color(0xFF0F7A4E),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFC2F3DA),
    onSuccessContainer = Color(0xFF00291A),
    warning = Color(0xFF9A6400),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFE3B0),
    onWarningContainer = Color(0xFF2E1F00),
    info = BlueLight,
    infoContainer = BlueContainerLight,
    onInfoContainer = BlueOnContainerLight,
    neutralContainer = Color(0xFFE6ECF2),
    onNeutralContainer = Color(0xFF48586A),
)

val LocalTvStatusColors = staticCompositionLocalOf { DarkStatusColors }

/**
 * Note card tints.
 *
 * Deliberately not the Material container roles. Those carry meaning in this app — primary is the
 * safe action, error is a failure — and a note tinted "error red" reads as a problem rather than a
 * colour someone picked. They are also far more saturated than a card full of body text can carry.
 *
 * These are near-neutral washes: barely-there in light, barely-lifted in dark, so the text keeps its
 * contrast against the background either way and the colour reads as a label rather than a
 * highlight.
 */
object TvNoteColors {

    private val light = listOf(
        Color(0xFFFFFFFF), // default — plain card
        Color(0xFFFFF3D6), // sand
        Color(0xFFE2F3E7), // sage
        Color(0xFFE3EFFA), // sky
        Color(0xFFF6E6F0), // orchid
        Color(0xFFFFE7E0), // clay
    )

    private val dark = listOf(
        Color(0xFF161E28), // default — plain card
        Color(0xFF33291A), // sand
        Color(0xFF1B2E23), // sage
        Color(0xFF1B2836), // sky
        Color(0xFF2C1F2A), // orchid
        Color(0xFF33221C), // clay
    )

    val count: Int get() = light.size

    /** Falls back to the plain card for an index a future version wrote and this one does not know. */
    fun tint(index: Int, darkTheme: Boolean): Color {
        val palette = if (darkTheme) dark else light
        return palette.getOrElse(index) { palette.first() }
    }
}
