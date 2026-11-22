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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.FlowRow
import com.prince.govtcompressor.core.FileUtils
import com.prince.govtcompressor.core.GovPreset
import com.prince.govtcompressor.core.ImageEngine
import com.prince.govtcompressor.core.OutFormat
import com.prince.govtcompressor.core.Presets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CompressorScreen(presetId: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var origBytes by remember { mutableStateOf(0L) }
    var origFormat by remember { mutableStateOf("") }
    var targetKb by remember { mutableStateOf(50) }
    var customKb by remember { mutableStateOf("") }
    var useCustom by remember { mutableStateOf(false) }
    var outFormat by remember { mutableStateOf(OutFormat.JPEG) }
    var result by remember { mutableStateOf<com.prince.govtcompressor.core.CompressResult?>(null) }
    var savedMsg by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var preset by remember { mutableStateOf<GovPreset?>(null) }

    LaunchedEffect(presetId) {
        if (presetId != null) {
            val p = Presets.DEFAULTS.firstOrNull { it.id == presetId }
            if (p != null) preset = Presets.flow(context, p).first()
        }
    }
    LaunchedEffect(preset) {
        preset?.let {
            targetKb = it.targetKb
            outFormat = it.format
        }
    }

    fun loadImage(picked: Uri) {
        scope.launch {
            working = true; error = null; result = null; savedMsg = null
            try {
                val decoded = withContext(Dispatchers.IO) { ImageEngine.decode(context, picked, 3000) }
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
        ScreenTitle(
            preset?.let { "${it.name} — Photo" } ?: "Photo Compressor",
            preset?.let {
                "Preset: ${it.targetKb} KB, ${it.width}×${it.height} px, ${it.format.label}. " +
                    "Editable in Govt Presets — always check the portal's latest rules."
            } ?: "Compress any photo to an exact KB target for government uploads."
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
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
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .height(220.dp)
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                StatRow("Original", "${FileUtils.formatKb(origBytes)} · ${bmp.width}×${bmp.height} px · $origFormat")
            }
            Spacer(Modifier.height(12.dp))
            Text("Target size", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp))
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                for (kb in ImageEngine.TARGET_SIZES_KB) {
                    FilterChip(
                        selected = !useCustom && targetKb == kb,
                        onClick = { useCustom = false; targetKb = kb },
                        label = { Text(if (kb >= 1024) "1 MB" else "$kb KB") }
                    )
                }
                FilterChip(
                    selected = useCustom,
                    onClick = { useCustom = true },
                    label = { Text("Custom") }
                )
            }
            if (useCustom) {
                OutlinedTextField(
                    value = customKb,
                    onValueChange = { customKb = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Custom size in KB (e.g. 35)") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
            }
            Text("Output format", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp))
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                for (fmt in OutFormat.values()) {
                    FilterChip(
                        selected = outFormat == fmt,
                        onClick = { outFormat = fmt },
                        label = { Text(fmt.label) }
                    )
                }
            }
            Text(
                if (outFormat == OutFormat.PNG) "PNG is lossless — very small targets may not be reachable; the result card will show the real size."
                else "Quality and dimensions are adjusted automatically until the target is met.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                BigButton("Compress", onClick = {
                    val kb = if (useCustom) customKb.toIntOrNull() ?: 0 else targetKb
                    if (kb <= 0) { error = "Enter a target size in KB first."; return@BigButton }
                    scope.launch {
                        working = true; error = null; savedMsg = null
                        try {
                            val src = bmp
                            val resized = if ((preset?.width ?: 0) > 0 && (preset?.height ?: 0) > 0) {
                                ImageEngine.resize(src, preset!!.width, preset!!.height)
                            } else src
                            val r = withContext(Dispatchers.IO) {
                                ImageEngine.compressToTarget(resized, outFormat, kb * 1024)
                            }
                            if (resized != src) resized.recycle()
                            result = r
                            Presets.saveLastTargetKb(context, kb)
                        } catch (e: Exception) {
                            error = e.message ?: "Compression failed."
                        } finally { working = false }
                    }
                })
            }
        }
        val res = result
        if (res != null) {
            val kb = if (useCustom) customKb.toIntOrNull() ?: targetKb else targetKb
            ResultCard(
                title = if (res.metTarget) "Compressed to target ✓" else "Compressed (target not reached)",
                origBytes = origBytes,
                newBytes = res.achievedBytes.toLong(),
                targetBytes = (kb * 1024).toLong(),
                extraLines = listOf(
                    "Dimensions" to "${res.width}×${res.height} px",
                    "Quality used" to if (res.quality >= 0) "${res.quality}%" else "lossless"
                ),
                savedMessage = savedMsg,
                onSave = {
                    scope.launch {
                        val base = FileUtils.baseName(uri?.let { FileUtils.displayName(context, it) } ?: "photo")
                        val name = "${base}_${kb}kb.${outFormat.ext}"
                        val saved = withContext(Dispatchers.IO) {
                            FileUtils.saveToDownloads(context, name, outFormat.mime, res.bytes)
                        }
                        savedMsg = if (saved != null) "Saved to Downloads / Govt Photo Compressor / $name" else "Save failed — check storage permission."
                    }
                },
                onShare = {
                    scope.launch {
                        val base = FileUtils.baseName(uri?.let { FileUtils.displayName(context, it) } ?: "photo")
                        withContext(Dispatchers.IO) {
                            FileUtils.shareBytes(context, "${base}_${kb}kb.${outFormat.ext}", outFormat.mime, res.bytes)
                        }
                    }
                }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
    if (working) WorkingOverlay("Processing…")
}
