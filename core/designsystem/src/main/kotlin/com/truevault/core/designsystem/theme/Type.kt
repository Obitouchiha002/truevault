package com.truevault.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/*
 * Typography.
 *
 * The system sans-serif is used deliberately: it is already tuned for every locale the device
 * supports, it scales with the user's font-size setting, and it ships no extra bytes.
 *
 * Only three weights appear anywhere in the app — Normal (body), Medium (labels and card titles)
 * and SemiBold (screen and display titles).
 */

private val Sans = FontFamily.SansSerif

private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = Sans,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = TrimBoth,
)

internal val TrueVaultTypography = Typography(
    // Display title — used once per screen at most, e.g. the privacy score.
    displaySmall = style(36, 44, FontWeight.SemiBold, (-0.5)),
    // Screen title
    headlineMedium = style(26, 34, FontWeight.SemiBold, (-0.3)),
    headlineSmall = style(22, 30, FontWeight.SemiBold, (-0.2)),
    // Section title
    titleLarge = style(19, 26, FontWeight.SemiBold, (-0.1)),
    // Card title
    titleMedium = style(16, 22, FontWeight.Medium),
    titleSmall = style(14, 20, FontWeight.Medium, 0.1),
    // Body
    bodyLarge = style(16, 24, FontWeight.Normal, 0.1),
    bodyMedium = style(14, 21, FontWeight.Normal, 0.15),
    bodySmall = style(13, 19, FontWeight.Normal, 0.2),
    // Supporting label / small status text
    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.4),
    labelSmall = style(11, 15, FontWeight.Medium, 0.5),
)
