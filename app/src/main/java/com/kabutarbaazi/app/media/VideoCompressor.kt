package com.kabutarbaazi.app.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.kabutarbaazi.domain.media.TranscodeTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Re-encodes a picked video to 720p H.264 before upload.
 *
 * This is what makes the whole media budget work: an unmodified phone recording is routinely
 * 40-80 MB for 30 seconds, and uploading that would blow the storage tier in a week and stall
 * on the connections these users actually have.
 */
@UnstableApi
object VideoCompressor {

    data class Result(val file: File, val width: Int, val height: Int, val durationMs: Int)

    suspend fun compress(
        context: Context,
        source: Uri,
        sourceWidth: Int,
        sourceHeight: Int,
        outputName: String,
        onProgress: (Float) -> Unit = {},
    ): Result {
        val target = TranscodeTarget.forSource(sourceWidth, sourceHeight)
        val output = File(context.cacheDir, outputName)
        if (output.exists()) output.delete()

        val result = withContext(Dispatchers.Main) {
            // Transformer must be built on a thread that has a Looper. The codecs themselves
            // run on their own threads, so this does not block the UI.
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            VideoEncoderSettings.Builder().setBitrate(target.videoBitrate).build(),
                        )
                        .build(),
                )
                .build()

            val edited = EditedMediaItem.Builder(MediaItem.fromUri(source))
                .setEffects(
                    Effects(
                        emptyList(),
                        // Exact dimensions rather than a short-side helper: TranscodeTarget has
                        // already rounded both sides to even numbers, which H.264 4:2:0 requires
                        // and several hardware encoders fail on rather than adjusting.
                        listOf(
                            Presentation.createForWidthAndHeight(
                                target.width,
                                target.height,
                                Presentation.LAYOUT_SCALE_TO_FIT,
                            ),
                        ),
                    ),
                )
                .build()

            val composition = Composition.Builder(
                androidx.media3.transformer.EditedMediaItemSequence.Builder(edited).build(),
            )
                // HDR capture on recent Samsung and Pixel devices fails the export outright
                // without an explicit tone-map, and those are exactly the phones enthusiasts use.
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()

            awaitExport(transformer, composition, output.absolutePath, onProgress)
        }

        // Read the dimensions back from the export rather than assuming the target: portrait
        // video carries rotation metadata, and guessing here makes every thumbnail sideways.
        val width = result.width.takeIf { it > 0 } ?: target.width
        val height = result.height.takeIf { it > 0 } ?: target.height

        return Result(
            file = output,
            width = width,
            height = height,
            durationMs = result.durationMs.toInt().coerceAtLeast(0),
        )
    }

    private suspend fun awaitExport(
        transformer: Transformer,
        composition: Composition,
        outputPath: String,
        onProgress: (Float) -> Unit,
    ): ExportResult = coroutineScope {
        // Media3 reports progress by polling, not by callback.
        val poller = launch(Dispatchers.Main) {
            val holder = ProgressHolder()
            while (isActive) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                }
                delay(200)
            }
        }
        try {
            suspendCancellableCoroutine { cont ->
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        if (cont.isActive) cont.resume(result)
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException,
                    ) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                })
                cont.invokeOnCancellation { runCatching { transformer.cancel() } }
                transformer.start(composition, outputPath)
            }
        } finally {
            poller.cancel()
            onProgress(1f)
        }
    }
}
