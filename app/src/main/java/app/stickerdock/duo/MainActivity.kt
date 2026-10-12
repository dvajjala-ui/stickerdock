package app.stickerdock.duo

import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        var packs by remember { mutableStateOf<List<StickerPack>>(emptyList()) }
        var selectedId by remember { mutableStateOf<String?>(null) }
        var stickers by remember { mutableStateOf<List<StickerItem>>(emptyList()) }
        var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
        var query by remember { mutableStateOf("") }
        var packFilter by remember { mutableStateOf("All") }
        var newPackKind by remember { mutableStateOf(StickerKind.STATIC) }
        var newPackRating by remember { mutableStateOf(PackRating.GENERAL) }
        var busy by remember { mutableStateOf(false) }
        var dialog by remember { mutableStateOf<String?>(null) }
        var name by remember { mutableStateOf("") }
        var showCommunity by remember { mutableStateOf(false) }
        var showSavedLinks by remember { mutableStateOf(false) }
        var linkInput by remember { mutableStateOf("") }
        var savedLinks by remember { mutableStateOf<List<StickerlyLinks.Link>>(emptyList()) }
        var communityLoading by remember { mutableStateOf(false) }
        var communityPacks by remember { mutableStateOf<List<CommunityCatalog.Listing>>(emptyList()) }
        var communityError by remember { mutableStateOf<String?>(null) }
        var moving by remember { mutableStateOf<StickerItem?>(null) }
        var target by remember { mutableStateOf<String?>(null) }
        val pack = packs.firstOrNull { it.id == selectedId }

        fun refresh() {
            scope.launch {
                val data = withContext(Dispatchers.IO) {
                    val all = store.listPacks()
                    Triple(all, selectedId?.let(store::listStickers) ?: emptyList(),
                        all.associate { it.id to store.listStickers(it.id).size })
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
            if (uri != null) operation {
                val report = FolderStickerImporter(this@MainActivity, store, files).import(uri)
                "Phone import: ${report.added} stickers in ${report.packsCreated} packs, ${report.duplicates} duplicate, ${report.rejected} rejected"
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
        LaunchedEffect(Unit) {
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
                    Text("Your stickers, organized offline.")
                    Text("To migrate saved WhatsApp/Sticker.ly stickers, choose Android/media → com.whatsapp → WhatsApp → Media → WhatsApp Stickers (or the Business equivalent).")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { name = ""; newPackKind = StickerKind.STATIC; newPackRating = PackRating.GENERAL; dialog = "create" }) { Text("New pack") }
                        OutlinedButton(onClick = { saveBackup.launch("stickerdock.zip") }) { Text("Backup") }
                        OutlinedButton(onClick = { loadBackup.launch(arrayOf("application/zip")) }) { Text("Restore") }
                    }
                    if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    OutlinedButton(enabled = !busy, onClick = { folderImporter.launch(null) }) { Text("Import phone sticker folder") }
                    OutlinedButton(enabled = !busy, onClick = { zipImporter.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Import sticker ZIP") }
                    OutlinedButton(onClick = {
                        showSavedLinks = true
                        scope.launch { savedLinks = withContext(Dispatchers.IO) { StickerlyLinks(this@MainActivity).list() } }
                    }) { Text("Saved Sticker.ly pack links") }
                    OutlinedButton(onClick = {
                        showCommunity = true
                        communityLoading = true
                        communityError = null
                        scope.launch {
                            try { communityPacks = withContext(Dispatchers.IO) { CommunityCatalog(this@MainActivity, backup).browse() } }
                            catch (e: Exception) { communityError = e.message ?: "Unable to load catalog" }
                            finally { communityLoading = false }
                        }
                    }) { Text("Community packs") }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All", "Static", "Animated").forEach { type ->
                            FilterChip(selected = packFilter == type, onClick = { packFilter = type }, label = { Text(type) })
                        }
                    }
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        label = { Text("Search packs") }, modifier = Modifier.fillMaxWidth())
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(packs.filter { it.name.contains(query, true) && (packFilter == "All" || it.kind.name.equals(packFilter, ignoreCase = true)) }, key = { it.id }) { item ->
                            Card(Modifier.fillMaxWidth().clickable { selectedId = item.id }) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.name + if (item.kind == StickerKind.ANIMATED) " (Animated)" else " (Static)", modifier = Modifier.weight(1f))
                                    Text("${counts[item.id] ?: 0} ${if (item.rating == PackRating.MATURE) "Mature" else "stickers"}")
                                }
                            }
                        }
                    }
                } else {
                    Text("${pack.kind.name.lowercase().replaceFirstChar { it.uppercase() }} pack • ${pack.rating.name.lowercase().replaceFirstChar { it.uppercase() }} • ${stickers.size}/30 stickers")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(enabled = !busy && stickers.size < 30, onClick = { picker.launch(arrayOf("image/*")) }) { Text("Import") }
                        OutlinedButton(onClick = { name = pack.name; dialog = "rename" }) { Text("Rename") }
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
                            val image = remember(path) { if (pack.kind == StickerKind.STATIC) BitmapFactory.decodeFile(path)?.asImageBitmap() else null }
                            val animation = remember(path) {
                                if (pack.kind == StickerKind.ANIMATED) try {
                                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path)))
                                } catch (_: Exception) { null } else null
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
                                        else Image(image, contentDescription = "Sticker", modifier = Modifier.fillMaxSize())
                                    }
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
            title = { Text("Community packs") },
            text = {
                Column {
                    Text("Only moderator-approved general-audience packs are shown.")
                    if (communityLoading) CircularProgressIndicator()
                    else if (communityError != null) Text(communityError ?: "")
                    else if (communityPacks.isEmpty()) Text("No approved public packs yet. Contributors can submit via GitHub pull request.")
                    else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(communityPacks, key = { it.id }) { entry ->
                            TextButton(enabled = !busy, onClick = {
                                showCommunity = false
                                operation {
                                    val summary = CommunityCatalog(this@MainActivity, backup).downloadAndImport(entry)
                                    "Imported ${summary.packs} packs and ${summary.stickers} stickers"
                                }
                            }) { Text("${entry.name} — ${entry.creator} (import)") }
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
        dialog?.let { mode ->
            AlertDialog(onDismissRequest = { dialog = null },
                title = { Text(if (mode == "create") "New pack" else if (mode == "rename") "Rename pack" else "Delete pack?") },
                text = {
                    if (mode == "delete") Text("Local stickers and data will be deleted.")
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
                    }
                },
                confirmButton = {
                    TextButton(enabled = !busy && (mode == "delete" || name.isNotBlank()), onClick = {
                        val id = selectedId
                        val value = name
                        dialog = null
                        when (mode) {
                            "create" -> operation { store.createPack(value, newPackKind, newPackRating); "Pack created" }
                            "rename" -> if (id != null) operation { store.renamePack(id, value); "Renamed" }
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
