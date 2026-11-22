package com.prince.govtcompressor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prince.govtcompressor.core.FileUtils

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp)
    ) { Text(text, fontSize = 15.sp) }
}

@Composable
fun ErrorBox(message: String?) {
    if (message.isNullOrBlank()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE8E6)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFB3261E))
            Spacer(Modifier.width(8.dp))
            Text(message, color = Color(0xFF7A1F1A), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun WorkingOverlay(text: String = "Processing…") {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Spacer(Modifier.width(16.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Before/after result card with actual sizes — never claims a target unless met. */
@Composable
fun ResultCard(
    title: String,
    origBytes: Long,
    newBytes: Long,
    targetBytes: Long?,
    extraLines: List<Pair<String, String>> = emptyList(),
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    savedMessage: String? = null
) {
    val saved = newBytes <= origBytes
    val met = targetBytes == null || newBytes <= targetBytes
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (met) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (met) Color(0xFF2E7D32) else Color(0xFFB3261E)
                )
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            StatRow("Original size", FileUtils.formatKb(origBytes))
            StatRow("New size", FileUtils.formatKb(newBytes))
            StatRow("Saved", "${FileUtils.percentSaved(origBytes, newBytes)}%")
            if (targetBytes != null) {
                StatRow("Target", "≤ ${FileUtils.formatKb(targetBytes)}")
                if (!met) {
                    Text(
                        "Target not reached — this is the smallest the app could make it " +
                            "at this format. Try a smaller target or JPG output.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7A1F1A)
                    )
                }
            }
            extraLines.forEach { (l, v) -> StatRow(l, v) }
            if (savedMessage != null) {
                Spacer(Modifier.height(6.dp))
                Text(savedMessage, style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
            }
            if (onSave != null || onShare != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onSave != null) {
                        Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
                    }
                    if (onShare != null) {
                        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share") }
                    }
                }
            }
        }
    }
}

/** Interactive crop box over an image. Rect is in fractions of the image (0..1).
 *  Drag inside to move, drag a corner handle to resize. */
@Composable
fun CropView(
    bitmap: Bitmap,
    rect: CropRect,
    onRectChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val imgAspect = bitmap.width.toFloat() / bitmap.height
        val boxAspect = w / h
        val (dispW, dispH) = if (imgAspect > boxAspect) w to w / imgAspect else h * imgAspect to h
        val offX = (w - dispW) / 2
        val offY = (h - dispH) / 2
        Box(
            Modifier
                .size(with(density) { dispW.toDp() }, with(density) { dispH.toDp() })
                .align(Alignment.Center)
                .onSizeChanged { boxSize = it }
                .pointerInput(rect) {
                    var mode = 0 // 0 none, 1 move, 2 resize
                    var startRect = rect
                    var startPos = Offset.Zero
                    detectDragGestures(
                        onDragStart = { pos ->
                            startRect = rect
                            startPos = pos
                            val x = (pos.x - offX) / dispW
                            val y = (pos.y - offY) / dispH
                            val nearRight = kotlin.math.abs(x - rect.right) < 0.08f
                            val nearBottom = kotlin.math.abs(y - rect.bottom) < 0.08f
                            val nearLeft = kotlin.math.abs(x - rect.left) < 0.08f
                            val nearTop = kotlin.math.abs(y - rect.top) < 0.08f
                            mode = if (nearRight || nearBottom || nearLeft || nearTop) 2 else 1
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            val dx = drag.x / dispW
                            val dy = drag.y / dispH
                            val r = startRect
                            val totalDx = (change.position.x - startPos.x) / dispW
                            val totalDy = (change.position.y - startPos.y) / dispH
                            val next = when (mode) {
                                1 -> {
                                    val rw = r.right - r.left
                                    val rh = r.bottom - r.top
                                    val nl = (r.left + totalDx).coerceIn(0f, 1f - rw)
                                    val nt = (r.top + totalDy).coerceIn(0f, 1f - rh)
                                    r.copy(left = nl, top = nt, right = nl + rw, bottom = nt + rh)
                                }
                                else -> r.copy(
                                    right = (r.right + totalDx).coerceIn(r.left + 0.05f, 1f),
                                    bottom = (r.bottom + totalDy).coerceIn(r.top + 0.05f, 1f)
                                )
                            }
                            onRectChange(next)
                        }
                    )
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            // Dim outside the crop rect.
            val l = rect.left * dispW
            val t = rect.top * dispH
            val rw = (rect.right - rect.left) * dispW
            val rh = (rect.bottom - rect.top) * dispH
            Box(
                Modifier
                    .size(with(density) { rw.toDp() }, with(density) { rh.toDp() })
                    .align(Alignment.TopStart)
                    .padding(
                        start = with(density) { l.toDp() },
                        top = with(density) { t.toDp() }
                    )
                    .border(2.dp, Color.White)
            )
        }
    }
}

data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun toPixels(bmpW: Int, bmpH: Int): IntArray = intArrayOf(
        (left * bmpW).toInt(),
        (top * bmpH).toInt(),
        ((right - left) * bmpW).toInt(),
        ((bottom - top) * bmpH).toInt()
    )
}
