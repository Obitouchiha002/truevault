package com.truevault.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.truevault.core.designsystem.theme.TrueVaultTheme
import com.truevault.core.designsystem.theme.TvMotion
import kotlin.math.roundToInt

/**
 * The privacy score, drawn as a ring that sweeps to its value once on arrival.
 *
 * The colour follows the score band rather than being fixed, so a low score never looks reassuring.
 * The whole component reports a single spoken description; the ring itself is decorative.
 */
@Composable
fun TvScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 132.dp,
    strokeWidth: Dp = 12.dp,
    label: String,
    contentDescription: String,
) {
    val target = (score.coerceIn(0, 100)) / 100f

    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = TvMotion.standardSpec(TvMotion.DURATION_VALUE),
        label = "privacyScoreSweep",
    )

    val status = TrueVaultTheme.statusColors
    val scheme = MaterialTheme.colorScheme

    val ringColor = when {
        score >= 85 -> status.success
        score >= 60 -> scheme.primary
        score >= 35 -> status.warning
        else -> scheme.error
    }

    Box(
        modifier = modifier
            .size(diameter)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = scheme.surfaceContainerHighest,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            drawArc(
                brush = Brush.sweepGradient(
                    0f to ringColor.copy(alpha = 0.65f),
                    0.75f to ringColor,
                    1f to ringColor.copy(alpha = 0.65f),
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "${(progress * 100).roundToInt()}",
                style = MaterialTheme.typography.displaySmall,
                color = scheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
