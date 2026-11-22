package com.prince.govtcompressor.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

/** Output formats the app can write. HEIC and GIF are input-only: Android has no
 *  encoder for them, so they are converted to JPG/PNG/WEBP instead. */
enum class OutFormat(val label: String, val ext: String, val mime: String) {
    JPEG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WEBP", "webp", "image/webp"),
    BMP("BMP", "bmp", "image/bmp");

    val isLossy: Boolean get() = this == JPEG || this == WEBP
}

enum class InFormat { JPEG, PNG, WEBP, BMP, GIF, TIFF, HEIC, UNKNOWN }

data class DecodedImage(
    val bitmap: Bitmap,
    val format: InFormat,
    val origBytes: Long,
    val name: String
)

data class CompressResult(
    val bytes: ByteArray,
    val achievedBytes: Int,
    val targetBytes: Int,
    val metTarget: Boolean,
    val quality: Int,          // -1 for lossless formats
    val width: Int,
    val height: Int,
    val scale: Float
)

object ImageEngine {

    val TARGET_SIZES_KB = listOf(10, 15, 20, 25, 30, 40, 50, 75, 100, 150, 200, 300, 500, 1024)

    /** Sniff the format from magic bytes. */
    fun sniffFormat(input: InputStream): InFormat {
        val head = ByteArray(12)
        val n = input.read(head)
        if (n < 4) return InFormat.UNKNOWN
        return when {
            head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> InFormat.JPEG
            head[0] == 0x89.toByte() && head[1] == 0x50.toByte() -> InFormat.PNG
            head[0] == 0x52.toByte() && head[1] == 0x49.toByte() && n >= 12 &&
                head[8] == 0x57.toByte() && head[9] == 0x45.toByte() -> InFormat.WEBP
            head[0] == 0x42.toByte() && head[1] == 0x4D.toByte() -> InFormat.BMP
            head[0] == 0x47.toByte() && head[1] == 0x49.toByte() && head[2] == 0x46.toByte() -> InFormat.GIF
            (head[0] == 0x49.toByte() && head[1] == 0x49.toByte() && head[2] == 0x2A.toByte()) ||
                (head[0] == 0x4D.toByte() && head[1] == 0x4D.toByte() && head[2] == 0x00.toByte()) -> InFormat.TIFF
            n >= 12 && head[4] == 0x66.toByte() && head[5] == 0x74.toByte() &&
                head[6] == 0x79.toByte() && head[7] == 0x70.toByte() &&
                (head[8] == 0x68.toByte() && head[9] == 0x65.toByte()) -> InFormat.HEIC
            else -> InFormat.UNKNOWN
        }
    }

