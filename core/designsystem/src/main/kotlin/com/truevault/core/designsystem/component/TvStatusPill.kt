package com.truevault.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.truevault.core.designsystem.R
import com.truevault.core.designsystem.theme.TrueVaultTheme
import com.truevault.core.designsystem.theme.TvRadius
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.PrivacyStatus

/**
 * The privacy status of a single vault item, as a compact pill.
 *
 * Colour alone never carries the meaning: every pill has an icon and a text label, so the state is
 * readable for colour-blind users and by a screen reader.
 */
@Composable
fun TvStatusPill(
    status: PrivacyStatus,
    modifier: Modifier = Modifier,
) {
    val visuals = status.visuals()
    val label = stringResource(visuals.labelRes)

    Row(
        modifier = modifier
            .background(visuals.container, RoundedCornerShape(TvRadius.pill))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.xs),
    ) {
        Icon(
            imageVector = visuals.icon,
            contentDescription = null,
            tint = visuals.onContainer,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = visuals.onContainer,
        )
    }
}

private data class StatusVisuals(
    val icon: ImageVector,
    val labelRes: Int,
    val container: Color,
    val onContainer: Color,
)

@Composable
private fun PrivacyStatus.visuals(): StatusVisuals {
    val status = TrueVaultTheme.statusColors
    val scheme = MaterialTheme.colorScheme

    return when (this) {
        PrivacyStatus.SECURED -> StatusVisuals(
            icon = Icons.Filled.Verified,
            labelRes = R.string.tv_status_secured,
            container = status.successContainer,
            onContainer = status.onSuccessContainer,
        )

        PrivacyStatus.ORIGINAL_REMAINS -> StatusVisuals(
            icon = Icons.Filled.VisibilityOff,
            labelRes = R.string.tv_status_original_remains,
            container = status.warningContainer,
            onContainer = status.onWarningContainer,
        )

        PrivacyStatus.DELETE_PENDING -> StatusVisuals(
            icon = Icons.Filled.HourglassEmpty,
            labelRes = R.string.tv_status_delete_pending,
            container = status.neutralContainer,
            onContainer = status.onNeutralContainer,
        )

        PrivacyStatus.DUPLICATE_FOUND -> StatusVisuals(
            icon = Icons.Filled.ContentCopy,
            labelRes = R.string.tv_status_duplicate_found,
            container = status.warningContainer,
            onContainer = status.onWarningContainer,
        )

        PrivacyStatus.IMPORT_FAILED -> StatusVisuals(
            icon = Icons.Filled.ReportProblem,
            labelRes = R.string.tv_status_import_failed,
            container = scheme.errorContainer,
            onContainer = scheme.onErrorContainer,
        )

        PrivacyStatus.VERIFYING -> StatusVisuals(
            icon = Icons.Filled.Autorenew,
            labelRes = R.string.tv_status_verifying,
            container = status.infoContainer,
            onContainer = status.onInfoContainer,
        )

        PrivacyStatus.CORRUPTED -> StatusVisuals(
            icon = Icons.Filled.ErrorOutline,
            labelRes = R.string.tv_status_corrupted,
            container = scheme.errorContainer,
            onContainer = scheme.onErrorContainer,
        )
    }
}
