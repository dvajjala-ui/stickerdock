package app.stickerdock.duo

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StickerCutoutEditor(original: ByteArray, initial: List<CutoutPoint>, onApply: (List<CutoutPoint>) -> Unit, onDismiss: () -> Unit) {
    val ownerInsets = WindowInsets.safeDrawing
    var points by remember { mutableStateOf(initial) }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, original) {
        value = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(original, 0, original.size)?.asImageBitmap() }
    }
    val accent = MaterialTheme.colorScheme.primary
    val outline = ArtworkCutout.canonical(points)
    val ready = ArtworkCutout.isValid(outline)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(ownerInsets.union(WindowInsets.safeDrawing)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel cutout") }
                    Text("Manual cutout", style = MaterialTheme.typography.titleLarge)
                }
                Text("Draw or tap an outline around the subject to keep. Everything outside it becomes transparent.")
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight, 380.dp)
                    Canvas(Modifier.size(side).semantics { contentDescription = "Trace cutout on starting image" }.pointerInput(Unit) {
                        detectTapGestures(onTap = { offset -> points = ArtworkCutout.append(points,
                            CutoutPoint((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f))) })
                    }.pointerInput(Unit) {
                        fun point(offset: Offset) = CutoutPoint((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f))
                        detectDragGestures(onDragStart = { points = listOf(point(it)) }, onDrag = { change, _ ->
                            change.consume()
                            points = ArtworkCutout.append(points, point(change.position))
                        })
                    }) {
                        val tile = size.width / 16
                        for (row in 0..15) for (column in 0..15) drawRect(
                            if ((row + column) % 2 == 0) Color(0xFFE2E4EA) else Color.White,
                            Offset(column * tile, row * tile), androidx.compose.ui.geometry.Size(tile, tile))
                        bitmap?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
                        if (points.isNotEmpty()) {
                            val path = Path().apply {
                                moveTo(points.first().x * size.width, points.first().y * size.height)
                                points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                                if (points.size >= 3) close()
                            }
                            drawPath(path, accent.copy(alpha = 0.18f))
                            drawPath(path, accent, style = Stroke(width = 3.dp.toPx()))
                        }
                    }
                }
                Text(if (ready) "Outline ready. You can undo this after applying." else "Trace a clear area to enable Apply cutout.",
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { points = emptyList() }, modifier = Modifier.weight(1f)) { Text("Start over") }
                    Button(enabled = bitmap != null && ready, onClick = { onApply(outline) }, modifier = Modifier.weight(1f)) { Text("Apply cutout") }
                }
            }
        }
    }
}
