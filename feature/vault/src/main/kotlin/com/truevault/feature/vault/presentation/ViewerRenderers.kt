package com.truevault.feature.vault.presentation

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.vault.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders one PDF page at a time, on demand.
 *
 * `PdfRenderer` can only have one page open at a time, so pages are rendered lazily as they scroll
 * into view. A 400-page document therefore costs one bitmap, not four hundred.
 */
@Composable
internal fun PdfViewer(file: File, pageCount: Int, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.small),
    ) {
        items(count = pageCount, key = { it }) { pageIndex ->
            PdfPage(file = file, pageIndex = pageIndex)
        }
    }
}

@Composable
private fun PdfPage(file: File, pageIndex: Int) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file, pageIndex) {
        value = withContext(Dispatchers.IO) { renderPdfPage(file, pageIndex) }
    }

    val page = bitmap
    if (page == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.707f)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    } else {
        Image(
            bitmap = page.asImageBitmap(),
            contentDescription = stringResource(R.string.viewer_pdf_page, pageIndex + 1),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val PDF_RENDER_WIDTH = 1400

private fun renderPdfPage(file: File, pageIndex: Int): Bitmap? = try {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(pageIndex).use { page ->
                val height = (PDF_RENDER_WIDTH.toFloat() / page.width * page.height).toInt()
                val bitmap = createBitmap(PDF_RENDER_WIDTH, height.coerceAtLeast(1))
                // PdfRenderer draws only ink; without a white base the page renders on transparency
                // and reads as black text on black in dark theme.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }
} catch (e: Exception) {
    null
}

/**
 * Plays a decrypted video from the internal cache.
 *
 * The player is released the moment this leaves the composition, which also releases the file
 * handle so the plaintext can actually be deleted on Android's file system semantics.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun VideoViewer(
    file: File,
    mimeType: String?,
    fileName: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var errorText by remember(file) { mutableStateOf<String?>(null) }

    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            // The decrypted file in the cache has a random name and no extension, so ExoPlayer
            // cannot guess the container from the path. Telling it the container explicitly is what
            // lets an MKV pick the Matroska extractor instead of failing to sniff — the exact case
            // that made .mkv files look broken while .mp4 worked.
            val resolvedMime = mediaMimeType(mimeType, fileName)
            val mediaItem = MediaItem.Builder()
                .setUri(file.toURI().toString())
                .apply { resolvedMime?.let { setMimeType(it) } }
                .build()
            setMediaItem(mediaItem)

            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // A dead frame with no message is what "the video doesn't work" actually looked
                    // like. Name the two real causes so the user knows which one they hit: a
                    // container this build cannot read, or — far more common for MKV — an audio or
                    // video codec (AC3, DTS, HEVC) this device has no decoder for.
                    errorText = when (error.errorCode) {
                        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                        PlaybackException.ERROR_CODE_DECODING_FAILED,
                        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                        ->
                            "This device has no decoder for this video's codec. MKV files often use " +
                                "audio (AC3, DTS) or video (HEVC) that Android cannot play. The file " +
                                "is safe in your vault — export it and play it on a computer."

                        else ->
                            "This video could not be played on this device. The file itself is " +
                                "unharmed and still in your vault."
                    }
                }
            })

            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    if (errorText != null) {
        Text(
            text = errorText!!,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
                .fillMaxWidth()
                .padding(TvSpacing.standard),
        )
        return
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
    )
}

/**
 * The container MIME type to hand ExoPlayer, from the stored type or the original file name.
 *
 * Android's picker often reports `video/x-matroska` for an .mkv, but sometimes an empty or
 * octet-stream type, so the extension is the reliable fallback. Only the container matters here —
 * ExoPlayer selects the extractor from it; the codecs inside are decoded separately.
 */
private fun mediaMimeType(mimeType: String?, fileName: String?): String? {
    val type = mimeType?.lowercase()
    if (type != null && type.startsWith("video/") && type != "video/octet-stream") return type
    return when (fileName?.substringAfterLast('.', "")?.lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "avi" -> "video/avi"
        "ts" -> "video/mp2t"
        else -> null
    }
}

/** Small helper so the viewer body can show a page counter above a PDF. */
@Composable
internal fun PdfPageCount(pageCount: Int) {
    Text(
        text = pluralStringResource(R.plurals.viewer_pdf_pages, pageCount, pageCount),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}
