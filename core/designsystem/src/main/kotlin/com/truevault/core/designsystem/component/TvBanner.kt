package com.truevault.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.truevault.core.designsystem.theme.TrueVaultTheme
import com.truevault.core.designsystem.theme.TvRadius
import com.truevault.core.designsystem.theme.TvSpacing

enum class TvBannerTone { Info, Success, Warning, Error }

/**
 * An inline explanation block.
 *
 * TrueVault uses this to state platform limitations plainly, in the place where the limitation
 * matters — for example, that the scanner can only see what the user has granted access to. Those
 * sentences are a product requirement, not fine print, so they get a first-class component.
 */
@Composable
fun TvBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: TvBannerTone = TvBannerTone.Info,
    title: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val status = TrueVaultTheme.statusColors
    val scheme = MaterialTheme.colorScheme

    val (container, content, icon) = when (tone) {
        TvBannerTone.Info -> Triple(status.infoContainer, status.onInfoContainer, Icons.Outlined.Info)
        TvBannerTone.Success -> Triple(status.successContainer, status.onSuccessContainer, Icons.Filled.CheckCircle)
        TvBannerTone.Warning -> Triple(status.warningContainer, status.onWarningContainer, Icons.Filled.WarningAmber)
        TvBannerTone.Error -> Triple(scheme.errorContainer, scheme.onErrorContainer, Icons.Filled.ErrorOutline)
    }

    BannerLayout(
        modifier = modifier,
        container = container,
        content = content,
        icon = icon,
        title = title,
        text = text,
        action = action,
    )
}

@Composable
private fun BannerLayout(
    modifier: Modifier,
    container: Color,
    content: Color,
    icon: ImageVector,
    title: String?,
    text: String,
    action: @Composable (() -> Unit)?,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(TvRadius.small))
            .padding(TvSpacing.standard),
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 1.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.xs)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = content,
            )
            action?.invoke()
        }
    }
}
