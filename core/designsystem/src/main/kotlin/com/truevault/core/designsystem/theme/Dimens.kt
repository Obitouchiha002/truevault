package com.truevault.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Layout tokens. Every screen uses these instead of literal dp values, so spacing stays consistent
 * when a screen is redesigned.
 */
object TvSpacing {
    /** Horizontal padding for every screen's content. */
    val screenHorizontal = 20.dp

    val xs = 4.dp
    val small = 8.dp
    val standard = 16.dp
    val section = 24.dp
    val large = 32.dp
    val xl = 48.dp

    /** Minimum interactive size, per accessibility guidance. */
    val minTouchTarget = 48.dp

    /** Bottom padding so content clears the navigation bar and the FAB. */
    val contentBottom = 96.dp
}

object TvRadius {
    val card = 20.dp
    val button = 16.dp
    val pill = 999.dp
    val sheet = 28.dp
    val small = 12.dp
}

object TvElevation {
    val none = 0.dp
    val card = 0.dp
    val raised = 2.dp
    val sheet = 8.dp
}

internal val TrueVaultShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(TvRadius.button),
    large = RoundedCornerShape(TvRadius.card),
    extraLarge = RoundedCornerShape(TvRadius.sheet),
)
