@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.prince.govtcompressor.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import com.prince.govtcompressor.core.FileUtils
import com.prince.govtcompressor.core.ImageEngine
import com.prince.govtcompressor.core.OutFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConverterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var origBytes by remember { mutableStateOf(0L) }
    var origFormat by remember { mutableStateOf("") }
    var outFormat by remember { mutableStateOf(OutFormat.PNG) }
    var result by remember { mutableStateOf<ByteArray?>(null) }
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
                origFormat = decoded.format.name
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Image Converter", "Convert between JPG, PNG, WEBP and BMP. Transparency is kept for PNG and WEBP; JPG flattens it onto white.")
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
        val bmp = bitmap
        if (bmp != null) {
            Spacer(Modifier.height(12.dp))
            Image(bmp.asImageBitmap(), null, contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(220.dp))
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                StatRow("Original", "${FileUtils.formatKb(origBytes)} · ${bmp.width}×${bmp.height} px · $origFormat")
            }
            Spacer(Modifier.height(12.dp))
            Text("Convert to", style = MaterialTheme.typography.titleMedium,
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
            if (outFormat == OutFormat.JPEG && bmp.hasAlpha()) {
                Text("Note: this image has transparency — converting to JPG will put it on a white background.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                BigButton("Convert", onClick = {
                    scope.launch {
                        working = true; error = null; savedMsg = null
                        try {
                            result = withContext(Dispatchers.IO) {
                                ImageEngine.encode(bmp, outFormat, 92)
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Conversion failed."
                        } finally { working = false }
                    }
                })
            }
        }
        val res = result
        if (res != null) {
            ResultCard(
                title = "Converted to ${outFormat.label}",
                origBytes = origBytes,
                newBytes = res.size.toLong(),
                targetBytes = null,
                extraLines = listOf("Format" to outFormat.label),
                savedMessage = savedMsg,
                onSave = {
                    scope.launch {
                        val base = FileUtils.baseName(uri?.let { FileUtils.displayName(context, it) } ?: "image")
                        val name = "${base}_converted.${outFormat.ext}"
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
                            FileUtils.shareBytes(context, "${base}_converted.${outFormat.ext}", outFormat.mime, res)
                        }
                    }
                }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
    if (working) WorkingOverlay("Converting…")
}
