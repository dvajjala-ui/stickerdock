package app.stickerdock.duo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PolishedHomeSection(val label: String, val mark: String) {
    LIBRARY("Library", "▦"),
    IMPORT("Import", "⇩"),
    FACTORY("Factory", "✦")
}

private data class LibrarySnapshot(
    val packs: List<StickerPack>,
    val stickers: List<StickerItem>,
    val counts: Map<String, Int>,
    val previews: Map<String, List<StickerItem>>
)

class StickerDockPolishedActivity : ComponentActivity() {
    private lateinit var store: StickerStore
    private lateinit var files: StickerFiles
    private lateinit var backup: StickerBackup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = StickerStore(applicationContext)
        files = StickerFiles(applicationContext, store)
        backup = StickerBackup(applicationContext, store, files)
        setContent { StickerDockTheme { StickerDockApp() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun StickerDockApp() {
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val previewCache = remember { StickerPreviewCache(64) }
        val pausePhoneImport = remember { AtomicBoolean(false) }

        var sectionName by rememberSaveable { mutableStateOf(PolishedHomeSection.LIBRARY.name) }
        val section = PolishedHomeSection.valueOf(sectionName)
        var packs by remember { mutableStateOf<List<StickerPack>>(emptyList()) }
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        var stickers by remember { mutableStateOf<List<StickerItem>>(emptyList()) }
        var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
        var previewItems by remember { mutableStateOf<Map<String, List<StickerItem>>>(emptyMap()) }

        var searchText by rememberSaveable { mutableStateOf("") }
        var stickerMatches by remember { mutableStateOf<List<StickerSearchResult>>(emptyList()) }
        var packFilter by rememberSaveable { mutableStateOf("All") }
        var packSort by rememberSaveable { mutableStateOf("Newest") }
        var categoryName by rememberSaveable { mutableStateOf("") }
        val categoryFilter = categoryName.takeIf { it.isNotBlank() }?.let(PackCategory::valueOf)
        var showFilters by remember { mutableStateOf(false) }

        var newPackKind by remember { mutableStateOf(StickerKind.STATIC) }
        var newPackRating by remember { mutableStateOf(PackRating.GENERAL) }
        var newPackCategory by remember { mutableStateOf(PackCategory.GENERAL) }
        var dialog by remember { mutableStateOf<String?>(null) }
        var name by remember { mutableStateOf("") }

        var busyLabel by remember { mutableStateOf<String?>(null) }
        val busy = busyLabel != null
        var phoneImportProgress by remember { mutableStateOf<FolderStickerImporter.Progress?>(null) }
        var phonePauseRequested by remember { mutableStateOf(false) }
        var pendingFolderImportUri by remember {
            mutableStateOf(FolderStickerImporter.pendingImportUri(this@StickerDockPolishedActivity)?.toString())
        }

        var showSavedLinks by remember { mutableStateOf(false) }
        var linkInput by remember { mutableStateOf("") }
        var savedLinks by remember { mutableStateOf<List<StickerlyLinks.Link>>(emptyList()) }

        var communityLoading by remember { mutableStateOf(false) }
        var communityPacks by remember { mutableStateOf<List<CommunityCatalog.Listing>>(emptyList()) }
        var communityError by remember { mutableStateOf<String?>(null) }
        var catalogImportedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

        var showPackOptions by remember { mutableStateOf(false) }
        var managingSticker by remember { mutableStateOf<StickerItem?>(null) }
        var editingSticker by remember { mutableStateOf<StickerItem?>(null) }
        var stickerLabel by remember { mutableStateOf("") }
        var stickerTags by remember { mutableStateOf("") }
        var transferItems by remember { mutableStateOf<List<StickerItem>?>(null) }
        var transferTarget by remember { mutableStateOf<String?>(null) }
        var selectedStickerIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        var bulkDeletePending by remember { mutableStateOf(false) }

        val pack = packs.firstOrNull { it.id == selectedId }
        val filteredPacks = packs.filter {
            val matchesSearch = searchText.isBlank() ||
                it.name.contains(searchText, ignoreCase = true) ||
                it.category.label.contains(searchText, ignoreCase = true)
            matchesSearch &&
                (packFilter == "All" || it.kind.name.equals(packFilter, ignoreCase = true)) &&
                (categoryFilter == null || it.category == categoryFilter)
        }.let { filtered ->
            when (packSort) {
                "Name" -> filtered.sortedBy { it.name.lowercase() }
                "Largest" -> filtered.sortedWith(compareByDescending<StickerPack> { counts[it.id] ?: 0 }.thenBy { it.name.lowercase() })
                else -> filtered.sortedByDescending { it.createdAt }
            }
        }
        val activeFilterCount = listOf(
            packFilter != "All",
            packSort != "Newest",
            categoryFilter != null
        ).count { it }

        fun clearFilters() {
            packFilter = "All"
            packSort = "Newest"
            categoryName = ""
        }

        fun refresh() {
            scope.launch {
                val snapshot = withContext(Dispatchers.IO) {
                    val all = store.listPacks()
                    LibrarySnapshot(
                        packs = all,
                        stickers = selectedId?.let(store::listStickers).orEmpty(),
                        counts = store.stickerCounts(),
                        previews = all.associate { it.id to store.listStickers(it.id).take(4) }
                    )
                }
                packs = snapshot.packs
                stickers = snapshot.stickers
                counts = snapshot.counts
                previewItems = snapshot.previews
                if (selectedId != null && packs.none { it.id == selectedId }) selectedId = null
                selectedStickerIds = selectedStickerIds.intersect(stickers.mapTo(mutableSetOf()) { it.id })
            }
        }

        fun operation(label: String, action: suspend () -> String) {
            if (busy) return
            busyLabel = label
            scope.launch {
                try {
                    val message = withContext(Dispatchers.IO) { action() }
                    refresh()
                    snackbar.showSnackbar(message)
                } catch (e: Exception) {
                    snackbar.showSnackbar(e.message ?: "Operation failed")
                } finally {
                    busyLabel = null
                }
            }
        }

        fun deleteStickerWithUndo(item: StickerItem) {
            if (busy) return
            managingSticker = null
            busyLabel = "Removing sticker"
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        val source = files.stickerFile(item.packId, item.fileName)
                        val snapshot = source.readBytes()
                        files.deleteSticker(item)
                        snapshot
                    }
                    refresh()
                    busyLabel = null
                    val result = snackbar.showSnackbar(
                        message = "Sticker removed",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        busyLabel = "Restoring sticker"
                        val restored = withContext(Dispatchers.IO) {
                            files.restoreBytes(item.packId, bytes, item.sha256, item.label, item.tags)
                        }
                        refresh()
                        snackbar.showSnackbar(if (restored == ImportResult.Added) "Sticker restored" else "Could not restore sticker")
                    }
                } catch (e: Exception) {
                    snackbar.showSnackbar(e.message ?: "Could not remove sticker")
                } finally {
                    busyLabel = null
                }
            }
        }

