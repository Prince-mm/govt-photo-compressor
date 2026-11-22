package com.prince.govtcompressor.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

object FileUtils {

    const val FOLDER = "Govt Photo Compressor"

    fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx) ?: "image"
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "image"
    }

    fun sizeOf(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) return c.getLong(idx)
                }
            }
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun baseName(name: String): String = name.substringBeforeLast('.', name).ifBlank { "image" }

    fun formatKb(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    }

    fun percentSaved(orig: Long, now: Long): Int =
        if (orig <= 0) 0 else ((1.0 - now.toDouble() / orig) * 100).roundToInt()

    /** Save bytes into Downloads/<FOLDER>/[fileName]. Returns the content Uri, or null. */
    fun saveToDownloads(context: Context, fileName: String, mime: String, bytes: ByteArray): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + File.separator + FOLDER)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    FOLDER
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(bytes) }
                Uri.fromFile(file)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Share a byte array as a file via FileProvider (WhatsApp, Gmail, etc.). */
    fun shareBytes(context: Context, fileName: String, mime: String, bytes: ByteArray) {
        try {
            val cacheDir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share via"))
        } catch (_: Exception) {
            // Caller shows an error toast.
        }
    }

    /** Zip a list of (fileName, bytes) into one byte array. */
    fun zipFiles(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
