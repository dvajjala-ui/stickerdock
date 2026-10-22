package app.stickerdock.duo

import android.view.WindowManager
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
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
import org.json.JSONObject
import java.util.UUID

@Composable
fun StickerArtworkEditor(item: StickerItem, projects: ArtworkProjects, cloud: CloudLibrary, allowOnlineAi: Boolean,
                         onSave: suspend (ArtworkHistory) -> Unit, onDismiss: () -> Unit) {
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
    var showRefinement by rememberSaveable { mutableStateOf(false) }
    var tool by rememberSaveable { mutableStateOf("Cutout") }
    var draftMessage by remember { mutableStateOf("Opening project…") }
    val aiAccount = cloud.accountId
    var aiRequestId by rememberSaveable(item.id, aiAccount) { mutableStateOf(cloud.pendingAi(item.id)) }
    var aiAvailable by remember(aiAccount) { mutableStateOf(false) }
    var aiJob by remember(item.id, aiAccount) { mutableStateOf<JSONObject?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiPollVersion by remember { mutableIntStateOf(0) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var showAiConsent by remember { mutableStateOf(false) }
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
    LaunchedEffect(aiAccount, allowOnlineAi) {
        aiAvailable = false
        if (aiAccount != null && allowOnlineAi) {
            aiAvailable = runCatching { withContext(Dispatchers.IO) {
                cloud.personalAi(JSONObject().put("action", "availability")).optBoolean("available")
            } }.getOrDefault(false)
        }
    }
    LaunchedEffect(aiAccount, aiRequestId, aiPollVersion) {
        val requestId = aiRequestId ?: return@LaunchedEffect
        if (aiAccount == null) return@LaunchedEffect
        while (true) {
            try {
                val result = withContext(Dispatchers.IO) {
                    cloud.personalAi(JSONObject().put("action", "status").put("id", requestId))
                }
                aiJob = result.getJSONObject("job")
                aiError = null
                if (aiJob?.optString("status") !in listOf("uploading", "queued", "running")) break
                delay(5000)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                aiError = e.message ?: "Could not check your AI request. Tap Refresh."
                break
            }
        }
    }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !saving, onClick = dismiss) { Text("Cancel") }
                    Text("Edit sticker", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = history.canUndo && !saving && !automaticBusy, onClick = { history = history.undo() }) { Text("Undo") }
                        TextButton(enabled = history.canRedo && !saving && !automaticBusy, onClick = { history = history.redo() }) { Text("Redo") }
                        Spacer(Modifier.weight(1f))
                        TextButton(enabled = input != null && edit != ArtworkEdit() && !saving && !automaticBusy, onClick = { history = history.update(ArtworkEdit()) }) { Text("Reset") }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val side = minOf(maxWidth, 320.dp)
                        val light = MaterialTheme.colorScheme.surface
                        val shade = MaterialTheme.colorScheme.surfaceContainerHigh
                        Surface(Modifier.size(side).align(Alignment.Center), shape = MaterialTheme.shapes.extraLarge) {
                            Box {
                                Canvas(Modifier.fillMaxSize()) {
                                    val tile = size.width / 20
                                    for (row in 0..19) for (column in 0..19) drawRect(if ((row + column) % 2 == 0) light else shade,
                                        Offset(column * tile, row * tile), Size(tile, tile))
                                }
                                preview?.let { Image(it, "Sticker preview", Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit) }
                                if (rendering) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                            }
                        }
                    }
                    Text(draftMessage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Cutout", "Transform", "Text")) { name ->
                            FilterChip(selected = tool == name, onClick = { tool = name }, label = { Text(name) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                        }
                    }
                    if (tool == "Cutout") {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = input != null && !saving && !automaticBusy, modifier = Modifier.weight(1f), onClick = { showRefinement = true }) { Text("Refine edges") }
                        OutlinedButton(enabled = input != null && !saving && !automaticBusy, modifier = Modifier.weight(1f), onClick = { showCutout = true }) { Text("Trace outline") }
                    }
                    if (edit.maskHash.isNotEmpty()) TextButton(enabled = !saving && !automaticBusy,
                        onClick = { history = history.update(edit.copy(maskHash = "", cutout = emptyList())) }) { Text("Restore background") }
                    if (edit.cutout.isNotEmpty()) TextButton(enabled = !saving, onClick = { history = history.update(edit.copy(cutout = emptyList())) }) { Text("Remove cutout") }
                    }
                    if (tool == "Transform") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = input != null && !saving, onClick = { history = history.update(edit.copy(quarterTurns = (edit.quarterTurns + 1) % 4)) }, modifier = Modifier.weight(1f)) { Text("Rotate") }
                        FilterChip(enabled = input != null && !saving, selected = edit.mirror, onClick = { history = history.update(edit.copy(mirror = !edit.mirror)) }, label = { Text("Mirror") })
                    }
                    Text("Zoom / crop")
                    Slider(enabled = !saving, value = edit.zoom, onValueChange = { history = history.update(edit.copy(zoom = it), "zoom") }, onValueChangeFinished = { history = history.endGroup() }, valueRange = 1f..3f)
                    Text("Horizontal position")
                    Slider(enabled = !saving, value = edit.horizontal, onValueChange = { history = history.update(edit.copy(horizontal = it), "horizontal") }, onValueChangeFinished = { history = history.endGroup() }, valueRange = -1f..1f)
                    Text("Vertical position")
                    Slider(enabled = !saving, value = edit.vertical, onValueChange = { history = history.update(edit.copy(vertical = it), "vertical") }, onValueChangeFinished = { history = history.endGroup() }, valueRange = -1f..1f)
                    }
                    if (tool == "Text") {
                    OutlinedTextField(edit.topText, { history = history.update(edit.copy(topText = it.take(100)), "top") }, enabled = !saving, label = { Text("Top caption") }, modifier = Modifier.fillMaxWidth().onFocusChanged { history = history.endGroup() })
                    OutlinedTextField(edit.bottomText, { history = history.update(edit.copy(bottomText = it.take(100)), "bottom") }, enabled = !saving, label = { Text("Bottom caption") }, modifier = Modifier.fillMaxWidth().onFocusChanged { history = history.endGroup() })
                    if (!allowOnlineAi) Text("This private mature pack stays on your phone.", style = MaterialTheme.typography.bodySmall)
                    else if (aiAccount == null) Text("Sign in under Private cloud to ask for an AI caption. Sync can stay off.", style = MaterialTheme.typography.bodySmall)
                    else {
                        if (aiAvailable && aiRequestId == null) {
                            OutlinedButton(enabled = input != null && !aiBusy && !saving, onClick = { showAiConsent = true },
                                modifier = Modifier.fillMaxWidth()) { Text("Suggest a reaction with AI") }
                            Text("Only this preview is sent online. It won't enter a pack until you apply and save it.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        aiRequestId?.let { requestId ->
                            val status = aiJob?.optString("status") ?: "checking"
                            Text("AI suggestion: $status", style = MaterialTheme.typography.bodySmall)
                            if (status == "complete") {
                                val caption = aiJob?.optString("caption").orEmpty()
                                if (caption.isNotBlank()) {
                                    Text(caption, style = MaterialTheme.typography.titleMedium)
                                    Button(enabled = !saving, onClick = {
                                        history = history.update(history.edit.copy(bottomText = caption))
                                        cloud.savePendingAi(item.id, null)
                                        aiRequestId = null; aiJob = null
                                    }) { Text("Use as bottom caption") }
                                }
                            } else if (status == "failed") {
                                Text(if (aiJob?.optString("error_code") == "limit") "Today's AI limit was reached."
                                    else "AI couldn't suggest a caption. Your sticker is unchanged.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            if ((status == "uploading" || aiError != null) && aiAvailable) {
                                TextButton(enabled = !aiBusy, onClick = { showAiConsent = true }) { Text("Retry sending this preview") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !aiBusy, onClick = { aiPollVersion++ }) { Text("Refresh") }
                                TextButton(enabled = !aiBusy, onClick = {
                                    aiBusy = true
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { cloud.personalAi(JSONObject().put("action", "cancel").put("id", requestId)) }
                                            cloud.savePendingAi(item.id, null)
                                            aiRequestId = null; aiJob = null
                                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                        catch (e: Exception) { aiError = e.message ?: "Could not cancel AI request" }
                                        finally { aiBusy = false }
                                    }
                                }) { Text(if (status == "complete" || status == "failed") "Clear" else "Cancel") }
                            }
                        }
                        aiError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    }
                    Text("Your original stays intact. Save when you're happy with the result.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (showAiConsent) AlertDialog(onDismissRequest = { showAiConsent = false },
        title = { Text("Send this sticker preview to AI?") },
        text = { Text("Only send a general-audience image you own or may use. Your selected preview, without its current captions, will go to StickerDock's private server and Groq for one suggested reaction. Your original photo, cutout mask and other stickers stay on this phone. Suggestions are never saved automatically.") },
        confirmButton = { Button(onClick = {
            showAiConsent = false
            val original = input ?: return@Button
            aiBusy = true; aiError = null
            scope.launch {
                val requestId = aiRequestId ?: cloud.pendingAi(item.id) ?: UUID.randomUUID().toString()
                try {
                    withContext(Dispatchers.IO) {
                        cloud.savePendingAi(item.id, requestId)
                        val mask = projects.mask(item, history.edit)
                        val bitmap = StickerArtwork.render(original, history.edit.copy(topText = "", bottomText = ""), mask)
                        val image = try { StickerArtwork.encode(bitmap) } finally { bitmap.recycle() }
                        cloud.personalAi(JSONObject().put("action", "submit").put("request_id", requestId)
                            .put("image", "data:image/webp;base64," + Base64.encodeToString(image, Base64.NO_WRAP))
                            .put("audience", "GENERAL").put("rights", "OWNED_OR_LICENSED").put("consent", true))
                    }
                    aiRequestId = requestId
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { aiRequestId = requestId; aiError = e.message ?: "AI request failed. Refresh or retry sending." }
                finally { aiBusy = false }
            }
        }) { Text("Send preview") } },
        dismissButton = { TextButton(onClick = { showAiConsent = false }) { Text("Keep offline") } })
    if (showCutout && input != null) StickerCutoutEditor(input, edit.cutout,
        onApply = { points ->
            // Quantize once so the preview and the persisted/reopened recipe are identical.
            val updated = history.update(edit.copy(cutout = points))
            history = ArtworkHistory.decodeStrict(updated.encode())
            showCutout = false
        }, onDismiss = { showCutout = false })
    if (showRefinement && input != null) StickerMaskEditor(input,
        loadMask = { withContext(Dispatchers.IO) { projects.mask(item, edit) } }, outline = edit.cutout,
        onApply = { bytes ->
            projectLock.withLock {
                val updated = withContext(Dispatchers.IO) {
                    val hash = projects.retainMask(item, bytes)
                    val next = history.update(history.edit.copy(maskHash = hash, cutout = emptyList()))
                    projects.saveDraft(item, next)
                    next
                }
                history = updated
            }
            showRefinement = false
        }, onDismiss = { showRefinement = false })
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