    /** Decode an image Uri, downsampling so the longest side is at most [maxDim].
     *  Handles EXIF rotation, TIFF (custom decoder) and HEIC (system decoder, API 28+). */
    fun decode(context: Context, uri: Uri, maxDim: Int = 4096): DecodedImage {
        val name = FileUtils.displayName(context, uri)
        val origBytes = FileUtils.sizeOf(context, uri)
        val format = context.contentResolver.openInputStream(uri)?.use { sniffFormat(it) }
            ?: InFormat.UNKNOWN
        var bitmap: Bitmap? = null
        if (format == InFormat.TIFF) {
            bitmap = context.contentResolver.openInputStream(uri)?.use { TiffDecoder.decode(it) }
        }
        if (bitmap == null) {
            // Two-pass decode with sampling to avoid OOM on large photos.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IllegalArgumentException("Could not read this image. It may be an unsupported format (e.g. HEIC on older devices or a corrupt file).")
            }
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: throw IllegalArgumentException("Could not decode this image.")
        }
        bitmap = applyExifRotation(context, uri, bitmap)
        return DecodedImage(bitmap, format, origBytes, name)
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                ?: return bitmap
            val rot = when (exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rot == 0f) return bitmap
            val m = Matrix().apply { postRotate(rot) }
            val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            if (out != bitmap) bitmap.recycle()
            out
        } catch (_: Exception) {
            bitmap
        }
    }

    /** Flatten transparency onto white (needed for JPEG output). */
    fun flattenOnWhite(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(src, 0f, 0f, null)
        return out
    }

    fun scaleTo(src: Bitmap, scale: Float): Bitmap {
        if (scale >= 1f) return src
        val w = maxOf(1, (src.width * scale).roundToInt())
        val h = maxOf(1, (src.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    /** Encode one bitmap to bytes in [format] at [quality] (0-100, lossy only). */
    fun encode(src: Bitmap, format: OutFormat, quality: Int): ByteArray {
        return when (format) {
            OutFormat.JPEG -> {
                val flat = flattenOnWhite(src)
                val out = ByteArrayOutputStream()
                flat.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
                if (flat != src) flat.recycle()
                out.toByteArray()
            }
            OutFormat.PNG -> {
                val out = ByteArrayOutputStream()
                src.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            OutFormat.WEBP -> {
                val out = ByteArrayOutputStream()
                @Suppress("DEPRECATION")
                src.compress(Bitmap.CompressFormat.WEBP, quality.coerceIn(1, 100), out)
                out.toByteArray()
            }
            OutFormat.BMP -> BmpWriter.encode24(src)
        }
    }

    /**
     * Compress [src] so the result is at most [targetBytes].
     * Lossy formats: binary-search quality, then shrink dimensions in steps.
     * Lossless (PNG/BMP): shrink dimensions in steps.
     * Never reports success unless the actual bytes fit the target.
     */
    fun compressToTarget(
        src: Bitmap,
        format: OutFormat,
        targetBytes: Int
    ): CompressResult {
        require(targetBytes > 0)
        var working = src
        var scale = 1f
        var best: ByteArray? = null
        var bestQuality = -1
        for (step in 0 until 9) { // up to 9 downscale steps: 1.0, 0.85, 0.72, ...
            if (format.isLossy) {
                // Binary search the highest quality that fits.
                var lo = 5
                var hi = 95
                var found: ByteArray? = null
                var foundQ = -1
                // Quick check: does even quality 95 fit?
                val top = encode(working, format, 95)
                if (top.size <= targetBytes) {
                    found = top; foundQ = 95
                } else {
                    val low = encode(working, format, 5)
                    if (low.size <= targetBytes) {
                        while (lo <= hi) {
                            val mid = (lo + hi) / 2
                            val bytes = encode(working, format, mid)
                            if (bytes.size <= targetBytes) {
                                found = bytes; foundQ = mid; lo = mid + 1
                            } else hi = mid - 1
                        }
                    } else {
                        best = low // keep smallest as best-effort fallback
                        bestQuality = 5
                    }
                }
                if (found != null) {
                    return CompressResult(found, found.size, targetBytes, true, foundQ,
                        working.width, working.height, scale)
                }
            } else {
                val bytes = encode(working, format, 100)
                if (bytes.size <= targetBytes) {
                    return CompressResult(bytes, bytes.size, targetBytes, true, -1,
                        working.width, working.height, scale)
                }
                if (best == null || bytes.size < best.size) {
                    best = bytes; bestQuality = -1
                }
            }
            // Shrink and try again.
            val nextScale = scale * 0.85f
            val next = scaleTo(working, nextScale / scale)
            if (next == working) break
            if (working != src) working.recycle()
            working = next
            scale = nextScale
        }
        val bestBytes = best
        val fallback = bestBytes ?: encode(working, format, 5)
        return CompressResult(fallback, fallback.size, targetBytes, false, bestQuality,
            working.width, working.height, scale)
    }

    /** Simple resize to exact dimensions (keeps aspect if one side is 0). */
    fun resize(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val w = if (targetW > 0) targetW else (src.width * targetH / src.height)
        val h = if (targetH > 0) targetH else (src.height * targetW / src.width)
        return Bitmap.createScaledBitmap(src, maxOf(1, w), maxOf(1, h), true)
    }

    /** Crop to a rectangle (clamped to the bitmap). */
    fun crop(src: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap {
        val l = left.coerceIn(0, src.width - 1)
        val t = top.coerceIn(0, src.height - 1)
        val w = width.coerceIn(1, src.width - l)
        val h = height.coerceIn(1, src.height - t)
        return Bitmap.createBitmap(src, l, t, w, h)
    }

    /** Center-crop to an aspect ratio (w/h). */
    fun cropToAspect(src: Bitmap, ratio: Float): Bitmap {
        val cur = src.width.toFloat() / src.height
        return if (cur > ratio) {
            val w = (src.height * ratio).roundToInt()
            crop(src, (src.width - w) / 2, 0, w, src.height)
        } else {
            val h = (src.width / ratio).roundToInt()
            crop(src, 0, (src.height - h) / 2, src.width, h)
        }
    }
}
