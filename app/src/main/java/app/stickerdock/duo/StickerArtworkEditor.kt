package app.stickerdock.duo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun StickerArtworkEditor(item: StickerItem, files: StickerFiles, onSave: (ArtworkEdit) -> Unit, onDismiss: () -> Unit) {
    var edit by remember(item.id) { mutableStateOf(ArtworkEdit()) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var rendering by remember { mutableStateOf(true) }
    val input by produceState<ByteArray?>(null, item.fileName) {
        value = withContext(Dispatchers.IO) { runCatching { files.stickerFile(item.packId, item.fileName).readBytes() }.getOrNull() }
        if (value == null) { error = "Could not open sticker"; rendering = false }
    }
    LaunchedEffect(input, edit) {
        val original = input ?: return@LaunchedEffect
        rendering = true
        delay(120)
        try {
            preview = withContext(Dispatchers.Default) { StickerArtwork.render(original, edit).asImageBitmap() }
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Preview unavailable" }
        finally { rendering = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { edit = ArtworkEdit() }) { Text("Reset") }
                    Button(enabled = input != null && error == null && !rendering, onClick = { onSave(edit) }) { Text("Save") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Edit sticker", style = MaterialTheme.typography.headlineSmall)
                    Surface(Modifier.fillMaxWidth().heightIn(max = 300.dp).aspectRatio(1f), color = MaterialTheme.colorScheme.surfaceVariant) {
                        preview?.let { Image(it, "Sticker preview", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { edit = edit.copy(quarterTurns = (edit.quarterTurns + 1) % 4) }, modifier = Modifier.weight(1f)) { Text("Rotate") }
                        FilterChip(selected = edit.mirror, onClick = { edit = edit.copy(mirror = !edit.mirror) }, label = { Text("Mirror") })
                    }
                    Text("Zoom / crop")
                    Slider(value = edit.zoom, onValueChange = { edit = edit.copy(zoom = it) }, valueRange = 1f..3f)
                    Text("Horizontal position")
                    Slider(value = edit.horizontal, onValueChange = { edit = edit.copy(horizontal = it) }, valueRange = -1f..1f)
                    Text("Vertical position")
                    Slider(value = edit.vertical, onValueChange = { edit = edit.copy(vertical = it) }, valueRange = -1f..1f)
                    OutlinedTextField(edit.topText, { edit = edit.copy(topText = it.take(100)) }, label = { Text("Top caption") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(edit.bottomText, { edit = edit.copy(bottomText = it.take(100)) }, label = { Text("Bottom caption") }, modifier = Modifier.fillMaxWidth())
                    Text("Edits update this sticker in its pack. Open WhatsApp to let it refresh the pack.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
