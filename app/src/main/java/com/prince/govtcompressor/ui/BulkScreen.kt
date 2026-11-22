@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.prince.govtcompressor.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prince.govtcompressor.core.BulkWorker
import com.prince.govtcompressor.core.ImageEngine
import com.prince.govtcompressor.core.OutFormat

@Composable
fun BulkScreen() {
    val context = LocalContext.current
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var targetKb by remember { mutableStateOf(50) }
    var customKb by remember { mutableStateOf("") }
    var useCustom by remember { mutableStateOf(false) }
    var outFormat by remember { mutableStateOf(OutFormat.JPEG) }
    var error by remember { mutableStateOf<String?>(null) }
    var workId by remember { mutableStateOf<java.util.UUID?>(null) }
    var resultText by remember { mutableStateOf<String?>(null) }

    val pickMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { picked ->
        if (picked.isNotEmpty()) {
            uris = picked; resultText = null; error = null
        }
    }

    val workInfo by workId?.let {
        WorkManager.getInstance(context).getWorkInfoByIdLiveData(it).observeAsState()
    } ?: remember { mutableStateOf<WorkInfo?>(null) }

    val progress = workInfo?.progress?.getInt(BulkWorker.KEY_PROGRESS, 0) ?: 0
    val state = workInfo?.state
    if (state == WorkInfo.State.SUCCEEDED && resultText == null) {
        val done = workInfo?.outputData?.getInt(BulkWorker.KEY_DONE, 0) ?: 0
        val failed = workInfo?.outputData?.getInt(BulkWorker.KEY_FAILED, 0) ?: 0
        val zipName = workInfo?.outputData?.getString(BulkWorker.KEY_ZIP_NAME).orEmpty()
        val failedNames = workInfo?.outputData?.getString(BulkWorker.KEY_FAILED_NAMES).orEmpty()
        resultText = buildString {
            append("Done: $done compressed and saved to Downloads / Govt Photo Compressor.")
            if (zipName.isNotEmpty()) append("\nZIP of all results: $zipName")
            if (failed > 0) append("\nFailed: $failed ($failedNames)")
        }
    }
    if (state == WorkInfo.State.FAILED && resultText == null) {
        resultText = "Bulk compression failed. Try fewer photos at once."
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Bulk Photo Compressor", "Compress many photos at once. It keeps working in the background, and every file plus one ZIP are saved to Downloads / Govt Photo Compressor.")
        Column(Modifier.padding(horizontal = 16.dp)) {
            BigButton("Choose Photos", onClick = { pickMultiple.launch(arrayOf("image/*")) })
        }
        ErrorBox(error)
        if (uris.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                StatRow("Photos selected", "${uris.size}")
            }
            Spacer(Modifier.height(8.dp))
            Text("Target size", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp))
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (kb in ImageEngine.TARGET_SIZES_KB) {
                    FilterChip(selected = !useCustom && targetKb == kb,
                        onClick = { useCustom = false; targetKb = kb },
                        label = { Text(if (kb >= 1024) "1 MB" else "$kb KB") })
                }
                FilterChip(selected = useCustom, onClick = { useCustom = true }, label = { Text("Custom") })
            }
            if (useCustom) {
                OutlinedTextField(
                    value = customKb,
                    onValueChange = { customKb = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Custom size in KB") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                BigButton("Compress All (${uris.size})", enabled = state != WorkInfo.State.RUNNING, onClick = {
                    val kb = if (useCustom) customKb.toIntOrNull() ?: 0 else targetKb
                    if (kb <= 0) { error = "Enter a target size in KB first."; return@BigButton }
                    resultText = null
                    val request = OneTimeWorkRequestBuilder<BulkWorker>()
                        .setInputData(workDataOf(
                            BulkWorker.KEY_URIS to uris.map { it.toString() }.toTypedArray(),
                            BulkWorker.KEY_TARGET_KB to kb,
                            BulkWorker.KEY_FORMAT to outFormat.name
                        ))
                        .addTag(BulkWorker.WORK_TAG)
                        .build()
                    workId = request.id
                    WorkManager.getInstance(context).enqueue(request)
                })
            }
            if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Compressing… $progress%", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("You can leave this screen — it keeps running in the background.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (resultText != null) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(resultText!!, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
