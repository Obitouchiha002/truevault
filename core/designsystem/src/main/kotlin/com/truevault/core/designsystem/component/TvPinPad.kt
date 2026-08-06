package com.truevault.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.truevault.core.designsystem.R
import com.truevault.core.designsystem.theme.TvMotion
import com.truevault.core.designsystem.theme.TvSpacing

/**
 * A numeric keypad for PIN entry.
 *
 * Deliberately its own keypad rather than a text field with a numeric keyboard:
 *
 * - The system keyboard offers autocorrect, clipboard, prediction and third-party IMEs. A PIN typed
 *   through a third-party keyboard passes through that keyboard's process.
 * - The entered digits are never rendered — only filled dots — so a shoulder-surfer sees length at
 *   most, and a screenshot (if the user has that permitted) captures nothing.
 *
 * The dot row is a single accessibility node reporting how many digits have been entered, never
 * which ones.
 */
@Composable
fun TvPinPad(
    length: Int,
    entered: Int,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TvSpacing.section),
    ) {
        PinDots(length = length, entered = entered)

        Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.section)) {
                    row.forEach { digit ->
                        PinKey(label = digit.toString(), enabled = enabled) { onDigit(digit) }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.section)) {
                // Empty cell keeps 0 centred without a decorative key the user might press.
                Box(modifier = Modifier.size(KEY_SIZE))
                PinKey(label = "0", enabled = enabled) { onDigit('0') }
                BackspaceKey(enabled = enabled && entered > 0, onClick = onBackspace)
            }
        }
    }
}

@Composable
private fun PinDots(length: Int, entered: Int) {
    val description = stringResource(R.string.tv_pin_entered, entered, length)

    Row(
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.standard),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        repeat(length) { index ->
            val filled = index < entered
            val size by animateDpAsState(
                targetValue = if (filled) 16.dp else 12.dp,
                animationSpec = TvMotion.standardSpec(TvMotion.DURATION_SHORT),
                label = "pinDot",
            )

            Box(
                modifier = Modifier
                    .size(size)
                    .background(
                        color = if (filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(KEY_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun BackspaceKey(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(KEY_SIZE)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = stringResource(R.string.tv_pin_backspace),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
            modifier = Modifier.padding(TvSpacing.standard),
        )
    }
}

private val KEY_SIZE = 72.dp
