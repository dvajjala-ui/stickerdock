package app.stickerdock.duo

import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun StickerArtworkEditor(item: StickerItem, projects: ArtworkProjects, onSave: suspend (ArtworkHistory) -> Unit, onDismiss: () -> Unit) {
    // Full-screen Dialog can receive consumed bar insets on Android 15. Keep the
    // owner's unconsumed values as a fallback, and union with the dialog's IME.
    val ownerInsets = WindowInsets.safeDrawing
    var history by rememberSaveable(item.id, item.fileName, stateSaver = Saver(
        save = { it.encode() }, restore = { ArtworkHistory.decode(it) }
    )) { mutableStateOf(ArtworkHistory()) }
    val edit = history.edit
    val scope = rememberCoroutineScope()
    val projectLock = remember(item.id) { Mutex() }
    var initialized by rememberSaveable(item.id, item.fileName) { mutableStateOf(false) }
    var session by remember(item.id, item.fileName) { mutableStateOf<ArtworkProjects.Session?>(null) }
    var saving by remember { mutableStateOf(false) }
    var automaticBusy by remember { mutableStateOf(false) }
    var automaticJob by remember { mutableStateOf<Job?>(null) }
    var automaticGeneration by remember { mutableIntStateOf(0) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showCutout by remember { mutableStateOf(false) }
    var draftMessage by remember { mutableStateOf("Opening project…") }
    val hasChanges = session?.let { edit != it.savedHistory.edit } ?: false
    val dismiss = {
        automaticGeneration++; automaticJob?.cancel(); automaticBusy = false
        if (!saving) { if (hasChanges) confirmDiscard = true else onDismiss() }
    }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var rendering by remember { mutableStateOf(true) }
    LaunchedEffect(item.id, item.fileName) {
        try {
            val opened = withContext(Dispatchers.IO) { projects.open(item) }
            if (!initialized) { history = opened.history; initialized = true }
            session = opened
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            error = if (e is java.io.IOException) "Could not read this project. Check storage or restore the sticker from a backup." else e.message ?: "Could not open sticker"
            draftMessage = "Project unavailable"
            rendering = false
        }
    }
    val input = session?.original
    LaunchedEffect(session, history) {
        if (session == null || !initialized) return@LaunchedEffect
        draftMessage = if (hasChanges) "Saving draft…" else "Saved version"
        delay(300)
        try {
            projectLock.withLock { withContext(Dispatchers.IO) { projects.saveDraft(item, history) } }
            draftMessage = if (hasChanges) "Draft saved on this device" else "Saved version"
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { draftMessage = "Draft could not be saved. Keep this screen open and try again." }
    }
    LaunchedEffect(input, edit) {
        val original = input ?: return@LaunchedEffect
        rendering = true
        delay(120)
        try {
            val mask = withContext(Dispatchers.IO) { projects.mask(item, edit) }
            preview = withContext(Dispatchers.Default) { StickerArtwork.render(original, edit, mask).asImageBitmap() }
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Preview unavailable" }
        finally { rendering = false }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        // Compose 1.7 only selects resize below Android 12. Its default pan mode
        // otherwise moves the entire header under the status bar during typing.
        SideEffect { window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(ownerInsets.union(WindowInsets.safeDrawing)).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = !saving, onClick = dismiss) { Text("Cancel") }
                    Button(enabled = input != null && error == null && !rendering && !saving && !automaticBusy && hasChanges, onClick = {
                        saving = true
                        saveError = null
                        scope.launch {
                            try { projectLock.withLock { onSave(history) } }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { saveError = if (e is java.io.IOException) "Could not save. Check device storage and try again. Your edits are still here." else e.message ?: "Could not save. Your edits are still here; try again." }
                            finally { saving = false }
                        }
                    }) { Text(if (saving) "Saving…" else "Save") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Edit sticker", style = MaterialTheme.typography.headlineSmall)
                    Text(draftMessage, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = history.canUndo && !saving && !automaticBusy, onClick = { history = history.undo() }) { Text("Undo") }
                        OutlinedButton(enabled = history.canRedo && !saving && !automaticBusy, onClick = { history = history.redo() }) { Text("Redo") }
                        Spacer(Modifier.weight(1f))
                        TextButton(enabled = input != null && edit != ArtworkEdit() && !saving && !automaticBusy, onClick = { history = history.update(ArtworkEdit()) }) { Text("Reset") }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val side = minOf(maxWidth, 240.dp)
                        Surface(Modifier.size(side).align(Alignment.Center), color = MaterialTheme.colorScheme.surfaceVariant) {
                            preview?.let { Image(it, "Sticker preview", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(enabled = input != null && !saving && !automaticBusy, modifier = Modifier.fillMaxWidth(), onClick = {
                        val original = input ?: return@Button
                        automaticBusy = true
                        automaticGeneration++
                        val request = automaticGeneration
                        saveError = null
                        automaticJob = scope.launch {
                            try {
                                val mask = AutomaticCutout.createMask(original)
                                val hash = projectLock.withLock { withContext(Dispatchers.IO) { projects.retainMask(item, mask) } }
                                history = history.update(history.edit.copy(maskHash = hash))
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { saveError = e.message ?: "Could not remove the background. Try Manual cutout." }
                            finally { if (automaticGeneration == request) automaticBusy = false }
                        }
                    }) { Text(if (automaticBusy) "Finding subject…" else "Remove background") }
                    if (automaticBusy) TextButton(onClick = { automaticGeneration++; automaticJob?.cancel(); automaticBusy = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancel removal") }
                    Text("Runs on this device. First use may need a model download.", style = MaterialTheme.typography.bodySmall)
                    if (edit.maskHash.isNotEmpty()) TextButton(enabled = !saving && !automaticBusy,
                        onClick = { history = history.update(edit.copy(maskHash = "", cutout = emptyList())) }) { Text("Restore background") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = input != null && !saving && !automaticBusy, onClick = { showCutout = true }) { Text("Cutout") }
                        OutlinedButton(enabled = input != null && !saving, onClick = { history = history.update(edit.copy(quarterTurns = (edit.quarterTurns + 1) % 4)) }, modifier = Modifier.weight(1f)) { Text("Rotate") }
                        FilterChip(enabled = input != null && !saving, selected = edit.mirror, onClick = { history = history.update(edit.copy(mirror = !edit.mirror)) }, label = { Text("Mirror") })
                    }
                    if (edit.cutout.isNotEmpty()) TextButton(enabled = !saving, onClick = { history = history.update(edit.copy(cutout = emptyList())) }) { Text("Remove cutout") }
                    Text("Zoom / crop")
                    Slider(enabled = !saving, value = edit.zoom, onValueChange = { history = history.update(edit.copy(zoom = it), "zoom") }, onValueChangeFinished = { history = history.endGroup() }, valueRange = 1f..3f)
                    Text("Horizontal position")
                    Slider(enabled = !saving, value = edit.horizontal, onValueChange = { history = history.update(edit.copy(horizontal = it), "horizontal") }, onValueChangeFinished = { history = history.endGroup() }, valueRange = -1f..1f)
                    Text("Vertical position")
                    Slider(enabled = !saving, value = edit.vertical, onValueChange = { history = history.update(edit.copy(vertical = it), "vertical") }, onValueChangeFinished = { history = history.endGroup() }, valueRange = -1f..1f)
                    OutlinedTextField(edit.topText, { history = history.update(edit.copy(topText = it.take(100)), "top") }, enabled = !saving, label = { Text("Top caption") }, modifier = Modifier.fillMaxWidth().onFocusChanged { history = history.endGroup() })
                    OutlinedTextField(edit.bottomText, { history = history.update(edit.copy(bottomText = it.take(100)), "bottom") }, enabled = !saving, label = { Text("Bottom caption") }, modifier = Modifier.fillMaxWidth().onFocusChanged { history = history.endGroup() })
                    Text("Reset restores your starting sticker. Editable originals and drafts stay on this device; shared packs and backups contain saved stickers.", style = MaterialTheme.typography.bodySmall)
                    Text("Open WhatsApp after saving to let it refresh the pack.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (showCutout && input != null) StickerCutoutEditor(input, edit.cutout,
        onApply = { points ->
            // Quantize once so the preview and the persisted/reopened recipe are identical.
            val updated = history.update(edit.copy(cutout = points))
            history = ArtworkHistory.decodeStrict(updated.encode())
            showCutout = false
        }, onDismiss = { showCutout = false })
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("Discard your edits?") },
        text = { Text("Your saved sticker will stay as it was.") },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            saving = true
            scope.launch {
                try {
                    projectLock.withLock { withContext(Dispatchers.IO) { projects.discardDraft(item) } }
                    confirmDiscard = false; onDismiss()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { confirmDiscard = false; saveError = "Could not discard this draft. Try again." }
                finally { saving = false }
            }
        }) { Text("Discard edits") } },
        dismissButton = {
            Column {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    scope.launch {
                        try {
                            projectLock.withLock { withContext(Dispatchers.IO) { projects.saveDraft(item, history) } }
                            confirmDiscard = false; onDismiss()
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { confirmDiscard = false; saveError = "Could not keep this draft. Check storage and try again." }
                        finally { saving = false }
                    }
                }) { Text("Keep draft") }
                TextButton(enabled = !saving, onClick = { confirmDiscard = false }) { Text("Keep editing") }
            }
        })
}
