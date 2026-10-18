package app.stickerdock.duo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StickerMaskEditor(original: ByteArray, loadMask: suspend () -> ByteArray?, outline: List<CutoutPoint>,
                      onApply: suspend (ByteArray) -> Unit, onDismiss: () -> Unit) {
    val ownerInsets = WindowInsets.safeDrawing
    var strokes by rememberSaveable(stateSaver = Saver<List<MaskStroke>, String>(
        save = { ArtworkBrush.encode(it) }, restore = { ArtworkBrush.decode(it) }
    )) { mutableStateOf(emptyList<MaskStroke>()) }
    var redo by remember { mutableStateOf(emptyList<MaskStroke>()) }
    var restore by rememberSaveable { mutableStateOf(false) }
    var radius by rememberSaveable { mutableFloatStateOf(18f) }
    var images by remember { mutableStateOf<Pair<Bitmap, Bitmap>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try {
            val mask = loadMask()
            val base = withContext(Dispatchers.Default) { ArtworkBrush.base(mask, outline) }
            val source = BitmapFactory.decodeByteArray(original, 0, original.size)
            if (source == null) { base.recycle(); error = "Could not open the starting image." }
            else images = source to base
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Could not open the cutout." }
    }
    DisposableEffect(images) {
        val owned = images
        onDispose { owned?.first?.recycle(); owned?.second?.recycle() }
    }
    fun begin(point: CutoutPoint) {
        drawing = !saving && images != null && strokes.size < ArtworkBrush.MAX_STROKES && strokes.sumOf { it.points.size } < ArtworkBrush.MAX_POINTS
        limit = !drawing && !saving && images != null
        if (drawing) { strokes = strokes + MaskStroke(listOf(point), radius, restore); redo = emptyList() }
    }
    fun append(point: CutoutPoint) {
        if (!drawing) return
        if (strokes.sumOf { it.points.size } >= ArtworkBrush.MAX_POINTS) { limit = true; drawing = false; return }
        val last = strokes.last()
        val previous = last.points.last()
        if (kotlin.math.hypot(point.x - previous.x, point.y - previous.y) >= .003f)
            strokes = strokes.dropLast(1) + last.copy(points = last.points + point)
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(ownerInsets.union(WindowInsets.safeDrawing)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel refinement") }
                    Button(enabled = images != null && strokes.isNotEmpty() && !saving && !drawing, onClick = {
                        saving = true; error = null
                        scope.launch {
                            try {
                                val bytes = withContext(Dispatchers.Default) { ArtworkBrush.render(images!!.second, strokes) }
                                onApply(bytes)
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "Could not apply. Your brush edits are still here." }
                            finally { saving = false }
                        }
                    }) { Text(if (saving) "Applying…" else "Apply") }
                }
                Text("Refine edges", style = MaterialTheme.typography.headlineSmall)
                Text("Erase background or restore parts of the original. Apply keeps these edits in your sticker draft.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !restore, enabled = !saving, onClick = { restore = false }, label = { Text("Erase") })
                    FilterChip(selected = restore, enabled = !saving, onClick = { restore = true }, label = { Text("Restore") })
                    TextButton(enabled = strokes.isNotEmpty() && !saving && !drawing, onClick = { redo = redo + strokes.last(); strokes = strokes.dropLast(1); limit = false }) { Text("Undo") }
                    TextButton(enabled = redo.isNotEmpty() && !saving && !drawing, onClick = { strokes = strokes + redo.last(); redo = redo.dropLast(1) }) { Text("Redo") }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight, 480.dp)
                    Canvas(Modifier.size(side).semantics { contentDescription = "Brush on sticker edges" }
                        .pointerInput(restore, radius, saving, images) {
                            detectTapGestures { offset -> begin(CutoutPoint((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f))); drawing = false }
                        }.pointerInput(restore, radius, saving, images) {
                            fun point(offset: Offset) = CutoutPoint((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f))
                            detectDragGestures(onDragStart = { begin(point(it)) }, onDragEnd = { drawing = false }, onDragCancel = { drawing = false }) { change, _ -> change.consume(); append(point(change.position)) }
                        }) {
                        val tile = size.width / 16
                        for (row in 0..15) for (column in 0..15) drawRect(if ((row + column) % 2 == 0) Color(0xFFE2E4EA) else Color.White, Offset(column * tile, row * tile), Size(tile, tile))
                        images?.let { (source, base) ->
                            val canvas = drawContext.canvas.nativeCanvas
                            val root = canvas.save()
                            canvas.scale(size.width / 512, size.height / 512)
                            val artwork = canvas.saveLayer(0f, 0f, 512f, 512f, null)
                            canvas.drawBitmap(source, 0f, 0f, null)
                            val mask = canvas.saveLayer(0f, 0f, 512f, 512f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
                            canvas.drawBitmap(base, 0f, 0f, null)
                            ArtworkBrush.paint(canvas, strokes)
                            canvas.restoreToCount(mask); canvas.restoreToCount(artwork); canvas.restoreToCount(root)
                        }
                    }
                }
                Text("Brush size · ${(radius * 2).toInt()} px", style = MaterialTheme.typography.labelMedium)
                Slider(value = radius, onValueChange = { radius = it }, valueRange = 2f..60f, enabled = !saving)
                if (limit) Text("Apply these edits, then reopen Refine edges to continue.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
