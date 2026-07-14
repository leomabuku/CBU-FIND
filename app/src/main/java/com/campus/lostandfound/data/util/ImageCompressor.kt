package com.campus.lostandfound.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageCompressor {
    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 82
    private const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024

    fun compressToJpeg(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "The selected image could not be opened." }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a valid image." }

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_DIMENSION * 2) {
            sampleSize *= 2
        }

        val decoded = resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "The selected image could not be opened." }
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        } ?: error("The selected image could not be decoded.")

        val orientation = resolver.openInputStream(uri).use { input ->
            if (input == null) ExifInterface.ORIENTATION_NORMAL
            else runCatching {
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        }

        val oriented = applyOrientation(decoded, orientation)
        if (oriented !== decoded) decoded.recycle()
        val longest = max(oriented.width, oriented.height)
        val scaled = if (longest > MAX_DIMENSION) {
            val ratio = MAX_DIMENSION.toFloat() / longest
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * ratio).roundToInt(),
                (oriented.height * ratio).roundToInt(),
                true
            )
        } else oriented
        if (scaled !== oriented) oriented.recycle()

        return try {
            ByteArrayOutputStream().use { output ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "The selected image could not be compressed."
                }
                output.toByteArray().also { bytes ->
                    require(bytes.size <= MAX_UPLOAD_BYTES) {
                        "The compressed image is still larger than 5 MB. Choose a smaller photo."
                    }
                }
            }
        } finally {
            scaled.recycle()
        }
    }

    private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postScale(-1f, 1f)
                    postRotate(270f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postScale(-1f, 1f)
                    postRotate(90f)
                }
            }
        }
        return if (matrix.isIdentity) source else Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true
        )
    }
}
