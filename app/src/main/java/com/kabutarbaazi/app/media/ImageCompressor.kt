package com.kabutarbaazi.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.kabutarbaazi.domain.media.MediaConstraints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downscales and re-encodes to WebP. No library: BitmapFactory plus ExifInterface is all this
 * needs, and an image-loading dependency for one operation would be dead weight.
 *
 * Re-encoding strips EXIF, GPS included. That is not a side effect, it is a privacy feature.
 * Outdoor pigeon photos routinely carry coordinates, and publishing them would broadcast
 * sellers' home addresses. The privacy policy says so explicitly.
 */
object ImageCompressor {

    suspend fun compress(
        context: Context,
        source: Uri,
        longestEdge: Int = MediaConstraints.IMAGE_LONGEST_EDGE,
        quality: Int = 80,
        outputName: String,
    ): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // Read the bounds first so a 50 MP photo is never fully decoded into memory: on the
        // cheap phones this app targets that is an immediate OutOfMemoryError.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "unreadable_image" }

        val sample = sampleSize(bounds.outWidth, bounds.outHeight, longestEdge)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: error("decode_failed")

        val rotation = resolver.openInputStream(source)?.use { readRotation(it) } ?: 0f
        val oriented = if (rotation == 0f) decoded else {
            Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(rotation) }, true,
            ).also { if (it !== decoded) decoded.recycle() }
        }

        val scaled = scaleToLongestEdge(oriented, longestEdge)

        val out = File(context.cacheDir, outputName)
        out.outputStream().use { stream ->
            @Suppress("DEPRECATION")
            val format =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
            scaled.compress(format, quality, stream)
        }
        if (scaled !== oriented) scaled.recycle()
        oriented.recycle()
        out
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (maxOf(w, h) / 2 >= target) {
            w /= 2; h /= 2; sample *= 2
        }
        return sample
    }

    private fun scaleToLongestEdge(bitmap: Bitmap, target: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= target) return bitmap
        val ratio = target.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun readRotation(stream: java.io.InputStream): Float =
        when (ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
}
