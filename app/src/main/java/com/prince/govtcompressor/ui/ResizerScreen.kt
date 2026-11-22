@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.prince.govtcompressor.ui

import android.graphics.Bitmap
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

@Composable
fun ResizerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var origBytes by remember { mutableStateOf(0L) }
    var widthText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var lockAspect by remember { mutableStateOf(true) }
    var outFormat by remember { mutableStateOf(OutFormat.JPEG) }
    var result by remember { mutableStateOf<ByteArray?>(null) }
    var resultDims by remember { mutableStateOf("") }
    var savedMsg by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    fun loadImage(picked: Uri) {
        scope.launch {
            working = true; error = null; result = null; savedMsg = null
            try {
                val decoded = withContext(Dispatchers.IO) { ImageEngine.decode(context, picked, 4096) }
                bitmap?.recycle()
                bitmap = decoded.bitmap
                uri = picked
                origBytes = decoded.origBytes
                widthText = decoded.bitmap.width.toString()
                heightText = decoded.bitmap.height.toString()
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
        ScreenTitle("Image Resizer", "Set an exact width and height in pixels. Common portal sizes are one tap away.")
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
            Image(bmp.asImageBitmap(), null, contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(200.dp))
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                StatRow("Original", "${bmp.width}×${bmp.height} px · ${FileUtils.formatKb(origBytes)}")
            }
            Spacer(Modifier.height(12.dp))
            Text("Quick sizes", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp))
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((label, w, h) in listOf(
                    Triple("200×200", 200, 200),
                    Triple("213×213 (PAN)", 213, 213),
                    Triple("300×300", 300, 300),
                    Triple("413×531 (Passport)", 413, 531),
                    Triple("800×800", 800, 800),
                    Triple("1024×1024", 1024, 1024)
                )) {
                    FilterChip(
                        selected = widthText == w.toString() && heightText == h.toString(),
                        onClick = { widthText = w.toString(); heightText = h.toString() },
                        label = { Text(label) }
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { widthText = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Width (px)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Height (px)") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lock aspect ratio", modifier = Modifier.weight(1f))
                Switch(checked = lockAspect, onCheckedChange = { lockAspect = it })
            }
            Text("Output format", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp))
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (fmt in OutFormat.values()) {
                    FilterChip(selected = outFormat == fmt, onClick = { outFormat = fmt },
                        label = { Text(fmt.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                BigButton("Resize", onClick = {
                    val w = widthText.toIntOrNull() ?: 0
                    val h = heightText.toIntOrNull() ?: 0
                    if (w <= 0 || h <= 0) { error = "Enter a valid width and height."; return@BigButton }
                    scope.launch {
                        working = true; error = null; savedMsg = null
                        try {
                            val resized = withContext(Dispatchers.IO) {
                                val target = if (lockAspect) {
                                    // Fit inside the box, keep aspect.
                                    val scale = minOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
                                    ImageEngine.scaleTo(bmp, scale)
                                } else {
                                    ImageEngine.resize(bmp, w, h)
                                }
                                val dims = "${target.width}×${target.height} px"
                                val bytes = ImageEngine.encode(target, outFormat, 92)
                                if (target != bmp) target.recycle()
                                bytes to dims
                            }
                            result = resized.first
                            resultDims = resized.second
                        } catch (e: Exception) {
                            error = e.message ?: "Resize failed."
                        } finally { working = false }
                    }
                })
            }
        }
        val res = result
        if (res != null) {
            ResultCard(
                title = "Resized to $resultDims",
                origBytes = origBytes,
                newBytes = res.size.toLong(),
                targetBytes = null,
                savedMessage = savedMsg,
                onSave = {
                    scope.launch {
                        val base = FileUtils.baseName(uri?.let { FileUtils.displayName(context, it) } ?: "image")
                        val name = "${base}_resized.${outFormat.ext}"
                        val saved = withContext(Dispatchers.IO) {
                            FileUtils.saveToDownloads(context, name, outFormat.mime, res)
                        }
                        savedMsg = if (saved != null) "Saved to Downloads / Govt Photo Compressor / $name" else "Save failed."
                    }
                },
                onShare = {
                    scope.launch {
                        val base = FileUtils.baseName(uri?.let { FileUtils.displayName(context, it) } ?: "image")
                        withContext(Dispatchers.IO) {
                            FileUtils.shareBytes(context, "${base}_resized.${outFormat.ext}", outFormat.mime, res)
                        }
                    }
                }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
    if (working) WorkingOverlay("Resizing…")
}
