package com.prince.govtcompressor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ToolItem(val title: String, val subtitle: String, val icon: ImageVector, val route: String)

val TOOLS = listOf(
    ToolItem("Photo Compressor", "Shrink to 10 KB – 1 MB", Icons.Default.Compress, "compressor"),
    ToolItem("Image Converter", "JPG · PNG · WEBP · BMP", Icons.Default.SwapHoriz, "converter"),
    ToolItem("Image Resizer", "Exact width & height", Icons.Default.PhotoSizeSelectLarge, "resizer"),
    ToolItem("Signature Compressor", "Crop & shrink signatures", Icons.Default.Draw, "signature"),
    ToolItem("Passport Photo Maker", "35×45 mm + print sheet", Icons.Default.Badge, "passport"),
    ToolItem("PDF Compressor", "Shrink PDF file size", Icons.Default.Description, "pdf"),
    ToolItem("Image to PDF", "Photos → PDF document", Icons.Default.PictureAsPdf, "pdf_img2pdf"),
    ToolItem("PDF to Image", "PDF pages → JPG/PNG", Icons.Default.Image, "pdf_pdf2img"),
    ToolItem("Bulk Photo Compressor", "Many photos at once", Icons.Default.Groups, "bulk")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigate: (String) -> Unit, onOpenPresets: () -> Unit, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                "Govt Photo & Document Compressor",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Photos & documents ready for government portals — 100% offline, nothing leaves your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = onOpenPresets) {
                    Text("Govt Presets")
                }
                androidx.compose.material3.OutlinedButton(onClick = onOpenSettings) {
                    Text("Settings & Privacy")
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(TOOLS) { tool ->
                Card(
                    onClick = { onNavigate(tool.route) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.height(132.dp)
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Icon(
                            tool.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(tool.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(tool.subtitle, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start)
                    }
                }
            }
        }
    }
}
