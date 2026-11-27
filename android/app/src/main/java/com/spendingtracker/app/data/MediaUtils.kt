package com.spendingtracker.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.math.max

object MediaUtils {

    private const val MAX_IMAGE_DIMENSION = 1600
    private const val JPEG_QUALITY = 85

    /**
     * Converts a list of content URIs into multipart `files[]` parts.
     * Images are downscaled to max ~1600px JPEG per spec to minimize bandwidth and tokens.
     * PDFs and CSVs are streamed as-is.
     */
    fun urisToMultipartParts(context: Context, uris: List<Uri>): List<MultipartBody.Part> {
        val resolver = context.contentResolver
        return uris.mapIndexedNotNull { index, uri ->
            val mimeType = resolver.getType(uri) ?: guessMimeFromName(queryFileName(context, uri))
            val fileName = queryFileName(context, uri) ?: "upload_$index"

            if (mimeType.startsWith("image/")) {
                val jpegBytes = downscaleImageToJpeg(context, uri) ?: return@mapIndexedNotNull null
                val body = jpegBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val cleanName = fileName.substringBeforeLast('.') + ".jpg"
                MultipartBody.Part.createFormData("files[]", cleanName, body)
            } else {
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@mapIndexedNotNull null
                val body = rawBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                MultipartBody.Part.createFormData("files[]", fileName, body)
            }
        }
    }

    fun isPdfUri(context: Context, uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri)
        if (mime == "application/pdf") return true
        return queryFileName(context, uri)?.endsWith(".pdf", ignoreCase = true) == true
    }

    private fun downscaleImageToJpeg(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver

        // 1. Read bounds only
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        // 2. Compute power-of-2 sample size
        val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
        var sampleSize = 1
        while (maxDim / (sampleSize * 2) >= MAX_IMAGE_DIMENSION) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val sampled = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        // 3. Exact scale down if still larger than 1600px
        val currentMax = max(sampled.width, sampled.height)
        val finalBitmap = if (currentMax > MAX_IMAGE_DIMENSION) {
            val scale = MAX_IMAGE_DIMENSION.toFloat() / currentMax.toFloat()
            val targetW = (sampled.width * scale).toInt().coerceAtLeast(1)
            val targetH = (sampled.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(sampled, targetW, targetH, true).also {
                if (it !== sampled) sampled.recycle()
            }
        } else {
            sampled
        }

        val out = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        finalBitmap.recycle()
        return out.toByteArray()
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment
    }

    private fun guessMimeFromName(name: String?): String {
        val lower = name?.lowercase().orEmpty()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".csv") -> "text/csv"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            else -> "image/jpeg"
        }
    }
}
