package com.prince.govtcompressor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prince.govtcompressor.core.GovPreset
import com.prince.govtcompressor.core.OutFormat
import com.prince.govtcompressor.core.Presets
import kotlinx.coroutines.launch

@Composable
fun PresetsScreen(onOpenCompressor: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var presets by remember { mutableStateOf<List<GovPreset>>(Presets.DEFAULTS) }
    var editing by remember { mutableStateOf<GovPreset?>(null) }
    var kbText by remember { mutableStateOf("") }
    var wText by remember { mutableStateOf("") }
    var hText by remember { mutableStateOf("") }
    var fmt by remember { mutableStateOf(OutFormat.JPEG) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        presets = Presets.loadAll(context)
    }

    fun startEdit(p: GovPreset) {
        editing = p
        kbText = p.targetKb.toString()
        wText = p.width.toString()
        hText = p.height.toString()
        fmt = p.format
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(
            "Government Photo Presets",
            "Starting values for common portals — all editable. Portals change their rules: always check the portal's latest requirements before uploading. This app never claims a portal's current rule."
        )
        for (p in presets) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { startEdit(p) }) { Text("Edit") }
                        androidx.compose.material3.TextButton(onClick = { onOpenCompressor(p.id) }) { Text("Use") }
                    }
                    StatRow("Target size", "≤ ${p.targetKb} KB")
                    StatRow("Dimensions", "${p.width}×${p.height} px")
                    StatRow("Format", p.format.label)
                }
            }
        }
        val e = editing
        if (e != null) {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Edit: ${e.name}", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = kbText,
                        onValueChange = { kbText = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Target size (KB)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(
                            value = wText,
                            onValueChange = { wText = it.filter { c -> c.isDigit() }.take(5) },
                            label = { Text("Width (px)") },
                            modifier = Modifier.weight(1f).padding(end = 6.dp)
                        )
                        OutlinedTextField(
                            value = hText,
                            onValueChange = { hText = it.filter { c -> c.isDigit() }.take(5) },
                            label = { Text("Height (px)") },
                            modifier = Modifier.weight(1f).padding(start = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Format", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        for (f in OutFormat.values()) {
                            FilterChip(selected = fmt == f, onClick = { fmt = f },
                                label = { Text(f.label) },
                                modifier = Modifier.padding(end = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        androidx.compose.material3.Button(
                            onClick = {
                                val kb = kbText.toIntOrNull() ?: 0
                                val w = wText.toIntOrNull() ?: 0
                                val h = hText.toIntOrNull() ?: 0
                                if (kb <= 0 || w <= 0 || h <= 0) return@Button
                                scope.launch {
                                    val updated = e.copy(targetKb = kb, width = w, height = h, format = fmt)
                                    Presets.save(context, updated)
                                    presets = Presets.loadAll(context)
                                    editing = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Save") }
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                scope.launch {
                                    Presets.reset(context, e)
                                    presets = Presets.loadAll(context)
                                    editing = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Reset") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
