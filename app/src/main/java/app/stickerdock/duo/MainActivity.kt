package app.stickerdock.duo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import java.io.File
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private lateinit var store: StickerStore
    private lateinit var files: StickerFiles
    private lateinit var backup: StickerBackup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = StickerStore(applicationContext)
        files = StickerFiles(applicationContext, store)
        backup = StickerBackup(applicationContext, store, files)
        setContent { MaterialTheme { Home() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Home() {
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val previewCache = remember { StickerPreviewCache() }
        val pausePhoneImport = remember { AtomicBoolean(false) }
        var packs by remember { mutableStateOf<List<StickerPack>>(emptyList()) }
        var selectedId by remember { mutableStateOf<String?>(null) }
        var stickers by remember { mutableStateOf<List<StickerItem>>(emptyList()) }
        var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
        var query by remember { mutableStateOf("") }
        var stickerQuery by remember { mutableStateOf("") }
        var stickerMatches by remember { mutableStateOf<List<StickerSearchResult>>(emptyList()) }
        var packFilter by remember { mutableStateOf("All") }
        var packSort by remember { mutableStateOf("Newest") }
        var categoryFilter by remember { mutableStateOf<PackCategory?>(null) }
        var newPackKind by remember { mutableStateOf(StickerKind.STATIC) }
        var newPackRating by remember { mutableStateOf(PackRating.GENERAL) }
        var newPackCategory by remember { mutableStateOf(PackCategory.GENERAL) }
        var busy by remember { mutableStateOf(false) }
        var phoneImportProgress by remember { mutableStateOf<FolderStickerImporter.Progress?>(null) }
        var phonePauseRequested by remember { mutableStateOf(false) }
        var pendingFolderImportUri by remember {
            mutableStateOf(FolderStickerImporter.pendingImportUri(this@MainActivity)?.toString())
        }
        var dialog by remember { mutableStateOf<String?>(null) }
        var name by remember { mutableStateOf("") }
        var showCommunity by remember { mutableStateOf(false) }
        var showSavedLinks by remember { mutableStateOf(false) }
        var linkInput by remember { mutableStateOf("") }
        var savedLinks by remember { mutableStateOf<List<StickerlyLinks.Link>>(emptyList()) }
        var communityLoading by remember { mutableStateOf(false) }
        var communityPacks by remember { mutableStateOf<List<CommunityCatalog.Listing>>(emptyList()) }
        var communityError by remember { mutableStateOf<String?>(null) }
        var catalogImportedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        var moving by remember { mutableStateOf<StickerItem?>(null) }
        var target by remember { mutableStateOf<String?>(null) }
        var editingSticker by remember { mutableStateOf<StickerItem?>(null) }
        var stickerLabel by remember { mutableStateOf("") }
        var stickerTags by remember { mutableStateOf("") }
        val pack = packs.firstOrNull { it.id == selectedId }

        fun refresh() {
            scope.launch {
                val data = withContext(Dispatchers.IO) {
                    val all = store.listPacks()
                    Triple(all, selectedId?.let(store::listStickers) ?: emptyList(),
                        store.stickerCounts())
                }
                packs = data.first
                stickers = data.second
                counts = data.third
                if (selectedId != null && packs.none { it.id == selectedId }) selectedId = null
            }
        }
        fun operation(action: suspend () -> String) {
            if (busy) return
            busy = true
            scope.launch {
                try {
                    val message = withContext(Dispatchers.IO) { action() }
                    refresh()
                    snackbar.showSnackbar(message)
                } catch (e: Exception) {
                    snackbar.showSnackbar(e.message ?: "Operation failed")
                } finally { busy = false }
            }
        }
        fun refreshFactory() {
            if (communityLoading) return
            communityLoading = true
            communityError = null
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val catalog = CommunityCatalog(this@MainActivity, backup).browse()
                        val imports = CatalogImports(this@MainActivity, store)
                        catalog to catalog.filter {
                            imports.importedPackIds(it.id, it.sha256).isNotEmpty()
                        }.mapTo(mutableSetOf()) { it.id }
                    }
                    communityPacks = result.first
                    catalogImportedIds = result.second
                } catch (e: Exception) {
                    communityError = e.message ?: "Unable to load Factory packs"
                } finally { communityLoading = false }
            }
        }
        fun importFactoryPack(entry: CommunityCatalog.Listing) {
            showCommunity = false
            operation {
                val summary = CommunityCatalog(this@MainActivity, backup).downloadAndImport(entry)
                val importedIds = CatalogImports(this@MainActivity, store).importedPackIds(entry.id, entry.sha256)
                runOnUiThread {
                    catalogImportedIds = catalogImportedIds + entry.id
                    selectedId = importedIds.firstOrNull()
                }
                if (summary.stickers == 0) "Pack already in your library"
                else "Imported ${summary.stickers} stickers. Choose Add to WhatsApp to use them."
            }
        }
        fun runFolderImport(uri: android.net.Uri) {
            if (busy) return
            pendingFolderImportUri = uri.toString()
            phoneImportProgress = null
            phonePauseRequested = false
            pausePhoneImport.set(false)
            operation {
                try {
                    val report = FolderStickerImporter(this@MainActivity, store, files).import(
                        uri,
                        shouldPause = { pausePhoneImport.get() || !scope.isActive },
                        onProgress = { progress -> runOnUiThread { phoneImportProgress = progress } }
                    )
                    buildString {
                        append(if (report.paused) "Phone import paused" else "Phone import complete")
                        append(": ${report.added} stickers in ${report.packsCreated} packs, ")
                        append("${report.duplicates} duplicate, ${report.rejected} rejected")
                        if (report.skippedDirectories > 0) {
                            append(", ${report.skippedDirectories} folders skipped at depth limit; import them separately")
                        }
                    }
                } finally {
                    runOnUiThread {
                        phoneImportProgress = null
                        phonePauseRequested = false
                        pendingFolderImportUri = FolderStickerImporter.pendingImportUri(this@MainActivity)?.toString()
                    }
                }
            }
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val id = selectedId ?: return@rememberLauncherForActivityResult
            if (uris.isNotEmpty()) operation {
                var added = 0; var duplicates = 0; var failed = 0
                uris.forEach {
                    when (files.importUri(id, it)) {
                        ImportResult.Added -> added++
                        ImportResult.Duplicate -> duplicates++
                        is ImportResult.Failed -> failed++
                    }
                }
                "Imported $added, duplicate $duplicates, failed $failed"
            }
        }
        val folderImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // Some document providers grant only temporary access. The
                    // import can still run now; resume will surface an error if
                    // the provider cannot be reopened after process death.
                }
                runFolderImport(uri)
            }
        }
        val zipImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) operation {
                require(uris.size <= 100) { "Select at most 100 ZIP files per batch" }
                var packsCreated = 0
                var added = 0
                var skipped = 0
                var failed = 0
                uris.forEach { uri ->
                    try {
                        val count = try {
                            val r = backup.restore(uri)
                            Triple(r.packs, r.stickers, r.rejected)
                        } catch (_: Exception) {
                            val r = PackZipImporter(this@MainActivity, store, files).import(uri)
                            Triple(r.packs, r.imported, r.skipped + r.duplicates)
                        }
                        packsCreated += count.first
                        added += count.second
                        skipped += count.third
                    } catch (_: Exception) { failed++ }
                }
                "Imported ${uris.size} ZIPs: ${packsCreated} packs, ${added} stickers, ${skipped} skipped, ${failed} failed"
            }
        }
        val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
            if (it != null) operation { backup.export(it); "Backup saved" }
        }
        val loadBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            if (it != null) operation {
                val s = backup.restore(it)
                "Restored ${s.packs} packs, ${s.stickers} stickers, ${s.rejected} skipped"
            }
        }
        LaunchedEffect(selectedId) { refresh() }
        LaunchedEffect(stickerQuery) {
            val requested = stickerQuery
            if (requested.isBlank()) stickerMatches = emptyList()
            else {
                delay(300)
                val matches = withContext(Dispatchers.IO) { store.searchStickers(requested) }
                if (stickerQuery == requested) stickerMatches = matches
            }
        }
        DisposableEffect(Unit) { onDispose { previewCache.clear() } }
        DisposableEffect(Unit) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshFactory()
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(Unit) {
            refreshFactory()
            if (intent.action == android.content.Intent.ACTION_SEND || intent.action == android.content.Intent.ACTION_SEND_MULTIPLE) {
                operation { IncomingStickers(this@MainActivity, store, files).import(intent) }
            }
        }
        Scaffold(topBar = {
            TopAppBar(title = { Text(pack?.name ?: "StickerDock") }, navigationIcon = {
                if (selectedId != null) TextButton(onClick = { selectedId = null }) { Text("Back") }
            })
        }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
                if (pack == null) {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Column {
                    Text("Your stickers, organized offline.")
                    Text("To migrate saved WhatsApp/Sticker.ly stickers, choose Android/media → com.whatsapp → WhatsApp → Media → WhatsApp Stickers (or the Business equivalent).")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { name = ""; newPackKind = StickerKind.STATIC; newPackRating = PackRating.GENERAL; newPackCategory = PackCategory.GENERAL; dialog = "create" }) { Text("New pack") }
                        OutlinedButton(enabled = !busy, onClick = {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { backup.estimate() }
                                    saveBackup.launch("stickerdock.zip")
                                } catch (e: Exception) {
                                    snackbar.showSnackbar(e.message ?: "Cannot create backup")
                                }
                            }
                        }) { Text("Backup") }
                        OutlinedButton(onClick = { loadBackup.launch(arrayOf("application/zip")) }) { Text("Restore") }
                    }
                    phoneImportProgress?.let { progress ->
                        Text(
                            "Phone import${if (progress.resumed) " (resumed)" else ""}: " +
                                "${progress.filesScanned} scanned • ${progress.added} added • " +
                                "${progress.duplicates} duplicate • ${progress.rejected} rejected",
                            style = MaterialTheme.typography.bodySmall
                        )
                        progress.currentFile?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                    if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (busy && phoneImportProgress != null) {
                        OutlinedButton(
                            enabled = !phonePauseRequested,
                            onClick = {
                                phonePauseRequested = true
                                pausePhoneImport.set(true)
                            }
                        ) { Text(if (phonePauseRequested) "Pausing…" else "Pause phone import") }
                    }
                    pendingFolderImportUri?.takeIf { !busy }?.let { savedUri ->
                        OutlinedButton(onClick = { runFolderImport(android.net.Uri.parse(savedUri)) }) {
                            Text("Resume phone import")
                        }
                        TextButton(onClick = {
                            operation {
                                val removed = FolderStickerImporter(this@MainActivity, store, files).discardPendingImport()
                                runOnUiThread {
                                    pendingFolderImportUri = null
                                    phoneImportProgress = null
                                }
                                "Discarded saved phone import${if (removed > 0) " and removed $removed empty packs" else ""}"
                            }
                        }) { Text("Discard saved import") }
                    }
                    OutlinedButton(enabled = !busy, onClick = { folderImporter.launch(null) }) { Text("Import phone sticker folder") }
                    OutlinedButton(enabled = !busy, onClick = { zipImporter.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Import sticker ZIP") }
                    OutlinedButton(onClick = {
                        showSavedLinks = true
                        scope.launch { savedLinks = withContext(Dispatchers.IO) { StickerlyLinks(this@MainActivity).list() } }
                    }) { Text("Saved Sticker.ly pack links") }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("From the Factory", style = MaterialTheme.typography.titleMedium)
                                TextButton(enabled = !communityLoading, onClick = { refreshFactory() }) { Text("Refresh") }
                            }
                            Text("Reviewed AI sticker packs. Import a pack, then add it to WhatsApp.",
                                style = MaterialTheme.typography.bodySmall)
                            if (communityLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (communityError != null) Text("Factory is unavailable. Your local stickers still work.",
                                style = MaterialTheme.typography.bodySmall)
                            else if (!communityLoading && communityPacks.isEmpty()) {
                                Text("New packs will appear here after review and publication.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            communityPacks.take(3).forEach { entry ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(entry.creator, style = MaterialTheme.typography.labelSmall)
                                        if (entry.categories.isNotEmpty()) {
                                            Text(entry.categories.joinToString(" • ") { it.label },
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    TextButton(enabled = !busy, onClick = { importFactoryPack(entry) }) {
                                        Text(if (entry.id in catalogImportedIds) "Open" else "Import")
                                    }
                                }
                            }
                            if (communityPacks.size > 3) TextButton(onClick = { showCommunity = true }) {
                                Text("View all ${communityPacks.size} packs")
                            }
                        }
                    }
                    OutlinedButton(enabled = false, onClick = {}) { Text("AI meme creator — Coming soon") }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All", "Static", "Animated").forEach { type ->
                            FilterChip(selected = packFilter == type, onClick = { packFilter = type }, label = { Text(type) })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Newest", "Name", "Largest")) { option ->
                            FilterChip(selected = packSort == option,
                                onClick = { packSort = option }, label = { Text(option) })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(selected = categoryFilter == null,
                                onClick = { categoryFilter = null }, label = { Text("All topics") })
                        }
                        items(PackCategory.entries.size) { index ->
                            val category = PackCategory.entries[index]
                            FilterChip(selected = categoryFilter == category,
                                onClick = { categoryFilter = category },
                                label = { Text(category.label) })
                        }
                    }
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        label = { Text("Search packs") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = stickerQuery, onValueChange = { stickerQuery = it },
                        label = { Text("Search stickers by label, tag, filename or pack") },
                        modifier = Modifier.fillMaxWidth())
                    if (stickerQuery.isNotBlank()) {
                        Text("${stickerMatches.size} sticker matches", style = MaterialTheme.typography.labelMedium)
                    }
                    } }
                        if (stickerQuery.isBlank()) {
                            items(packs.filter { it.name.contains(query, true) &&
                                    (packFilter == "All" || it.kind.name.equals(packFilter, ignoreCase = true)) &&
                                    (categoryFilter == null || it.category == categoryFilter) }
                                .let { filtered -> when (packSort) {
                                    "Name" -> filtered.sortedBy { it.name.lowercase() }
                                    "Largest" -> filtered.sortedWith(compareByDescending<StickerPack> { counts[it.id] ?: 0 }
                                        .thenBy { it.name.lowercase() })
                                    else -> filtered.sortedByDescending { it.createdAt }
                                } }, key = { it.id }) { item ->
                                Card(Modifier.fillMaxWidth().clickable { selectedId = item.id }) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name + if (item.kind == StickerKind.ANIMATED) " (Animated)" else " (Static)")
                                            Text(item.category.label, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text("${counts[item.id] ?: 0} ${if (item.rating == PackRating.MATURE) "Mature" else "stickers"}")
                                    }
                                }
                            }
                        } else {
                            items(stickerMatches, key = { it.item.id }) { result ->
                                Card(Modifier.fillMaxWidth().clickable { selectedId = result.pack.id }) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(result.item.displayName)
                                        Text("${result.pack.name} • ${result.pack.category.label}",
                                            style = MaterialTheme.typography.labelMedium)
                                        if (result.item.tags.isNotBlank()) {
                                            Text(result.item.tags, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("${pack.category.label} • ${pack.kind.name.lowercase().replaceFirstChar { it.uppercase() }} • ${pack.rating.name.lowercase().replaceFirstChar { it.uppercase() }} • ${stickers.size}/30 stickers")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(enabled = !busy && stickers.size < 30, onClick = { picker.launch(arrayOf("image/*")) }) { Text("Import") }
                        OutlinedButton(onClick = { name = pack.name; dialog = "rename" }) { Text("Rename") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { newPackCategory = pack.category; dialog = "category" }) { Text("Category") }
                        OutlinedButton(onClick = { dialog = "delete" }) { Text("Delete") }
                    }
                    OutlinedButton(enabled = !busy && stickers.isNotEmpty(), onClick = {
                        scope.launch {
                            try {
                                val send = withContext(Dispatchers.IO) { PackSharing(this@MainActivity, backup).share(pack) }
                                startActivity(android.content.Intent.createChooser(send, "Share sticker pack"))
                            } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Cannot share pack") }
                        }
                    }) { Text("Share pack ZIP") }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (pack.kind == StickerKind.ANIMATED) "Animated WebP only, up to 500 KB" else "PNG/JPEG/static WebP, up to 100 KB")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((title, packageName) in listOf("WhatsApp" to "com.whatsapp", "WA Business" to "com.whatsapp.w4b")) {
                            OutlinedButton(enabled = stickers.size in 3..30,
                                onClick = { installPack(pack, packageName, snackbar, scope) }) { Text(title) }
                        }
                    }
                    if (stickers.size < 3) Text("Import at least 3 stickers to export.")
                    LazyVerticalGrid(columns = GridCells.Adaptive(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        gridItems(stickers, key = { it.id }) { item ->
                            val path = files.stickerFile(item.packId, item.fileName).path
                            val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path, pack.kind) {
                                value = withContext(Dispatchers.IO) {
                                    if (pack.kind == StickerKind.STATIC) previewCache.load(path) else null
                                }
                            }
                            val animation by produceState<android.graphics.drawable.Drawable?>(null, path, pack.kind) {
                                value = withContext(Dispatchers.IO) {
                                    if (pack.kind == StickerKind.ANIMATED) try {
                                        ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path)))
                                    } catch (_: Exception) { null } else null
                                }
                            }
                            DisposableEffect(animation) {
                                (animation as? AnimatedImageDrawable)?.start()
                                onDispose { (animation as? AnimatedImageDrawable)?.stop() }
                            }
                            Card {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                                        if (pack.kind == StickerKind.ANIMATED) {
                                            if (animation == null) Text("Invalid animation")
                                            else AndroidView(factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                                update = { it.setImageDrawable(animation) }, modifier = Modifier.fillMaxSize())
                                        } else if (image == null) Text("Missing")
                                        else image?.let { Image(it, contentDescription = "Sticker", modifier = Modifier.fillMaxSize()) }
                                    }
                                    Text(item.displayName, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                    if (item.tags.isNotBlank()) Text(item.tags, maxLines = 2, style = MaterialTheme.typography.labelSmall)
                                    TextButton(onClick = {
                                        editingSticker = item
                                        stickerLabel = item.label
                                        stickerTags = item.tags
                                    }, enabled = !busy) { Text("Edit info") }
                                    TextButton(onClick = { moving = item; target = packs.firstOrNull { it.id != item.packId }?.id },
                                        enabled = packs.size > 1 && !busy) { Text("Copy/Move") }
                                    TextButton(onClick = { operation { files.deleteSticker(item); "Removed" } },
                                        enabled = !busy) { Text("Remove") }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showSavedLinks) AlertDialog(onDismissRequest = { showSavedLinks = false },
            title = { Text("Sticker.ly pack links") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste one or more public Sticker.ly pack links or codes. This saves bookmarks only; it does not download creators' stickers.")
                    OutlinedTextField(value = linkInput, onValueChange = { linkInput = it },
                        label = { Text("Pack links or codes (one per line)") }, minLines = 2, maxLines = 5)
                    if (savedLinks.isEmpty()) Text("No links saved.")
                    else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(savedLinks, key = { it.code }) { link ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(link.url)))
                                }, modifier = Modifier.weight(1f)) { Text(link.code) }
                                TextButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { StickerlyLinks(this@MainActivity).remove(link.code) }
                                        savedLinks = withContext(Dispatchers.IO) { StickerlyLinks(this@MainActivity).list() }
                                    }
                                }) { Text("Remove") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                val submitted = linkInput
                scope.launch {
                    try {
                        val count = withContext(Dispatchers.IO) { StickerlyLinks(this@MainActivity).saveFromText(submitted) }
                        savedLinks = withContext(Dispatchers.IO) { StickerlyLinks(this@MainActivity).list() }
                        linkInput = ""
                        snackbar.showSnackbar("Saved $count new pack links")
                    } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Invalid pack links") }
                }
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showSavedLinks = false }) { Text("Close") } })
        if (showCommunity) AlertDialog(onDismissRequest = { showCommunity = false },
            title = { Text("From the Factory") },
            text = {
                Column {
                    Text("Only moderator-approved general-audience packs are shown.")
                    if (communityLoading) CircularProgressIndicator()
                    else if (communityError != null) Text(communityError ?: "")
                    else if (communityPacks.isEmpty()) Text("No reviewed packs have been published yet.")
                    else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(communityPacks, key = { it.id }) { entry ->
                            TextButton(enabled = !busy, onClick = { importFactoryPack(entry) }) {
                                val topics = entry.categories.takeIf { it.isNotEmpty() }
                                    ?.joinToString(" • ") { it.label }?.let { " — $it" }.orEmpty()
                                Text("${entry.name} — ${entry.creator}$topics (${if (entry.id in catalogImportedIds) "open" else "import"})")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCommunity = false }) { Text("Close") } })
        moving?.let { item ->
            AlertDialog(onDismissRequest = { moving = null },
                title = { Text("Transfer sticker") },
                text = {
                    Column {
                        packs.filter { it.id != item.packId }.forEach { candidate ->
                            TextButton(onClick = { target = candidate.id }) {
                                Text((if (target == candidate.id) "● " else "○ ") + candidate.name)
                            }
                        }
                    }
                },
                confirmButton = {
                    Row {
                        listOf(false to "Copy", true to "Move").forEach { (move, label) ->
                            TextButton(enabled = target != null && !busy, onClick = {
                                val dest = target
                                moving = null
                                if (dest != null) operation {
                                    when (val result = files.transferSticker(item, dest, move)) {
                                        ImportResult.Added -> "$label complete"
                                        ImportResult.Duplicate -> "Duplicate in target"
                                        is ImportResult.Failed -> result.reason
                                    }
                                }
                            }) { Text(label) }
                        }
                    }
                },
                dismissButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } })
        }
        editingSticker?.let { item ->
            AlertDialog(onDismissRequest = { editingSticker = null },
                title = { Text("Sticker info") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stickerLabel,
                            onValueChange = { stickerLabel = it },
                            label = { Text("Label") },
                            supportingText = { Text("A readable name for search and display") }
                        )
                        OutlinedTextField(
                            value = stickerTags,
                            onValueChange = { stickerTags = it },
                            label = { Text("Tags") },
                            supportingText = { Text("Comma-separated, for example: funny, work, reaction") }
                        )
                        Text("Stored file: ${item.fileName}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                confirmButton = {
                    TextButton(enabled = !busy, onClick = {
                        val label = stickerLabel
                        val tags = stickerTags
                        editingSticker = null
                        operation {
                            store.updateStickerMetadata(item.packId, item.id, label, tags)
                            "Sticker info updated"
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editingSticker = null }) { Text("Cancel") } })
        }
        dialog?.let { mode ->
            AlertDialog(onDismissRequest = { dialog = null },
                title = { Text(when (mode) {
                    "create" -> "New pack"
                    "rename" -> "Rename pack"
                    "category" -> "Pack category"
                    else -> "Delete pack?"
                }) },
                text = {
                    if (mode == "delete") Text("Local stickers and data will be deleted.")
                    else if (mode == "category") LazyColumn {
                        items(PackCategory.entries.size) { index ->
                            val category = PackCategory.entries[index]
                            TextButton(onClick = { newPackCategory = category }) {
                                Text((if (newPackCategory == category) "● " else "○ ") + category.label)
                            }
                        }
                    }
                    else Column {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Pack name") })
                        if (mode == "create") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StickerKind.entries.forEach { kind ->
                                FilterChip(selected = newPackKind == kind, onClick = { newPackKind = kind }, label = { Text(kind.name.lowercase().replaceFirstChar { it.uppercase() }) })
                            }
                        }
                        if (mode == "create") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PackRating.entries.forEach { rating ->
                                FilterChip(selected = newPackRating == rating, onClick = { newPackRating = rating }, label = { Text(rating.name.lowercase().replaceFirstChar { it.uppercase() }) })
                            }
                        }
                        if (mode == "create") {
                            Text("Category", style = MaterialTheme.typography.labelMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(PackCategory.entries.size) { index ->
                                    val category = PackCategory.entries[index]
                                    FilterChip(selected = newPackCategory == category,
                                        onClick = { newPackCategory = category },
                                        label = { Text(category.label) })
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !busy && (mode in setOf("delete", "category") || name.isNotBlank()), onClick = {
                        val id = selectedId
                        val value = name
                        dialog = null
                        when (mode) {
                            "create" -> operation { store.createPack(value, newPackKind, newPackRating, newPackCategory); "Pack created" }
                            "rename" -> if (id != null) operation { store.renamePack(id, value); "Renamed" }
                            "category" -> if (id != null) operation { store.updateCategory(id, newPackCategory); "Category updated" }
                            "delete" -> if (id != null) operation { files.deletePack(id); "Deleted" }
                        }
                    }) { Text("Confirm") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } })
        }
    }
    private fun installPack(pack: StickerPack, packageName: String, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
        try {
            val intent = WhatsAppStickerProvider.installIntent(pack, packageName)
            if (intent.resolveActivity(packageManager) == null) {
                scope.launch { snackbar.showSnackbar("WhatsApp app not available or sticker import unsupported") }
            } else startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Unable to launch WhatsApp") }
        } catch (e: SecurityException) {
            scope.launch { snackbar.showSnackbar(e.message ?: "WhatsApp refused request") }
        }
    }
}
