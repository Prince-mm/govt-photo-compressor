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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prince.govtcompressor.core.Presets
import kotlinx.coroutines.launch

val PRIVACY_POLICY = """
Govt Photo & Document Compressor — Privacy Policy

1. Everything happens on your phone. All compression, conversion, resizing, cropping and PDF work is done locally on your device. Your photos and documents are never uploaded to any server by this app.

2. No account. The app does not ask you to register or log in, and it collects no personal documents.

3. Files you save. When you tap Save, the finished file is written to Downloads / Govt Photo Compressor on your device. The app keeps no copy of your photos after you leave a screen.

4. Sharing. When you tap Share, the file is sent only to the app you choose (for example WhatsApp or Gmail) using Android's share sheet.

5. Permissions. The app asks only for access to the photos and files you choose, and only when you choose them. No contacts, location or other data is requested.

6. No advertising trackers. This app contains no ads and no analytics trackers.

7. Questions. The app is developed for personal use; if a portal rejects a file, check that portal's current size and format rules — this app's presets are editable starting values, not the portal's rules.
""".trimIndent()

@Composable
fun SettingsScreen(darkMode: Boolean, onDarkModeChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Settings & Privacy", "Your files stay on your phone. Nothing is uploaded.")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dark mode", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = darkMode,
                onCheckedChange = {
                    onDarkModeChange(it)
                    scope.launch { Presets.setDarkMode(context, it) }
                }
            )
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            StatRow("Save location", "Downloads / Govt Photo Compressor")
            StatRow("Version", "1.0")
            StatRow("Processing", "100% offline, on-device")
        }
        Spacer(Modifier.height(16.dp))
        Text("Privacy Policy", fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        Text(PRIVACY_POLICY, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(24.dp))
    }
}
