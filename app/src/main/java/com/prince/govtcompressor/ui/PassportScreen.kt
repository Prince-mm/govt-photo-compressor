@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.prince.govtcompressor.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prince.govtcompressor.core.FileUtils
import com.prince.govtcompressor.core.ImageEngine
import com.prince.govtcompressor.core.OutFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SheetKind(val label: String, val w: Int, val h: Int, val cols: Int, val rows: Int) {
    PHOTO_4X6("4×6 inch — 6 photos", 1200, 1800, 2, 3),
    A4("A4 — 20 photos", 2480, 3508, 4, 5)
}

/** Lay passport photos out on a white printable sheet with thin cut guides. */
fun buildSheet(photo: Bitmap, kind: SheetKind): Bitmap {
    val sheet = Bitmap.createBitmap(kind.w, kind.h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(sheet)
    canvas.drawColor(Color.WHITE)
    val paint = Paint().apply { color = Color.LTGRAY; strokeWidth = 2f }
    val margin = 60
    val gap = 30
    val cellW = (kind.w - margin * 2 - gap * (kind.cols - 1)) / kind.cols
    val cellH = (kind.h - margin * 2 - gap * (kind.rows - 1)) / kind.rows
    val scale = minOf(cellW / photo.width, cellH / photo.height)
    val dw = (photo.width * scale).toInt()
    val dh = (photo.height * scale).toInt()
    for (r in 0 until kind.rows) {
        for (c in 0 until kind.cols) {
            val x = margin + c * (cellW + gap) + (cellW - dw) / 2
            val y = margin + r * (cellH + gap) + (cellH - dh) / 2
            canvas.drawBitmap(photo, null,
                android.graphics.Rect(x, y, x + dw, y + dh), null)
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + dw).toFloat(), (y + dh).toFloat(), paint)
        }
    }
    return sheet
}

