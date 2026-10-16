package app.stickerdock.duo

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.view.WindowManager
import android.widget.ImageView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

@Composable
fun ClipStickerEditor(uri: Uri, packs: List<StickerPack>, onSave: suspend (ByteArray, String, String?, String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ownerInsets = WindowInsets.safeDrawing
    val scope = rememberCoroutineScope()
    var source by remember(uri) { mutableStateOf<ClipStickerCreator?>(null) }
    var opening by remember(uri) { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var start by rememberSaveable(uri.toString()) { mutableStateOf(0) }
    var duration by rememberSaveable(uri.toString()) { mutableStateOf(2500) }
    var caption by rememberSaveable(uri.toString()) { mutableStateOf("") }
    var cover by rememberSaveable(uri.toString()) { mutableStateOf(true) }
    var fps by rememberSaveable(uri.toString()) { mutableStateOf(8) }
    var target by rememberSaveable(uri.toString()) { mutableStateOf<String?>(null) }
    var packName by rememberSaveable(uri.toString()) { mutableStateOf("My animated reactions") }
    var result by remember { mutableStateOf<ClipStickerCreator.Result?>(null) }
    var renderedEdit by remember { mutableStateOf<ClipStickerCreator.Edit?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var renderJob by remember { mutableStateOf<Job?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    val busy = progress != null || saving
    val edit = ClipStickerCreator.Edit(start, duration, caption, cover, fps)
    val currentPreview = result != null && renderedEdit == edit
    val dismiss = { if (!saving) { if (result != null || caption.isNotBlank()) confirmClose = true else onDismiss() } }

    LaunchedEffect(uri) {
        try {
            source = withContext(Dispatchers.IO) { ClipStickerCreator.open(context, uri) }
            duration = min(duration, source!!.durationMs - start).coerceAtLeast(500)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Could not open this clip. Choose a video up to 1080p or a GIF, within 20 MB and ten minutes." }
        finally { opening = false }
    }
    DisposableEffect(source) { val opened = source; onDispose { opened?.close() } }

    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(ownerInsets.union(WindowInsets.safeDrawing)).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !saving, onClick = dismiss) { Text("Cancel") }
                    Button(enabled = currentPreview && !busy && (target != null || packName.isNotBlank()), onClick = {
                        val bytes = result?.bytes ?: return@Button
                        saving = true; error = null
                        scope.launch {
                            try { onSave(bytes, caption, target, packName) }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = if (e is java.io.IOException) "Could not save. Check storage and try again. Your preview is still here."
                                else e.message?.take(180) ?: "Could not save. Your preview is still here; try again." }
                            finally { saving = false }
                        }
                    }) { Text(if (saving) "Saving…" else "Save sticker") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Make an animated reaction", style = MaterialTheme.typography.headlineSmall)
                    Text("Trim a moment, add your caption and preview it before saving. Your clip stays on this device.", style = MaterialTheme.typography.bodyMedium)
                    if (opening) LinearProgressIndicator(Modifier.fillMaxWidth())
                    result?.let { preview ->
                        AnimatedClipPreview(preview.bytes)
                        Text(if (currentPreview) "${preview.frames} frames · ${preview.durationMs / 1000f}s · ${preview.bytes.size / 1000} KB"
                            else "Settings changed. Preview again before saving.", style = MaterialTheme.typography.labelMedium)
                    }
                    source?.let { opened ->
                        Text("Start: ${start / 1000f}s of ${opened.durationMs / 1000f}s", style = MaterialTheme.typography.titleSmall)
                        Slider(value = start.toFloat(), enabled = !busy && opened.durationMs > 500,
                            onValueChange = { start = it.toInt(); duration = min(duration, opened.durationMs - start) },
                            valueRange = 0f..(opened.durationMs - 500).coerceAtLeast(1).toFloat())
                        Text("Length: ${duration / 1000f}s", style = MaterialTheme.typography.titleSmall)
                        val longest = min(4000, opened.durationMs - start)
                        if (longest > 500) Slider(value = duration.toFloat().coerceIn(500f, longest.toFloat()), enabled = !busy,
                            onValueChange = { duration = it.toInt() }, valueRange = 500f..longest.toFloat())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(cover, { cover = true }, label = { Text("Fill square") }, enabled = !busy)
                            FilterChip(!cover, { cover = false }, label = { Text("Keep full frame") }, enabled = !busy)
                        }
                        Text("Motion", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(6, 8, 10).forEach { rate -> FilterChip(fps == rate, { fps = rate }, label = { Text("$rate fps") }, enabled = !busy) }
                        }
                        OutlinedTextField(caption, { caption = it.filter { c -> c.code >= 32 }.take(80) }, label = { Text("Reaction caption") },
                            placeholder = { Text("Bas kar bhai") }, supportingText = { Text("Optional · ${caption.length}/80") }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth())
                        Button(enabled = !busy, onClick = {
                            val requested = edit
                            progress = "Preparing preview"; error = null
                            renderJob = scope.launch {
                                try {
                                    val created = withContext(Dispatchers.Default) { opened.render(requested) { message -> progress = message } }
                                    result = created; renderedEdit = requested
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.message?.take(180) ?: "Could not make this reaction. Try a shorter clip." }
                                finally { progress = null }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Preview animation") }
                    }
                    progress?.let {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(it, style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { renderJob?.cancel() }) { Text("Stop preview") }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    HorizontalDivider()
                    Text("Save to pack", style = MaterialTheme.typography.titleMedium)
                    Text("Animated and static stickers stay separate. A pack needs 3 stickers before WhatsApp can accept it.", style = MaterialTheme.typography.bodySmall)
                    FilterChip(target == null, { target = null }, label = { Text("New animated pack") }, enabled = !busy)
                    if (target == null) OutlinedTextField(packName, { packName = it.filter { c -> c.code >= 32 }.take(60) }, label = { Text("Pack name") },
                        singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    packs.filter { it.kind == StickerKind.ANIMATED }.forEach { pack ->
                        FilterChip(target == pack.id, { target = pack.id }, label = { Text(pack.name) }, enabled = !busy)
                    }
                    Text("The saved animation is an export. Keep the source clip if you want to make more versions later.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { Text("Close this creation?") },
        text = { Text("This unsaved animation will close. Your existing stickers and source clip stay unchanged.") },
        confirmButton = { TextButton(onClick = { renderJob?.cancel(); onDismiss() }) { Text("Close creation") } },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep editing") } })
}

@Composable
private fun AnimatedClipPreview(bytes: ByteArray) {
    val drawable by produceState<AnimatedImageDrawable?>(null, bytes) {
        value = withContext(Dispatchers.Default) { ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) as? AnimatedImageDrawable }
    }
    DisposableEffect(drawable) { drawable?.start(); onDispose { drawable?.stop() } }
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Animated reaction preview" } },
            update = { it.setImageDrawable(drawable) }, modifier = Modifier.size(240.dp))
    }
}
