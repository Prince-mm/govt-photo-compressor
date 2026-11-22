package com.prince.govtcompressor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prince.govtcompressor.core.Presets
import com.prince.govtcompressor.ui.BulkScreen
import com.prince.govtcompressor.ui.CompressorScreen
import com.prince.govtcompressor.ui.ConverterScreen
import com.prince.govtcompressor.ui.HomeScreen
import com.prince.govtcompressor.ui.PassportScreen
import com.prince.govtcompressor.ui.PdfMode
import com.prince.govtcompressor.ui.PdfScreen
import com.prince.govtcompressor.ui.PresetsScreen
import com.prince.govtcompressor.ui.ResizerScreen
import com.prince.govtcompressor.ui.SettingsScreen
import com.prince.govtcompressor.ui.SignatureScreen
import com.prince.govtcompressor.ui.theme.GovtCompressorTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkMode by Presets.darkModeFlow(applicationContext).collectAsState(initial = false)
            var darkOverride by remember { mutableStateOf<Boolean?>(null) }
            GovtCompressorTheme(darkTheme = darkOverride ?: darkMode) {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route ?: "home"
                val titles = mapOf(
                    "compressor" to "Photo Compressor",
                    "converter" to "Image Converter",
                    "resizer" to "Image Resizer",
                    "signature" to "Signature Compressor",
                    "passport" to "Passport Photo Maker",
                    "pdf" to "PDF Tools",
                    "bulk" to "Bulk Compressor",
                    "presets" to "Govt Presets",
                    "settings" to "Settings & Privacy"
                )
                val title = titles.entries.firstOrNull { route.startsWith(it.key) }?.value ?: ""
                Scaffold(
                    topBar = {
                        if (route != "home") {
                            TopAppBar(
                                title = { Text(title) },
                                navigationIcon = {
                                    IconButton(onClick = { nav.popBackStack() }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                                    navigationIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                ) { padding ->
                    NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                        composable("home") {
                            HomeScreen(
                                onNavigate = { r ->
                                    when (r) {
                                        "pdf_img2pdf" -> nav.navigate("pdf?mode=${PdfMode.IMG2PDF.name}")
                                        "pdf_pdf2img" -> nav.navigate("pdf?mode=${PdfMode.PDF2IMG.name}")
                                        else -> nav.navigate(r)
                                    }
                                },
                                onOpenPresets = { nav.navigate("presets") },
                                onOpenSettings = { nav.navigate("settings") }
                            )
                        }
                        composable(
                            "compressor?preset={preset}",
                            arguments = listOf(navArgument("preset") {
                                type = NavType.StringType; nullable = true; defaultValue = null
                            })
                        ) { entry ->
                            CompressorScreen(presetId = entry.arguments?.getString("preset"))
                        }
                        composable("converter") { ConverterScreen() }
                        composable("resizer") { ResizerScreen() }
                        composable("signature") { SignatureScreen() }
                        composable("passport") { PassportScreen() }
                        composable(
                            "pdf?mode={mode}",
                            arguments = listOf(navArgument("mode") {
                                type = NavType.StringType; nullable = true
                                defaultValue = PdfMode.COMPRESS.name
                            })
                        ) { entry ->
                            val m = runCatching {
                                PdfMode.valueOf(entry.arguments?.getString("mode") ?: PdfMode.COMPRESS.name)
                            }.getOrDefault(PdfMode.COMPRESS)
                            PdfScreen(m)
                        }
                        composable("bulk") { BulkScreen() }
                        composable("presets") {
                            PresetsScreen(onOpenCompressor = { id ->
                                nav.navigate("compressor?preset=$id")
                            })
                        }
                        composable("settings") {
                            SettingsScreen(
                                darkMode = darkOverride ?: darkMode,
                                onDarkModeChange = { darkOverride = it }
                            )
                        }
                    }
                }
            }
        }
    }
}
