package com.truevault.feature.vault.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.truevault.core.model.MimeCategory

/**
 * A vault item's preview.
 *
 * The bytes come from a small, separately encrypted thumbnail — the full-size file is never
 * decrypted to draw a grid cell. Items without a thumbnail get a category icon rather than a blank
 * square, so a document is still recognisable at a glance.
 */
@Composable
internal fun VaultThumbnail(
    itemId: String,
    category: MimeCategory,
    hasThumbnail: Boolean,
    loadThumbnail: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    val bytes by produceState<ByteArray?>(initialValue = null, itemId, hasThumbnail) {
        value = if (hasThumbnail) loadThumbnail(itemId) else null
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val thumbnailBytes = bytes
        if (thumbnailBytes != null) {
            AsyncImage(
                model = thumbnailBytes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = category.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

internal fun MimeCategory.icon(): ImageVector = when (this) {
    MimeCategory.PHOTO -> Icons.Filled.Image
    MimeCategory.VIDEO -> Icons.Filled.VideoFile
    MimeCategory.DOCUMENT -> Icons.Filled.Description
    MimeCategory.AUDIO -> Icons.Filled.AudioFile
    MimeCategory.ARCHIVE -> Icons.Filled.FolderZip
    MimeCategory.OTHER -> Icons.Filled.Folder
}