@Composable
fun PassportScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var origBytes by remember { mutableStateOf(0L) }
    var cropRect by remember { mutableStateOf(CropRect(0.1f, 0.05f, 0.9f, 0.95f)) }
    var targetKb by remember { mutableStateOf(100) }
    var customKb by remember { mutableStateOf("") }
    var useCustom by remember { mutableStateOf(false) }
    var sheetKind by remember { mutableStateOf(SheetKind.PHOTO_4X6) }
    var result by remember { mutableStateOf<com.prince.govtcompressor.core.CompressResult?>(null) }
    var sheetBytes by remember { mutableStateOf<ByteArray?>(null) }
    var savedMsg by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    fun loadImage(picked: Uri) {
        scope.launch {
            working = true; error = null; result = null; sheetBytes = null; savedMsg = null
            try {
                val decoded = withContext(Dispatchers.IO) { ImageEngine.decode(context, picked, 3000) }
                bitmap?.recycle()
                bitmap = decoded.bitmap
                uri = picked
                origBytes = decoded.origBytes
                cropRect = CropRect(0.1f, 0.05f, 0.9f, 0.95f)
            } catch (e: Exception) {
                error = e.message ?: "Could not open this image."
            } finally { working = false }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked -> if (picked != null) loadImage(picked) }
    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked -> if (picked != null) loadImage(picked) }

    val bmp = bitmap
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Passport Photo Maker", "Crop to 35×45 mm (413×531 px at 300 DPI), white background, compress — then print several on one sheet.")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(
                onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.weight(1f)
            ) { Text("Gallery") }
            androidx.compose.material3.OutlinedButton(
                onClick = { filesLauncher.launch(arrayOf("image/*")) },
                modifier = Modifier.weight(1f)
            ) { Text("Files") }
        }
        ErrorBox(error)
        if (bmp != null) {
            Spacer(Modifier.height(12.dp))
            Text("Crop the face area — drag the box, then make the photo.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            CropView(
                bitmap = bmp,
                rect = cropRect,
                onRectChange = { cropRect = it },
                modifier = Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("Target size", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (kb in listOf(20, 50, 100, 200)) {
                    FilterChip(selected = !useCustom && targetKb == kb,
                        onClick = { useCustom = false; targetKb = kb }, label = { Text("$kb KB") })
                }
                FilterChip(selected = useCustom, onClick = { useCustom = true }, label = { Text("Custom") })
            }
            if (useCustom) {
                OutlinedTextField(
                    value = customKb,
                    onValueChange = { customKb = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Custom size in KB") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                BigButton("Make Passport Photo", onClick = {
                    val kb = if (useCustom) customKb.toIntOrNull() ?: 0 else targetKb
                    if (kb <= 0) { error = "Enter a target size in KB first."; return@BigButton }
                    scope.launch {
                        working = true; error = null; savedMsg = null; sheetBytes = null
                        try {
                            val r = withContext(Dispatchers.IO) {
                                val p = cropRect.toPixels(bmp.width, bmp.height)
                                var work = ImageEngine.crop(bmp, p[0], p[1], p[2], p[3])
                                work = ImageEngine.cropToAspect(work, 35f / 45f)
                                work = ImageEngine.resize(work, 413, 531)
                                val flat = ImageEngine.flattenOnWhite(work)
                                if (flat != work) work.recycle()
                                work = flat
                                val res = ImageEngine.compressToTarget(work, OutFormat.JPEG, kb * 1024)
                                if (work != bmp) work.recycle()
                                res
                            }
                            result = r
                        } catch (e: Exception) {
                            error = e.message ?: "Failed to make the photo."
                        } finally { working = false }
                    }
                })
            }
        }
        val res = result
        if (res != null) {
            val kb = if (useCustom) customKb.toIntOrNull() ?: targetKb else targetKb
            // Preview the finished passport photo.
            val previewBmp = remember(res) {
                android.graphics.BitmapFactory.decodeByteArray(res.bytes, 0, res.bytes.size)
            }
            if (previewBmp != null) {
                Spacer(Modifier.height(12.dp))
                Image(previewBmp.asImageBitmap(), null, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(240.dp))
            }
            ResultCard(
                title = if (res.metTarget) "Passport photo ready ✓" else "Photo ready (target not reached)",
                origBytes = origBytes,
                newBytes = res.achievedBytes.toLong(),
                targetBytes = (kb * 1024).toLong(),
                extraLines = listOf("Dimensions" to "413×531 px (35×45 mm @ 300 DPI)"),
                savedMessage = savedMsg,
                onSave = {
                    scope.launch {
                        val name = "passport_photo_${kb}kb.jpg"
                        val saved = withContext(Dispatchers.IO) {
                            FileUtils.saveToDownloads(context, name, "image/jpeg", res.bytes)
                        }
                        savedMsg = if (saved != null) "Saved to Downloads / Govt Photo Compressor / $name" else "Save failed."
                    }
                },
                onShare = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            FileUtils.shareBytes(context, "passport_photo_${kb}kb.jpg", "image/jpeg", res.bytes)
                        }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Text("Printable sheet", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp))
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (kind in SheetKind.values()) {
                    FilterChip(selected = sheetKind == kind, onClick = { sheetKind = kind },
                        label = { Text(kind.label) })
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                BigButton("Make Print Sheet", onClick = {
                    scope.launch {
                        working = true; error = null
                        try {
                            val bytes = withContext(Dispatchers.IO) {
                                val photo = android.graphics.BitmapFactory.decodeByteArray(res.bytes, 0, res.bytes.size)
                                    ?: throw IllegalStateException("Could not read the finished photo.")
                                val sheet = buildSheet(photo, sheetKind)
                                val out = ImageEngine.encode(sheet, OutFormat.JPEG, 92)
                                photo.recycle(); sheet.recycle()
                                out
                            }
                            sheetBytes = bytes
                            savedMsg = null
                        } catch (e: Exception) {
                            error = e.message ?: "Sheet failed."
                        } finally { working = false }
                    }
                })
            }
            val sheet = sheetBytes
            if (sheet != null) {
                ResultCard(
                    title = "Print sheet ready — ${sheetKind.cols * sheetKind.rows} photos",
                    origBytes = res.achievedBytes.toLong(),
                    newBytes = sheet.size.toLong(),
                    targetBytes = null,
                    savedMessage = savedMsg,
                    onSave = {
                        scope.launch {
                            val name = "passport_sheet_${sheetKind.name.lowercase()}.jpg"
                            val saved = withContext(Dispatchers.IO) {
                                FileUtils.saveToDownloads(context, name, "image/jpeg", sheet)
                            }
                            savedMsg = if (saved != null) "Saved to Downloads / Govt Photo Compressor / $name" else "Save failed."
                        }
                    },
                    onShare = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                FileUtils.shareBytes(context, "passport_sheet_${sheetKind.name.lowercase()}.jpg", "image/jpeg", sheet)
                            }
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (working) WorkingOverlay("Processing…")
}
