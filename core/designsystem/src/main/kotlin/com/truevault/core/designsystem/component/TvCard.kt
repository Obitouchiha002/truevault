package com.truevault.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.truevault.core.designsystem.theme.TvElevation
import com.truevault.core.designsystem.theme.TvRadius
import com.truevault.core.designsystem.theme.TvSpacing

private val HairlineWidth = 1.dp

/**
 * The standard TrueVault surface: 20dp radius, no elevation, a hairline outline instead of a
 * shadow. Flat surfaces keep long lists calm; a shadow under every card makes a dashboard noisy.
 */
@Composable
fun TvCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: Dp = TvSpacing.standard,
    border: BorderStroke? = BorderStroke(HairlineWidth, MaterialTheme.colorScheme.outlineVariant),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(TvRadius.card)
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val elevation = CardDefaults.cardElevation(defaultElevation = TvElevation.card)

    if (onClick == null) {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, elevation = elevation) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            border = border,
            elevation = elevation,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/** A card carrying emphasis. At most one of these appears per screen. */
@Composable
fun TvAccentCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = TvSpacing.section,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    TvCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = contentPadding,
        border = null,
        onClick = onClick,
        content = content,
    )
}
