package com.truevault.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.net.Uri
import androidx.core.graphics.scale
import com.truevault.core.common.log.SecureLog
import com.truevault.core.model.MimeCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Thumbnails"

/**
 * Builds the small preview image stored alongside a vault item.
 *
 * Thumbnails exist so the vault grid never has to decrypt a full-size photo or seek into a video to
 * draw a cell. They are generated once, encrypted with the item's own file key, and stored
 * separately — so a grid of 500 items reads 500 small blobs instead of 500 originals.
 *
 * Images are decoded with `inSampleSize`, so a 50-megapixel photo is never fully decoded into
 * memory just to produce a 320px preview.
 */
@Singleton
class ThumbnailFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun createThumbnail(uri: Uri, category: MimeCategory): ByteArray? = when (category) {
        MimeCategory.PHOTO -> fromImage(uri)
        MimeCategory.VIDEO -> fromVideo(uri)
        else -> null
    }

    private fun fromImage(uri: Uri): ByteArray? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            bitmap?.let(::compress)
        }
    } catch (e: Exception) {
        // A thumbnail is a convenience. Failing to make one must never fail an import.
        SecureLog.w(TAG, "Could not build an image thumbnail (${e.javaClass.simpleName})")
        null
    }

    private fun fromVideo(uri: Uri): ByteArray? = try {
        // MediaMetadataRetriever only became AutoCloseable in API 29, and minSdk here is 26.
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            // Scaled frame extraction arrived in API 27. On API 26 the full frame is decoded and
            // then downscaled, which costs more memory for one frame but keeps the feature working.
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    TARGET_SIZE,
                    TARGET_SIZE,
                )
            } else {
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { full ->
                        val scaled = full.scale(TARGET_SIZE, TARGET_SIZE)
                        if (scaled !== full) full.recycle()
                        scaled
                    }
            }
            frame?.let(::compress)
        } finally {
            retriever.release()
        }
    } catch (e: Exception) {
        SecureLog.w(TAG, "Could not build a video thumbnail (${e.javaClass.simpleName})")
        null
    }

    private fun compress(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= TARGET_SIZE && h / 2 >= TARGET_SIZE) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val TARGET_SIZE = 320
        const val JPEG_QUALITY = 78
    }
}
