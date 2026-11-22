@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.prince.govtcompressor.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prince.govtcompressor.core.FileUtils
import com.prince.govtcompressor.core.OutFormat
import com.prince.govtcompressor.core.PdfEngine
import com.prince.govtcompressor.core.PdfPageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PdfMode(val label: String) {
    COMPRESS("Compress PDF"),
    IMG2PDF("Image to PDF"),
    PDF2IMG("PDF to Image"),
    MERGE("Merge PDFs")
}

@Composable
fun PdfScreen(initialMode: PdfMode) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(initialMode) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var workingText by remember { mutableStateOf("Processing…") }
    var savedMsg by remember { mutableStateOf<String?>(null) }

    // Compress PDF state
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfName by remember { mutableStateOf("") }
    var pdfOrigBytes by remember { mutableStateOf(0L) }
    var pdfPages by remember { mutableStateOf(0) }
    var quality by remember { mutableStateOf(60f) }
    var compressedPdf by remember { mutableStateOf<ByteArray?>(null) }

    // Image to PDF state
    var imgUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var builtPdf by remember { mutableStateOf<ByteArray?>(null) }

    // PDF to image state
    var pdf2ImgUri by remember { mutableStateOf<Uri?>(null) }
    var pdf2ImgName by remember { mutableStateOf("") }
    var imgFormat by remember { mutableStateOf(OutFormat.JPEG) }
    var renderedImages by remember { mutableStateOf<List<ByteArray>>(emptyList()) }

    // Merge state
    var mergeUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var mergedPdf by remember { mutableStateOf<ByteArray?>(null) }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        scope.launch {
            working = true; error = null; compressedPdf = null; savedMsg = null
            try {
                val info = withContext(Dispatchers.IO) { PdfEngine.info(context, picked) }
                pdfUri = picked
                pdfName = FileUtils.displayName(context, picked)
                pdfOrigBytes = info.sizeBytes
                pdfPages = info.pages
            } catch (e: Exception) {
                error = e.message ?: "Could not open this PDF."
            } finally { working = false }
        }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        if (picked.isNotEmpty()) { imgUris = picked; builtPdf = null; savedMsg = null; error = null }
    }
    val pickPdf2Img = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        pdf2ImgUri = picked
        pdf2ImgName = FileUtils.displayName(context, picked)
        renderedImages = emptyList(); savedMsg = null; error = null
    }
    val pickMerge = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        if (picked.isNotEmpty()) { mergeUris = picked; mergedPdf = null; savedMsg = null; error = null }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("PDF Tools", "Compress, create, convert and merge PDFs — all on your phone.")
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (m in PdfMode.values()) {
                FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
            }
        }
        ErrorBox(error)

        when (mode) {
            PdfMode.COMPRESS -> {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    BigButton("Choose PDF", onClick = { pickPdf.launch(arrayOf("application/pdf")) })
                }
                if (pdfUri != null) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        StatRow("File", pdfName)
                        StatRow("Original", "${FileUtils.formatKb(pdfOrigBytes)} · $pdfPages pages")
                        Spacer(Modifier.height(8.dp))
                        Text("Image quality: ${quality.toInt()}% (lower = smaller file)", style = MaterialTheme.typography.bodyMedium)
                        Slider(value = quality, onValueChange = { quality = it }, valueRange = 10f..90f, steps = 7)
                        Text("Only the images inside the PDF are recompressed. Text-only PDFs shrink very little — the result card shows the real numbers.",
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        BigButton("Compress PDF", onClick = {
                            val u = pdfUri ?: return@BigButton
                            scope.launch {
                                working = true; workingText = "Compressing PDF…"; error = null; savedMsg = null
                                try {
                                    compressedPdf = withContext(Dispatchers.IO) {
                                        PdfEngine.compressPdf(context, u, quality.toInt())
                                    }
                                } catch (e: Exception) {
                                    error = e.message ?: "PDF compression failed."
                                } finally { working = false }
                            }
                        })
                    }
                    val out = compressedPdf
                    if (out != null) {
                        ResultCard(
                            title = if (out.size < pdfOrigBytes) "PDF compressed ✓" else "PDF processed — barely smaller",
                            origBytes = pdfOrigBytes,
                            newBytes = out.size.toLong(),
                            targetBytes = null,
                            extraLines = listOf("Pages" to "$pdfPages"),
                            savedMessage = savedMsg,
                            onSave = {
                                scope.launch {
                                    val name = FileUtils.baseName(pdfName) + "_compressed.pdf"
                                    val saved = withContext(Dispatchers.IO) {
                                        FileUtils.saveToDownloads(context, name, "application/pdf", out)
                                    }
                                    savedMsg = if (saved != null) "Saved to Downloads / Govt Photo Compressor / $name" else "Save failed."
                                }
                            },
                            onShare = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        FileUtils.shareBytes(context, FileUtils.baseName(pdfName) + "_compressed.pdf", "application/pdf", out)
                                    }
                                }
                            }
                        )
                    }
                }
            }
            PdfMode.IMG2PDF -> {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    BigButton("Choose Images", onClick = { pickImages.launch(arrayOf("image/*")) })
                }
                if (imgUris.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        StatRow("Images selected", "${imgUris.size}")
                        Spacer(Modifier.height(8.dp))
                        Text("Page size", style = MaterialTheme.typography.titleMedium)
                        FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (ps in PdfPageSize.values()) {
                                FilterChip(selected = pageSize == ps, onClick = { pageSize = ps },
                                    label = { Text(ps.label) })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        BigButton("Create PDF", onClick = {
                            scope.launch {
                                working = true; workingText = "Building PDF…"; error = null; savedMsg = null
                                try {
                                    builtPdf = withContext(Dispatchers.IO) {
                                        PdfEngine.imagesToPdf(context, imgUris, pageSize)
                                    }
                                } catch (e: Exception) {
                                    error = e.message ?: "Could not build the PDF."
                                } finally { working = false }
                            }
                        })
                    }
                    val out = builtPdf
                    if (out != null) {
                        ResultCard(
                            title = "PDF created — ${imgUris.size} pages",
                            origBytes = 0L,
                            newBytes = out.size.toLong(),
                            targetBytes = null,
                            savedMessage = savedMsg,
                            onSave = {
                                scope.launch {
                                    val name = "images_${System.currentTimeMillis()}.pdf"
                                    val saved = withContext(Dispatchers.IO) {
                                        FileUtils.saveToDownloads(context, name, "application/pdf", out)
                                    }
                                    savedMsg = if (saved != null) "Saved to Downloads / Govt Photo Compressor / $name" else "Save failed."
                                }
                            },
                            onShare = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        FileUtils.shareBytes(context, "images.pdf", "application/pdf", out)
                                    }
                                }
                            }
                        )
                    }
                }
            }
            PdfMode.PDF2IMG -> {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    BigButton("Choose PDF", onClick = { pickPdf2Img.launch(arrayOf("application/pdf")) })
                }
                if (pdf2ImgUri != null) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        StatRow("File", pdf2ImgName)
                        Text("Output format", style = MaterialTheme.typography.titleMedium)
                        FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (fmt in listOf(OutFormat.JPEG, OutFormat.PNG)) {
                                FilterChip(selected = imgFormat == fmt, onClick = { imgFormat = fmt },
                                    label = { Text(fmt.label) })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        BigButton("Convert Pages to Images", onClick = {
                            val u = pdf2ImgUri ?: return@BigButton
                            scope.launch {
                                working = true; workingText = "Rendering pages…"; error = null; savedMsg = null
                                try {
                                    renderedImages = withContext(Dispatchers.IO) {
                                        PdfEngine.pdfToImages(context, u, imgFormat, 150f)
                                    }
                                } catch (e: Exception) {
                                    error = e.message ?: "Could not render this PDF."
                                } finally { working = false }
                            }
                        })
                    }
                    if (renderedImages.isNotEmpty()) {
                        ResultCard(
                            title = "${renderedImages.size} pages rendered to ${imgFormat.label}",
                            origBytes = 0L,
                            newBytes = renderedImages.sumOf { it.size }.toLong(),
                            targetBytes = null,
                            savedMessage = savedMsg,
                            onSave = {
                                scope.launch {
                                    working = true; workingText = "Saving images…"
                                    try {
                                        val base = FileUtils.baseName(pdf2ImgName)
                                        var count = 0
                                        withContext(Dispatchers.IO) {
                                            renderedImages.forEachIndexed { i, bytes ->
                                                val saved = FileUtils.saveToDownloads(
                                                    context, "${base}_page${i + 1}.${imgFormat.ext}",
                                                    imgFormat.mime, bytes
                                                )
                                                if (saved != null) count++
                                            }
                                        }
                                        savedMsg = "$count of ${renderedImages.size} pages saved to Downloads / Govt Photo Compressor"
                                    } finally { working = false }
                                }
                            }
                        )
                    }
                }
            }
            PdfMode.MERGE -> {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    BigButton("Choose PDFs (in order)", onClick = { pickMerge.launch(arrayOf("application/pdf")) })
                    Text("Pick the PDFs in the order they should appear in the merged file.",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (mergeUris.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        StatRow("PDFs selected", "${mergeUris.size}")
                        mergeUris.forEachIndexed { i, u ->
                            StatRow("  ${i + 1}.", FileUtils.displayName(context, u))
                        }
                        Spacer(Modifier.height(12.dp))
                        BigButton("Merge PDFs", onClick = {
                            scope.launch {
                                working = true; workingText = "Merging…"; error = null; savedMsg = null
                                try {
                                    mergedPdf = withContext(Dispatchers.IO) {
                                        PdfEngine.mergePdfs(context, mergeUris)
                                    }
                                } catch (e: Exception) {
                                    error = e.message ?: "Merge failed."
                                } finally { working = false }
                            }
                        })
                    }
                    val out = mergedPdf
                    if (out != null) {
                        ResultCard(
                            title = "Merged ${mergeUris.size} PDFs ✓",
                            origBytes = 0L,
                            newBytes = out.size.toLong(),
                            targetBytes = null,
                            savedMessage = savedMsg,
                            onSave = {
                                scope.launch {
                                    val name = "merged_${System.currentTimeMillis()}.pdf"
                                    val saved = withContext(Dispatchers.IO) {
                                        FileUtils.saveToDownloads(context, name, "application/pdf", out)
                                    }
                                    savedMsg = if (saved != null) "Saved to Downloads / Govt Photo Compressor / $name" else "Save failed."
                                }
                            },
                            onShare = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        FileUtils.shareBytes(context, "merged.pdf", "application/pdf", out)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (working) WorkingOverlay(workingText)
}