        fun refreshFactory() {
            if (communityLoading) return
            communityLoading = true
            communityError = null
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val catalog = CommunityCatalog(this@StickerDockPolishedActivity, backup).browse()
                        val imports = CatalogImports(this@StickerDockPolishedActivity, store)
                        catalog to catalog.filter {
                            imports.importedPackIds(it.id, it.sha256).isNotEmpty()
                        }.mapTo(mutableSetOf()) { it.id }
                    }
                    communityPacks = result.first
                    catalogImportedIds = result.second
                } catch (e: Exception) {
                    communityError = e.message ?: "Unable to load Factory packs"
                } finally {
                    communityLoading = false
                }
            }
        }

        fun importFactoryPack(entry: CommunityCatalog.Listing) {
            operation("Importing Factory pack") {
                val summary = CommunityCatalog(this@StickerDockPolishedActivity, backup).downloadAndImport(entry)
                val importedIds = CatalogImports(this@StickerDockPolishedActivity, store).importedPackIds(entry.id, entry.sha256)
                runOnUiThread {
                    catalogImportedIds = catalogImportedIds + entry.id
                    selectedId = importedIds.firstOrNull()
                    sectionName = PolishedHomeSection.LIBRARY.name
                }
                if (summary.stickers == 0) "Pack already in your library" else "Imported ${summary.stickers} stickers"
            }
        }

        fun runFolderImport(uri: android.net.Uri) {
            if (busy) return
            pendingFolderImportUri = uri.toString()
            phoneImportProgress = null
            phonePauseRequested = false
            pausePhoneImport.set(false)
            operation("Importing phone stickers") {
                try {
                    val report = FolderStickerImporter(this@StickerDockPolishedActivity, store, files).import(
                        uri,
                        shouldPause = { pausePhoneImport.get() || !scope.isActive },
                        onProgress = { progress -> runOnUiThread { phoneImportProgress = progress } }
                    )
                    buildString {
                        append(if (report.paused) "Phone import paused" else "Phone import complete")
                        append(": ${report.added} added, ${report.duplicates} duplicate, ${report.rejected} rejected")
                    }
                } finally {
                    runOnUiThread {
                        phoneImportProgress = null
                        phonePauseRequested = false
                        pendingFolderImportUri = FolderStickerImporter.pendingImportUri(this@StickerDockPolishedActivity)?.toString()
                    }
                }
            }
        }

        val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val id = selectedId ?: return@rememberLauncherForActivityResult
            if (uris.isNotEmpty()) operation("Importing stickers") {
                var added = 0
                var duplicates = 0
                var failed = 0
                uris.forEach { uri ->
                    when (files.importUri(id, uri)) {
                        ImportResult.Added -> added++
                        ImportResult.Duplicate -> duplicates++
                        is ImportResult.Failed -> failed++
                    }
                }
                "Imported $added • $duplicates duplicate • $failed failed"
            }
        }

        val folderImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) {}
                runFolderImport(uri)
            }
        }

        val zipImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) operation("Importing ZIPs") {
                require(uris.size <= 100) { "Select at most 100 ZIP files per batch" }
                var packsCreated = 0
                var added = 0
                var skipped = 0
                var failed = 0
                uris.forEach { uri ->
                    try {
                        val result = try {
                            val restored = backup.restore(uri)
                            Triple(restored.packs, restored.stickers, restored.rejected)
                        } catch (_: Exception) {
                            val imported = PackZipImporter(this@StickerDockPolishedActivity, store, files).import(uri)
                            Triple(imported.packs, imported.imported, imported.skipped + imported.duplicates)
                        }
                        packsCreated += result.first
                        added += result.second
                        skipped += result.third
                    } catch (_: Exception) { failed++ }
                }
                "${uris.size} ZIPs • $packsCreated packs • $added stickers • $skipped skipped • $failed failed"
            }
        }

        val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) operation("Creating backup") { backup.export(uri); "Backup saved" }
        }
        val loadBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) operation("Restoring backup") {
                val restored = backup.restore(uri)
                "Restored ${restored.packs} packs • ${restored.stickers} stickers • ${restored.rejected} skipped"
            }
        }

        LaunchedEffect(selectedId) {
            selectedStickerIds = emptySet()
            refresh()
        }
        LaunchedEffect(searchText) {
            val requested = searchText
            if (requested.isBlank()) stickerMatches = emptyList()
            else {
                delay(250)
                val matches = withContext(Dispatchers.IO) { store.searchStickers(requested) }
                if (searchText == requested) stickerMatches = matches
            }
        }
        LaunchedEffect(Unit) {
            refreshFactory()
            if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
                operation("Importing shared stickers") { IncomingStickers(this@StickerDockPolishedActivity, store, files).import(intent) }
            }
        }
        DisposableEffect(Unit) { onDispose { previewCache.clear() } }
        DisposableEffect(Unit) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refreshFactory() }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        BackHandler(enabled = selectedStickerIds.isNotEmpty() || selectedId != null) {
            if (selectedStickerIds.isNotEmpty()) selectedStickerIds = emptySet() else selectedId = null
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (pack == null) "StickerDock" else pack.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (pack == null) section.label else "${pack.category.label} • ${stickers.size} stickers",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (pack != null) TextButton(onClick = { selectedId = null }) { Text("Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                if (pack == null) {
                    BoxWithConstraints {
                        if (maxWidth < 720.dp) {
                            NavigationBar {
                                PolishedHomeSection.entries.forEach { destination ->
                                    NavigationBarItem(
                                        selected = section == destination,
                                        onClick = { sectionName = destination.name },
                                        icon = { Text(destination.mark, fontWeight = FontWeight.Bold) },
                                        label = { Text(destination.label) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                if (section == PolishedHomeSection.LIBRARY && pack == null) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            name = ""
                            newPackKind = StickerKind.STATIC
                            newPackRating = PackRating.GENERAL
                            newPackCategory = PackCategory.GENERAL
                            dialog = "create"
                        },
                        icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
                        text = { Text("New pack") },
                        expanded = !busy
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { scaffoldPadding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(scaffoldPadding)) {
                val wideTwoPane = maxWidth >= 1000.dp
                val rail = maxWidth >= 720.dp
                Column(Modifier.fillMaxSize()) {
                    if (busyLabel != null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            busyLabel!!,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(Modifier.weight(1f)) {
                        if (rail) {
                            NavigationRail {
                                Spacer(Modifier.height(12.dp))
                                PolishedHomeSection.entries.forEach { destination ->
                                    NavigationRailItem(
                                        selected = section == destination,
                                        onClick = {
                                            sectionName = destination.name
                                            if (destination != PolishedHomeSection.LIBRARY) selectedId = null
                                        },
                                        icon = { Text(destination.mark, fontWeight = FontWeight.Bold) },
                                        label = { Text(destination.label) }
                                    )
                                }
                            }
                            VerticalDivider()
                        }
                        Box(Modifier.weight(1f)) {
                            when {
                                section == PolishedHomeSection.LIBRARY && wideTwoPane -> {
                                    Row(Modifier.fillMaxSize()) {
                                        Box(Modifier.widthIn(min = 340.dp, max = 430.dp).fillMaxHeight()) {
                                            LibraryScreen(
                                                packs, counts, previewItems, filteredPacks, searchText, stickerMatches,
                                                activeFilterCount, selectedId, busy, files, previewCache, true,
                                                { searchText = it }, { showFilters = true }, ::clearFilters,
                                                {
                                                    name = ""; newPackKind = StickerKind.STATIC; newPackRating = PackRating.GENERAL
                                                    newPackCategory = PackCategory.GENERAL; dialog = "create"
                                                },
                                                { sectionName = PolishedHomeSection.IMPORT.name; folderImporter.launch(null) },
                                                { selectedId = it }
                                            )
                                        }
                                        VerticalDivider()
                                        Box(Modifier.weight(1f)) {
                                            if (pack == null) WideLibraryPlaceholder {
                                                name = ""; newPackKind = StickerKind.STATIC; newPackRating = PackRating.GENERAL
                                                newPackCategory = PackCategory.GENERAL; dialog = "create"
                                            }
                                            else PackDetailScreen(
                                                pack, stickers, busy, previewCache, files, selectedStickerIds,
                                                { stickerPicker.launch(arrayOf("image/*")) },
                                                { showPackOptions = true },
                                                {
                                                    scope.launch {
                                                        try {
                                                            val send = withContext(Dispatchers.IO) { PackSharing(this@StickerDockPolishedActivity, backup).share(pack) }
                                                            startActivity(Intent.createChooser(send, "Share sticker pack"))
                                                        } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Cannot share pack") }
                                                    }
                                                },
                                                { installPack(pack, "com.whatsapp", snackbar, scope) },
                                                { installPack(pack, "com.whatsapp.w4b", snackbar, scope) },
                                                { managingSticker = it },
                                                { item -> selectedStickerIds = if (item.id in selectedStickerIds) selectedStickerIds - item.id else selectedStickerIds + item.id },
                                                { item -> selectedStickerIds = selectedStickerIds + item.id },
                                                { selectedStickerIds = emptySet() },
                                                {
                                                    val chosen = stickers.filter { it.id in selectedStickerIds }
                                                    transferItems = chosen
                                                    transferTarget = packs.firstOrNull { it.id != pack.id && it.kind == pack.kind }?.id
                                                },
                                                { bulkDeletePending = true }
                                            )
                                        }
                                    }
                                }
                                section == PolishedHomeSection.LIBRARY && pack != null -> PackDetailScreen(
                                    pack, stickers, busy, previewCache, files, selectedStickerIds,
                                    { stickerPicker.launch(arrayOf("image/*")) }, { showPackOptions = true },
                                    {
                                        scope.launch {
                                            try {
                                                val send = withContext(Dispatchers.IO) { PackSharing(this@StickerDockPolishedActivity, backup).share(pack) }
                                                startActivity(Intent.createChooser(send, "Share sticker pack"))
                                            } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Cannot share pack") }
                                        }
                                    },
                                    { installPack(pack, "com.whatsapp", snackbar, scope) },
                                    { installPack(pack, "com.whatsapp.w4b", snackbar, scope) },
                                    { managingSticker = it },
                                    { item -> selectedStickerIds = if (item.id in selectedStickerIds) selectedStickerIds - item.id else selectedStickerIds + item.id },
                                    { item -> selectedStickerIds = selectedStickerIds + item.id },
                                    { selectedStickerIds = emptySet() },
                                    {
                                        transferItems = stickers.filter { it.id in selectedStickerIds }
                                        transferTarget = packs.firstOrNull { it.id != pack.id && it.kind == pack.kind }?.id
                                    },
                                    { bulkDeletePending = true }
                                )
                                section == PolishedHomeSection.LIBRARY -> AnimatedScreenSwap("library") {
                                    LibraryScreen(
                                        packs, counts, previewItems, filteredPacks, searchText, stickerMatches,
                                        activeFilterCount, selectedId, busy, files, previewCache, false,
                                        { searchText = it }, { showFilters = true }, ::clearFilters,
                                        {
                                            name = ""; newPackKind = StickerKind.STATIC; newPackRating = PackRating.GENERAL
                                            newPackCategory = PackCategory.GENERAL; dialog = "create"
                                        },
                                        { sectionName = PolishedHomeSection.IMPORT.name; folderImporter.launch(null) },
                                        { selectedId = it }
                                    )
                                }
                                section == PolishedHomeSection.IMPORT -> AnimatedScreenSwap("import") {
                                    ImportScreen(
                                        busy, phoneImportProgress, phonePauseRequested, pendingFolderImportUri,
                                        { phonePauseRequested = true; pausePhoneImport.set(true) },
                                        { runFolderImport(android.net.Uri.parse(it)) },
                                        {
                                            operation("Discarding saved import") {
                                                val removed = FolderStickerImporter(this@StickerDockPolishedActivity, store, files).discardPendingImport()
                                                runOnUiThread { pendingFolderImportUri = null; phoneImportProgress = null }
                                                "Discarded saved import${if (removed > 0) " • removed $removed empty packs" else ""}"
                                            }
                                        },
                                        { folderImporter.launch(null) },
                                        { zipImporter.launch(arrayOf("application/zip", "application/octet-stream")) },
                                        {
                                            showSavedLinks = true
                                            scope.launch { savedLinks = withContext(Dispatchers.IO) { StickerlyLinks(this@StickerDockPolishedActivity).list() } }
                                        },
                                        {
                                            scope.launch {
                                                try { withContext(Dispatchers.IO) { backup.estimate() }; saveBackup.launch("stickerdock.zip") }
                                                catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Cannot create backup") }
                                            }
                                        },
                                        { loadBackup.launch(arrayOf("application/zip")) }
                                    )
                                }
                                else -> AnimatedScreenSwap("factory") {
                                    FactoryScreen(communityLoading, communityPacks, communityError, catalogImportedIds, busy, ::refreshFactory, ::importFactoryPack)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showFilters) {
            LibraryFiltersSheet(
                packFilter, packSort, categoryFilter,
                { packFilter = it }, { packSort = it }, { categoryName = it?.name.orEmpty() },
                ::clearFilters, { showFilters = false }
            )
        }

        pack?.takeIf { showPackOptions }?.let { current ->
            PackOptionsSheet(
                current, busy,
                {
                    showPackOptions = false
                    name = current.name
                    dialog = "rename"
                },
                {
                    showPackOptions = false
                    newPackCategory = current.category
                    dialog = "category"
                },
                {
                    showPackOptions = false
                    dialog = "delete"
                },
                { showPackOptions = false }
            )
        }

        managingSticker?.let { item ->
            StickerManageSheet(
                item = item,
                kind = pack?.kind ?: StickerKind.STATIC,
                files = files,
                previewCache = previewCache,
                canTransfer = packs.any { it.id != item.packId && it.kind == pack?.kind },
                busy = busy,
                onEdit = {
                    managingSticker = null
                    editingSticker = item
                    stickerLabel = item.label
                    stickerTags = item.tags
                },
                onTransfer = {
                    managingSticker = null
                    transferItems = listOf(item)
                    transferTarget = packs.firstOrNull { it.id != item.packId && it.kind == pack?.kind }?.id
                },
                onDelete = { deleteStickerWithUndo(item) },
                onDismiss = { managingSticker = null }
            )
        }

        editingSticker?.let { item ->
            StickerEditSheet(
                item, stickerLabel, stickerTags, busy,
                { stickerLabel = it }, { stickerTags = it },
                {
                    val label = stickerLabel
                    val tags = stickerTags
                    editingSticker = null
                    operation("Saving sticker details") {
                        store.updateStickerMetadata(item.packId, item.id, label, tags)
                        "Sticker details updated"
                    }
                },
                { editingSticker = null }
            )
        }

        transferItems?.let { itemsToTransfer ->
            val sourcePack = itemsToTransfer.firstOrNull()?.let { source -> packs.firstOrNull { it.id == source.packId } }
            val candidates = if (sourcePack == null) emptyList() else packs.filter { it.id != sourcePack.id && it.kind == sourcePack.kind }
            StickerTransferSheet(
                title = if (itemsToTransfer.size == 1) "Copy or move sticker" else "Copy or move ${itemsToTransfer.size} stickers",
                packs = candidates,
                targetId = transferTarget,
                enabled = !busy,
                onTarget = { transferTarget = it },
                onCopy = {
                    val targetId = transferTarget ?: return@StickerTransferSheet
                    val batch = itemsToTransfer.toList()
                    transferItems = null
                    selectedStickerIds = emptySet()
                    operation("Copying stickers") { transferBatch(batch, targetId, false) }
                },
                onMove = {
                    val targetId = transferTarget ?: return@StickerTransferSheet
                    val batch = itemsToTransfer.toList()
                    transferItems = null
                    selectedStickerIds = emptySet()
                    operation("Moving stickers") { transferBatch(batch, targetId, true) }
                },
                onDismiss = { transferItems = null }
            )
        }

        if (bulkDeletePending) {
            AlertDialog(
                onDismissRequest = { bulkDeletePending = false },
                title = { Text("Delete ${selectedStickerIds.size} stickers?") },
                text = { Text("This removes the selected local sticker files. Bulk delete cannot be undone.") },
                confirmButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            val chosen = stickers.filter { it.id in selectedStickerIds }
                            bulkDeletePending = false
                            selectedStickerIds = emptySet()
                            operation("Deleting stickers") {
                                chosen.forEach(files::deleteSticker)
                                "Removed ${chosen.size} stickers"
                            }
                        }
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { bulkDeletePending = false }) { Text("Cancel") } }
            )
        }

        if (showSavedLinks) {
            SavedLinksDialog(
                linkInput, savedLinks, { linkInput = it },
                { link -> startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link.url))) },
                { link ->
                    scope.launch {
                        withContext(Dispatchers.IO) { StickerlyLinks(this@StickerDockPolishedActivity).remove(link.code) }
                        savedLinks = withContext(Dispatchers.IO) { StickerlyLinks(this@StickerDockPolishedActivity).list() }
                    }
                },
                {
                    val submitted = linkInput
                    scope.launch {
                        try {
                            val count = withContext(Dispatchers.IO) { StickerlyLinks(this@StickerDockPolishedActivity).saveFromText(submitted) }
                            savedLinks = withContext(Dispatchers.IO) { StickerlyLinks(this@StickerDockPolishedActivity).list() }
                            linkInput = ""
                            snackbar.showSnackbar("Saved $count new pack links")
                        } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Invalid pack links") }
                    }
                },
                { showSavedLinks = false }
            )
        }

        dialog?.let { mode ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = {
                    Text(when (mode) {
                        "create" -> "Create sticker pack"
                        "rename" -> "Rename pack"
                        "category" -> "Choose topic"
                        else -> "Delete pack?"
                    })
                },
                text = {
                    when (mode) {
                        "delete" -> Text("This permanently deletes this local pack and its sticker files.")
                        "category" -> LazyColumn(Modifier.heightIn(max = 380.dp)) {
                            items(PackCategory.entries) { category ->
                                FilterChip(
                                    selected = newPackCategory == category,
                                    onClick = { newPackCategory = category },
                                    label = { Text(category.label) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(name, { name = it }, label = { Text("Pack name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            if (mode == "create") {
                                Text("Sticker type", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StickerKind.entries.forEach { kind ->
                                        FilterChip(newPackKind == kind, { newPackKind = kind }, { Text(kind.name.lowercase().replaceFirstChar { it.uppercase() }) })
                                    }
                                }
                                Text("Content", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PackRating.entries.forEach { rating ->
                                        FilterChip(newPackRating == rating, { newPackRating = rating }, { Text(rating.name.lowercase().replaceFirstChar { it.uppercase() }) })
                                    }
                                }
                                Text("Topic", style = MaterialTheme.typography.labelLarge)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(PackCategory.entries) { category ->
                                        FilterChip(newPackCategory == category, { newPackCategory = category }, { Text(category.label) })
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !busy && (mode in setOf("delete", "category") || name.isNotBlank()),
                        onClick = {
                            val id = selectedId
                            val value = name
                            dialog = null
                            when (mode) {
                                "create" -> operation("Creating pack") {
                                    val created = store.createPack(value, newPackKind, newPackRating, newPackCategory)
                                    runOnUiThread { selectedId = created.id; sectionName = PolishedHomeSection.LIBRARY.name }
                                    "Pack created"
                                }
                                "rename" -> if (id != null) operation("Renaming pack") { store.renamePack(id, value); "Pack renamed" }
                                "category" -> if (id != null) operation("Updating topic") { store.updateCategory(id, newPackCategory); "Topic updated" }
                                "delete" -> if (id != null) operation("Deleting pack") {
                                    files.deletePack(id)
                                    runOnUiThread { selectedId = null; selectedStickerIds = emptySet() }
                                    "Pack deleted"
                                }
                            }
                        }
                    ) { Text(if (mode == "delete") "Delete" else if (mode == "create") "Create" else "Save") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
            )
        }
    }

    private fun transferBatch(items: List<StickerItem>, targetId: String, move: Boolean): String {
        var added = 0
        var duplicates = 0
        var failed = 0
        items.forEach { item ->
            when (files.transferSticker(item, targetId, move)) {
                ImportResult.Added -> added++
                ImportResult.Duplicate -> duplicates++
                is ImportResult.Failed -> failed++
            }
        }
        return "${if (move) "Moved" else "Copied"} $added • $duplicates duplicate • $failed failed"
    }

    private fun installPack(
        pack: StickerPack,
        packageName: String,
        snackbar: SnackbarHostState,
        scope: kotlinx.coroutines.CoroutineScope
    ) {
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
