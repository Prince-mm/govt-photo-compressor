package com.prince.govtcompressor.core

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Bulk compression worker (WorkManager). Processes a list of image Uris
 * sequentially on a background thread, writes each result to
 * Downloads/Govt Photo Compressor/, builds a ZIP of all results in the
 * cache dir as it goes, and reports progress (0-100).
 * Runs even if the user leaves the screen; the UI observes WorkInfo.
 */
class BulkWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriStrings = inputData.getStringArray(KEY_URIS) ?: return@withContext Result.failure()
        val targetKb = inputData.getInt(KEY_TARGET_KB, 50)
        val formatName = inputData.getString(KEY_FORMAT) ?: OutFormat.JPEG.name
        val format = runCatching { OutFormat.valueOf(formatName) }.getOrDefault(OutFormat.JPEG)
        val targetBytes = targetKb * 1024
        var done = 0
        var failed = 0
        val failedNames = mutableListOf<String>()
        val zipFile = File(applicationContext.cacheDir, "bulk_compressed_${System.currentTimeMillis()}.zip")
        val zipOut = ZipOutputStream(FileOutputStream(zipFile))
        try {
            for ((index, uriStr) in uriStrings.withIndex()) {
                if (isStopped) return@withContext Result.failure()
                try {
                    val uri = Uri.parse(uriStr)
                    val decoded = ImageEngine.decode(applicationContext, uri, 3000)
                    val result = ImageEngine.compressToTarget(decoded.bitmap, format, targetBytes)
                    val base = FileUtils.baseName(decoded.name)
                    val fileName = "${base}_compressed.${format.ext}"
                    FileUtils.saveToDownloads(applicationContext, fileName, format.mime, result.bytes)
                    zipOut.putNextEntry(ZipEntry(fileName))
                    zipOut.write(result.bytes)
                    zipOut.closeEntry()
                    decoded.bitmap.recycle()
                    done++
                } catch (e: Exception) {
                    failed++
                    failedNames.add(uriStr.substringAfterLast('/').take(30))
                }
                setProgress(workDataOf(KEY_PROGRESS to ((index + 1) * 100 / uriStrings.size)))
            }
        } finally {
            runCatching { zipOut.close() }
        }
        var zipName: String? = null
        if (done > 0) {
            zipName = "bulk_compressed_${System.currentTimeMillis()}.zip"
            FileUtils.saveToDownloads(
                applicationContext, zipName, "application/zip", zipFile.readBytes()
            )
            runCatching { zipFile.delete() }
        }
        val out = Data.Builder()
            .putInt(KEY_DONE, done)
            .putInt(KEY_FAILED, failed)
            .putString(KEY_FAILED_NAMES, failedNames.joinToString(", "))
            .putString(KEY_ZIP_NAME, zipName ?: "")
            .build()
        Result.success(out)
    }

    companion object {
        const val KEY_URIS = "uris"
        const val KEY_TARGET_KB = "target_kb"
        const val KEY_FORMAT = "format"
        const val KEY_PROGRESS = "progress"
        const val KEY_DONE = "done"
        const val KEY_FAILED = "failed"
        const val KEY_FAILED_NAMES = "failed_names"
        const val KEY_ZIP_NAME = "zip_name"
        const val WORK_TAG = "bulk_compress"
    }
}
