package com.prince.govtcompressor.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.min

data class PdfInfo(val pages: Int, val sizeBytes: Long)

enum class PdfPageSize(val label: String, val rect: PDRectangle?) {
    A4("A4", PDRectangle.A4),
    LETTER("Letter", PDRectangle.LETTER),
    PHOTO_4X6("4×6 inch", PDRectangle(288f, 432f)),
    FIT_IMAGE("Fit image", null)
}

object PdfEngine {

    fun info(context: Context, uri: Uri): PdfInfo {
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                return PdfInfo(doc.numberOfPages, FileUtils.sizeOf(context, uri))
            }
        }
        throw IllegalArgumentException("Could not open this PDF.")
    }

    /**
     * Compress a PDF by re-encoding its embedded images at [quality] (1-100)
     * and downscaling images wider than [maxImageWidth] px.
     * Text-only PDFs have few images, so they shrink little — reported honestly.
     * Returns the new PDF bytes.
     */
    fun compressPdf(
        context: Context,
        uri: Uri,
        quality: Int,
        maxImageWidth: Int = 1500
    ): ByteArray {
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                var imagesRecompressed = 0
                for (page in doc.pages) {
                    val resources = page.resources ?: continue
                    for (name in resources.xObjectNames) {
                        val xobj = resources.getXObject(name) ?: continue
                        if (xobj !is PDImageXObject) continue
                        try {
                            val img = xobj.image ?: continue
                            if (img.width < 50 || img.height < 50) continue
                            var bmp = img
                            if (bmp.width > maxImageWidth) {
                                val scale = maxImageWidth.toFloat() / bmp.width
                                bmp = ImageEngine.scaleTo(bmp, scale)
                            }
                            val jpeg = ImageEngine.encode(bmp, OutFormat.JPEG, quality)
                            // Only replace when the new encoding is actually smaller.
                            val oldBytes = estimateImageBytes(xobj)
                            if (oldBytes > 0 && jpeg.size >= oldBytes) continue
                            val replacement = PDImageXObject.createFromByteArray(
                                doc, jpeg, "img${imagesRecompressed}"
                            )
                            resources.put(name, replacement)
                            imagesRecompressed++
                            if (bmp != img) bmp.recycle()
                        } catch (_: Exception) {
                            // Leave that image untouched.
                        }
                    }
                }
                val out = ByteArrayOutputStream()
                doc.save(out)
                return out.toByteArray()
            }
        }
        throw IllegalArgumentException("Could not open this PDF.")
    }

    private fun estimateImageBytes(xobj: PDImageXObject): Long {
        return try {
            val stream = xobj.cosObject
            val len = stream.getInt(COSName.LENGTH)
            if (len > 0) len.toLong() else -1L
        } catch (_: Exception) {
            -1L
        }
    }

    /** Build a PDF from images. Each image gets its own page. */
    fun imagesToPdf(
        context: Context,
        uris: List<Uri>,
        pageSize: PdfPageSize
    ): ByteArray {
        val doc = PDDocument()
        try {
            for (uri in uris) {
                val decoded = ImageEngine.decode(context, uri, 3000)
                val bmp = decoded.bitmap
                val rect = pageSize.rect ?: PDRectangle(
                    min(bmp.width, 3000).toFloat(),
                    min(bmp.height, 3000).toFloat()
                )
                val page = PDPage(rect)
                doc.addPage(page)
                val jpeg = ImageEngine.encode(bmp, OutFormat.JPEG, 85)
                val img = PDImageXObject.createFromByteArray(doc, jpeg, decoded.name)
                val pw = rect.width
                val ph = rect.height
                val scale = min(pw / bmp.width, ph / bmp.height) * 0.95f
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(img, (pw - dw) / 2, (ph - dh) / 2, dw, dh)
                }
                if (bmp != decoded.bitmap) bmp.recycle()
                decoded.bitmap.recycle()
            }
            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        } finally {
            doc.close()
        }
    }

    /** Render every page of a PDF to images. */
    fun pdfToImages(
        context: Context,
        uri: Uri,
        format: OutFormat,
        dpi: Float = 150f
    ): List<ByteArray> {
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                val renderer = PDFRenderer(doc)
                val results = mutableListOf<ByteArray>()
                for (i in 0 until doc.numberOfPages) {
                    val bmp = renderer.renderImageWithDPI(i, dpi)
                    results.add(ImageEngine.encode(bmp, format, 90))
                    bmp.recycle()
                }
                return results
            }
        }
        throw IllegalArgumentException("Could not open this PDF.")
    }

    /** Merge several PDFs into one, in the given order. */
    fun mergePdfs(context: Context, uris: List<Uri>): ByteArray {
        val merger = PDFMergerUtility()
        val streams = mutableListOf<ByteArrayInputStream>()
        try {
            for (uri in uris) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("Could not read one of the PDFs.")
                streams.add(ByteArrayInputStream(bytes))
                merger.addSource(ByteArrayInputStream(bytes))
            }
            val out = ByteArrayOutputStream()
            merger.destinationStream = out
            merger.mergeDocuments(null)
            return out.toByteArray()
        } finally {
            streams.forEach { runCatching { it.close() } }
        }
    }
}
