package com.kabutarbaazi.app.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.kabutarbaazi.domain.media.MediaConstraints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Pulls a poster frame so the reels feed has something to show before the first video frame. */
object VideoThumbnailer {

    data class VideoInfo(val width: Int, val height: Int, val durationMs: Int)

    suspend fun probe(context: Context, uri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        MediaMetadataRetriever().use { mmr ->
            mmr.setDataSource(context, uri)
            fun meta(key: Int) = mmr.extractMetadata(key)?.toIntOrNull() ?: 0
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            // Rotation metadata means the stored dimensions are not the displayed ones.
            val swapped = rotation == 90 || rotation == 270
            VideoInfo(
                width = if (swapped) h else w,
                height = if (swapped) w else h,
                durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION),
            )
        }
    }

    suspend fun thumbnail(context: Context, uri: Uri, outputName: String): File =
        withContext(Dispatchers.IO) {
            val bitmap = MediaMetadataRetriever().use { mmr ->
                mmr.setDataSource(context, uri)
                mmr.getScaledFrameAtTime(
                    0,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    MediaConstraints.THUMB_LONGEST_EDGE,
                    (MediaConstraints.THUMB_LONGEST_EDGE * 16) / 9,
                ) ?: error("no_frame")
            }
            val out = File(context.cacheDir, outputName)
            out.outputStream().use { stream ->
                @Suppress("DEPRECATION")
                val format =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        Bitmap.CompressFormat.WEBP
                    }
                bitmap.compress(format, 75, stream)
            }
            bitmap.recycle()
            out
        }
}
