package app.stickerdock.duo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private enum class HomeSection(val label: String, val mark: String) {
    LIBRARY("Library", "▦"),
    IMPORT("Import", "⇩"),
    FACTORY("Factory", "✦")
}

class StickerDockActivity : ComponentActivity() {
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
        val previewCache = remember { StickerPreviewCache() }
        val pausePhoneImport = remember { AtomicBoolean(false) }

        var sectionName by rememberSaveable { mutableStateOf(HomeSection.LIBRARY.name) }
        val section = HomeSection.valueOf(sectionName)
        var packs by remember { mutableStateOf<List<StickerPack>>(emptyList()) }
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        var stickers by remember { mutableStateOf<List<StickerItem>>(emptyList()) }
        var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

        var query by rememberSaveable { mutableStateOf("") }
        var stickerQuery by rememberSaveable { mutableStateOf("") }
        var stickerMatches by remember { mutableStateOf<List<StickerSearchResult>>(emptyList()) }
        var packFilter by rememberSaveable { mutableStateOf("All") }
        var packSort by rememberSaveable { mutableStateOf("Newest") }
        var categoryFilter by remember { mutableStateOf<PackCategory?>(null) }

        var newPackKind by remember { mutableStateOf(StickerKind.STATIC) }
        var newPackRating by remember { mutableStateOf(PackRating.GENERAL) }
        var newPackCategory by remember { mutableStateOf(PackCategory.GENERAL) }
        var dialog by remember { mutableStateOf<String?>(null) }
        var name by remember { mutableStateOf("") }

        var busy by remember { mutableStateOf(false) }
        var phoneImportProgress by remember { mutableStateOf<FolderStickerImporter.Progress?>(null) }
        var phonePauseRequested by remember { mutableStateOf(false) }
        var pendingFolderImportUri by remember {
            mutableStateOf(FolderStickerImporter.pendingImportUri(this@StickerDockActivity)?.toString())
        }

        var showSavedLinks by remember { mutableStateOf(false) }
        var linkInput by remember { mutableStateOf("") }
        var savedLinks by remember { mutableStateOf<List<StickerlyLinks.Link>>(emptyList()) }

        var communityLoading by remember { mutableStateOf(false) }
        var communityPacks by remember { mutableStateOf<List<CommunityCatalog.Listing>>(emptyList()) }
        var communityError by remember { mutableStateOf<String?>(null) }
        var catalogImportedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

        var managingSticker by remember { mutableStateOf<StickerItem?>(null) }
        var editingSticker by remember { mutableStateOf<StickerItem?>(null) }
        var stickerLabel by remember { mutableStateOf("") }
        var stickerTags by remember { mutableStateOf("") }
        var moving by remember { mutableStateOf<StickerItem?>(null) }
        var target by remember { mutableStateOf<String?>(null) }

        val pack = packs.firstOrNull { it.id == selectedId }
        val filteredPacks = packs.filter {
            it.name.contains(query, ignoreCase = true) &&
                (packFilter == "All" || it.kind.name.equals(packFilter, ignoreCase = true)) &&
                (categoryFilter == null || it.category == categoryFilter)
        }.let { filtered ->
            when (packSort) {
                "Name" -> filtered.sortedBy { it.name.lowercase() }
                "Largest" -> filtered.sortedWith(
                    compareByDescending<StickerPack> { counts[it.id] ?: 0 }
                        .thenBy { it.name.lowercase() }
                )
                else -> filtered.sortedByDescending { it.createdAt }
            }
        }

        fun refresh() {
            scope.launch {
                val data = withContext(Dispatchers.IO) {
                    val all = store.listPacks()
                    Triple(
                        all,
                        selectedId?.let(store::listStickers) ?: emptyList(),
                        store.stickerCounts()
                    )
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
                } finally {
                    busy = false
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
                        val catalog = CommunityCatalog(this@StickerDockActivity, backup).browse()
                        val imports = CatalogImports(this@StickerDockActivity, store)
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
            operation {
                val summary = CommunityCatalog(this@StickerDockActivity, backup).downloadAndImport(entry)
                val importedIds = CatalogImports(this@StickerDockActivity, store)
                    .importedPackIds(entry.id, entry.sha256)
                runOnUiThread {
                    catalogImportedIds = catalogImportedIds + entry.id
                    selectedId = importedIds.firstOrNull()
                    sectionName = HomeSection.LIBRARY.name
                }
                if (summary.stickers == 0) "Pack already in your library"
                else "Imported ${summary.stickers} stickers. Choose WhatsApp or Business when ready."
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
                    val report = FolderStickerImporter(this@StickerDockActivity, store, files).import(
                        uri,
                        shouldPause = { pausePhoneImport.get() || !scope.isActive },
                        onProgress = { progress -> runOnUiThread { phoneImportProgress = progress } }
                    )
                    buildString {
                        append(if (report.paused) "Phone import paused" else "Phone import complete")
                        append(": ${report.added} stickers in ${report.packsCreated} packs, ")
                        append("${report.duplicates} duplicate, ${report.rejected} rejected")
                        if (report.skippedDirectories > 0) {
                            append(", ${report.skippedDirectories} folders skipped at depth limit")
                        }
                    }
                } finally {
                    runOnUiThread {
                        phoneImportProgress = null
                        phonePauseRequested = false
                        pendingFolderImportUri = FolderStickerImporter
                            .pendingImportUri(this@StickerDockActivity)?.toString()
                    }
                }
            }
        }

        val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val id = selectedId ?: return@rememberLauncherForActivityResult
            if (uris.isNotEmpty()) operation {
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
                "Imported $added, duplicate $duplicates, failed $failed"
            }
        }

        val folderImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // Temporary access still allows the current import. Resume surfaces provider errors.
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
                            val restored = backup.restore(uri)
                            Triple(restored.packs, restored.stickers, restored.rejected)
                        } catch (_: Exception) {
                            val imported = PackZipImporter(this@StickerDockActivity, store, files).import(uri)
                            Triple(imported.packs, imported.imported, imported.skipped + imported.duplicates)
                        }
                        packsCreated += count.first
                        added += count.second
                        skipped += count.third
                    } catch (_: Exception) {
                        failed++
                    }
                }
                "Imported ${uris.size} ZIPs: $packsCreated packs, $added stickers, $skipped skipped, $failed failed"
            }
        }

        val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) operation {
                backup.export(uri)
                "Backup saved"
            }
        }

        val loadBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) operation {
                val restored = backup.restore(uri)
                "Restored ${restored.packs} packs, ${restored.stickers} stickers, ${restored.rejected} skipped"
            }
        }

        LaunchedEffect(selectedId) { refresh() }
        LaunchedEffect(stickerQuery) {
            val requested = stickerQuery
            if (requested.isBlank()) {
                stickerMatches = emptyList()
            } else {
                delay(300)
                val matches = withContext(Dispatchers.IO) { store.searchStickers(requested) }
                if (stickerQuery == requested) stickerMatches = matches
            }
        }
        LaunchedEffect(Unit) {
            refreshFactory()
            if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
                operation { IncomingStickers(this@StickerDockActivity, store, files).import(intent) }
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
        BackHandler(enabled = selectedId != null) { selectedId = null }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (pack == null) "StickerDock" else "Pack details")
                            Text(
                                if (pack == null) section.label else pack.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (pack != null) {
                            TextButton(onClick = { selectedId = null }) { Text("Back") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                if (pack == null) {
                    BoxWithConstraints {
                        if (maxWidth < 720.dp) {
                            NavigationBar {
                                HomeSection.entries.forEach { destination ->
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
            snackbarHost = { SnackbarHost(snackbar) }
        ) { scaffoldPadding ->
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding)
            ) {
                if (pack != null) {
                    PackDetailScreen(
                        pack = pack,
                        stickers = stickers,
                        packs = packs,
                        busy = busy,
                        previewCache = previewCache,
                        onImport = { stickerPicker.launch(arrayOf("image/*")) },
                        onRename = {
                            name = pack.name
                            dialog = "rename"
                        },
                        onCategory = {
                            newPackCategory = pack.category
                            dialog = "category"
                        },
                        onShare = {
                            scope.launch {
                                try {
                                    val send = withContext(Dispatchers.IO) {
                                        PackSharing(this@StickerDockActivity, backup).share(pack)
                                    }
                                    startActivity(Intent.createChooser(send, "Share sticker pack"))
                                } catch (e: Exception) {
                                    snackbar.showSnackbar(e.message ?: "Cannot share pack")
                                }
                            }
                        },
                        onWhatsApp = { installPack(pack, "com.whatsapp", snackbar, scope) },
                        onBusiness = { installPack(pack, "com.whatsapp.w4b", snackbar, scope) },
                        onDeletePack = { dialog = "delete" },
                        onManageSticker = { managingSticker = it }
                    )
                } else if (maxWidth >= 720.dp) {
                    Row(Modifier.fillMaxSize()) {
                        NavigationRail {
                            Spacer(Modifier.height(12.dp))
                            HomeSection.entries.forEach { destination ->
                                NavigationRailItem(
                                    selected = section == destination,
                                    onClick = { sectionName = destination.name },
                                    icon = { Text(destination.mark, fontWeight = FontWeight.Bold) },
                                    label = { Text(destination.label) }
                                )
                            }
                        }
                        VerticalDivider()
                        Box(Modifier.weight(1f)) {
                            when (section) {
                                HomeSection.LIBRARY -> LibraryScreen(
                                    packs = packs,
                                    counts = counts,
                                    filteredPacks = filteredPacks,
                                    query = query,
                                    stickerQuery = stickerQuery,
                                    stickerMatches = stickerMatches,
                                    packFilter = packFilter,
                                    packSort = packSort,
                                    categoryFilter = categoryFilter,
                                    busy = busy,
                                    onCreatePack = {
                                        name = ""
                                        newPackKind = StickerKind.STATIC
                                        newPackRating = PackRating.GENERAL
                                        newPackCategory = PackCategory.GENERAL
                                        dialog = "create"
                                    },
                                    onOpenPack = { selectedId = it },
                                    onQueryChange = { query = it },
                                    onStickerQueryChange = { stickerQuery = it },
                                    onPackFilterChange = { packFilter = it },
                                    onPackSortChange = { packSort = it },
                                    onCategoryChange = { categoryFilter = it },
                                    onImportPhone = {
                                        sectionName = HomeSection.IMPORT.name
                                        folderImporter.launch(null)
                                    }
                                )
                                HomeSection.IMPORT -> ImportScreen(
                                    busy = busy,
                                    progress = phoneImportProgress,
                                    pauseRequested = phonePauseRequested,
                                    pendingUri = pendingFolderImportUri,
                                    onPause = {
                                        phonePauseRequested = true
                                        pausePhoneImport.set(true)
                                    },
                                    onResume = { saved -> runFolderImport(android.net.Uri.parse(saved)) },
                                    onDiscard = {
                                        operation {
                                            val removed = FolderStickerImporter(this@StickerDockActivity, store, files)
                                                .discardPendingImport()
                                            runOnUiThread {
                                                pendingFolderImportUri = null
                                                phoneImportProgress = null
                                            }
                                            "Discarded saved import${if (removed > 0) " and removed $removed empty packs" else ""}"
                                        }
                                    },
                                    onPhone = { folderImporter.launch(null) },
                                    onZip = { zipImporter.launch(arrayOf("application/zip", "application/octet-stream")) },
                                    onLinks = {
                                        showSavedLinks = true
                                        scope.launch {
                                            savedLinks = withContext(Dispatchers.IO) {
                                                StickerlyLinks(this@StickerDockActivity).list()
                                            }
                                        }
                                    },
                                    onBackup = {
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) { backup.estimate() }
                                                saveBackup.launch("stickerdock.zip")
                                            } catch (e: Exception) {
                                                snackbar.showSnackbar(e.message ?: "Cannot create backup")
                                            }
                                        }
                                    },
                                    onRestore = { loadBackup.launch(arrayOf("application/zip")) }
                                )
                                HomeSection.FACTORY -> FactoryScreen(
                                    loading = communityLoading,
                                    packs = communityPacks,
                                    error = communityError,
                                    importedIds = catalogImportedIds,
                                    busy = busy,
                                    onRefresh = { refreshFactory() },
                                    onPack = { importFactoryPack(it) }
                                )
                            }
                        }
                    }
                } else {
                    when (section) {
                        HomeSection.LIBRARY -> LibraryScreen(
                            packs = packs,
                            counts = counts,
                            filteredPacks = filteredPacks,
                            query = query,
                            stickerQuery = stickerQuery,
                            stickerMatches = stickerMatches,
                            packFilter = packFilter,
                            packSort = packSort,
                            categoryFilter = categoryFilter,
                            busy = busy,
                            onCreatePack = {
                                name = ""
                                newPackKind = StickerKind.STATIC
                                newPackRating = PackRating.GENERAL
                                newPackCategory = PackCategory.GENERAL
                                dialog = "create"
                            },
                            onOpenPack = { selectedId = it },
                            onQueryChange = { query = it },
                            onStickerQueryChange = { stickerQuery = it },
                            onPackFilterChange = { packFilter = it },
                            onPackSortChange = { packSort = it },
                            onCategoryChange = { categoryFilter = it },
                            onImportPhone = {
                                sectionName = HomeSection.IMPORT.name
                                folderImporter.launch(null)
                            }
                        )
                        HomeSection.IMPORT -> ImportScreen(
                            busy = busy,
                            progress = phoneImportProgress,
                            pauseRequested = phonePauseRequested,
                            pendingUri = pendingFolderImportUri,
                            onPause = {
                                phonePauseRequested = true
                                pausePhoneImport.set(true)
                            },
                            onResume = { saved -> runFolderImport(android.net.Uri.parse(saved)) },
                            onDiscard = {
                                operation {
                                    val removed = FolderStickerImporter(this@StickerDockActivity, store, files)
                                        .discardPendingImport()
                                    runOnUiThread {
                                        pendingFolderImportUri = null
                                        phoneImportProgress = null
                                    }
                                    "Discarded saved import${if (removed > 0) " and removed $removed empty packs" else ""}"
                                }
                            },
                            onPhone = { folderImporter.launch(null) },
                            onZip = { zipImporter.launch(arrayOf("application/zip", "application/octet-stream")) },
                            onLinks = {
                                showSavedLinks = true
                                scope.launch {
                                    savedLinks = withContext(Dispatchers.IO) {
                                        StickerlyLinks(this@StickerDockActivity).list()
                                    }
                                }
                            },
                            onBackup = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { backup.estimate() }
                                        saveBackup.launch("stickerdock.zip")
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar(e.message ?: "Cannot create backup")
                                    }
                                }
                            },
                            onRestore = { loadBackup.launch(arrayOf("application/zip")) }
                        )
                        HomeSection.FACTORY -> FactoryScreen(
                            loading = communityLoading,
                            packs = communityPacks,
                            error = communityError,
                            importedIds = catalogImportedIds,
                            busy = busy,
                            onRefresh = { refreshFactory() },
                            onPack = { importFactoryPack(it) }
                        )
                    }
                }
            }
        }

        if (showSavedLinks) {
            SavedLinksDialog(
                input = linkInput,
                links = savedLinks,
                onInput = { linkInput = it },
                onOpen = { link ->
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link.url)))
                },
                onRemove = { link ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            StickerlyLinks(this@StickerDockActivity).remove(link.code)
                        }
                        savedLinks = withContext(Dispatchers.IO) {
                            StickerlyLinks(this@StickerDockActivity).list()
                        }
                    }
                },
                onSave = {
                    val submitted = linkInput
                    scope.launch {
                        try {
                            val count = withContext(Dispatchers.IO) {
                                StickerlyLinks(this@StickerDockActivity).saveFromText(submitted)
                            }
                            savedLinks = withContext(Dispatchers.IO) {
                                StickerlyLinks(this@StickerDockActivity).list()
                            }
                            linkInput = ""
                            snackbar.showSnackbar("Saved $count new pack links")
                        } catch (e: Exception) {
                            snackbar.showSnackbar(e.message ?: "Invalid pack links")
                        }
                    }
                },
                onDismiss = { showSavedLinks = false }
            )
        }

        managingSticker?.let { item ->
            AlertDialog(
                onDismissRequest = { managingSticker = null },
                title = { Text(item.displayName) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Manage this sticker without crowding the grid with actions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                managingSticker = null
                                editingSticker = item
                                stickerLabel = item.label
                                stickerTags = item.tags
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Edit label and tags") }
                        TextButton(
                            enabled = packs.size > 1 && !busy,
                            onClick = {
                                managingSticker = null
                                moving = item
                                target = packs.firstOrNull { it.id != item.packId }?.id
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Copy or move to another pack") }
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                managingSticker = null
                                operation {
                                    files.deleteSticker(item)
                                    "Sticker removed"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Remove sticker", color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { managingSticker = null }) { Text("Close") }
                }
            )
        }

        editingSticker?.let { item ->
            AlertDialog(
                onDismissRequest = { editingSticker = null },
                title = { Text("Sticker info") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = stickerLabel,
                            onValueChange = { stickerLabel = it },
                            label = { Text("Label") },
                            supportingText = { Text("Readable name used in display and search") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = stickerTags,
                            onValueChange = { stickerTags = it },
                            label = { Text("Tags") },
                            supportingText = { Text("Comma-separated, e.g. funny, work, reaction") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Stored file: ${item.fileName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            val label = stickerLabel
                            val tags = stickerTags
                            editingSticker = null
                            operation {
                                store.updateStickerMetadata(item.packId, item.id, label, tags)
                                "Sticker info updated"
                            }
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { editingSticker = null }) { Text("Cancel") }
                }
            )
        }

        moving?.let { item ->
            AlertDialog(
                onDismissRequest = { moving = null },
                title = { Text("Copy or move sticker") },
                text = {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(packs.filter { it.id != item.packId }, key = { it.id }) { candidate ->
                            FilterChip(
                                selected = target == candidate.id,
                                onClick = { target = candidate.id },
                                label = { Text(candidate.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(
                            enabled = target != null && !busy,
                            onClick = {
                                val destination = target
                                moving = null
                                if (destination != null) operation {
                                    when (val result = files.transferSticker(item, destination, false)) {
                                        ImportResult.Added -> "Copy complete"
                                        ImportResult.Duplicate -> "Duplicate in target"
                                        is ImportResult.Failed -> result.reason
                                    }
                                }
                            }
                        ) { Text("Copy") }
                        TextButton(
                            enabled = target != null && !busy,
                            onClick = {
                                val destination = target
                                moving = null
                                if (destination != null) operation {
                                    when (val result = files.transferSticker(item, destination, true)) {
                                        ImportResult.Added -> "Move complete"
                                        ImportResult.Duplicate -> "Duplicate in target"
                                        is ImportResult.Failed -> result.reason
                                    }
                                }
                            }
                        ) { Text("Move") }
                    }
                },
                dismissButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } }
            )
        }

        dialog?.let { mode ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = {
                    Text(
                        when (mode) {
                            "create" -> "Create sticker pack"
                            "rename" -> "Rename pack"
                            "category" -> "Choose topic"
                            else -> "Delete pack?"
                        }
                    )
                },
                text = {
                    when (mode) {
                        "delete" -> Text("This permanently deletes this local pack and its sticker files.")
                        "category" -> LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                            items(PackCategory.entries.size) { index ->
                                val category = PackCategory.entries[index]
                                FilterChip(
                                    selected = newPackCategory == category,
                                    onClick = { newPackCategory = category },
                                    label = { Text(category.label) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Pack name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (mode == "create") {
                                Text("Sticker type", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StickerKind.entries.forEach { kind ->
                                        FilterChip(
                                            selected = newPackKind == kind,
                                            onClick = { newPackKind = kind },
                                            label = {
                                                Text(kind.name.lowercase().replaceFirstChar { it.uppercase() })
                                            }
                                        )
                                    }
                                }
                                Text("Content", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PackRating.entries.forEach { rating ->
                                        FilterChip(
                                            selected = newPackRating == rating,
                                            onClick = { newPackRating = rating },
                                            label = {
                                                Text(rating.name.lowercase().replaceFirstChar { it.uppercase() })
                                            }
                                        )
                                    }
                                }
                                Text("Topic", style = MaterialTheme.typography.labelLarge)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(PackCategory.entries.size) { index ->
                                        val category = PackCategory.entries[index]
                                        FilterChip(
                                            selected = newPackCategory == category,
                                            onClick = { newPackCategory = category },
                                            label = { Text(category.label) }
                                        )
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
                                "create" -> operation {
                                    val created = store.createPack(value, newPackKind, newPackRating, newPackCategory)
                                    runOnUiThread {
                                        selectedId = created.id
                                        sectionName = HomeSection.LIBRARY.name
                                    }
                                    "Pack created"
                                }
                                "rename" -> if (id != null) operation {
                                    store.renamePack(id, value)
                                    "Pack renamed"
                                }
                                "category" -> if (id != null) operation {
                                    store.updateCategory(id, newPackCategory)
                                    "Topic updated"
                                }
                                "delete" -> if (id != null) operation {
                                    files.deletePack(id)
                                    "Pack deleted"
                                }
                            }
                        }
                    ) { Text(if (mode == "delete") "Delete" else "Confirm") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
            )
        }
    }

    @Composable
    private fun LibraryScreen(
        packs: List<StickerPack>,
        counts: Map<String, Int>,
        filteredPacks: List<StickerPack>,
        query: String,
        stickerQuery: String,
        stickerMatches: List<StickerSearchResult>,
        packFilter: String,
        packSort: String,
        categoryFilter: PackCategory?,
        busy: Boolean,
        onCreatePack: () -> Unit,
        onOpenPack: (String) -> Unit,
        onQueryChange: (String) -> Unit,
        onStickerQueryChange: (String) -> Unit,
        onPackFilterChange: (String) -> Unit,
        onPackSortChange: (String) -> Unit,
        onCategoryChange: (PackCategory?) -> Unit,
        onImportPhone: () -> Unit
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 960.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    StickerDockHero(
                        packCount = packs.size,
                        stickerCount = counts.values.sum(),
                        busy = busy,
                        onCreatePack = onCreatePack,
                        onImportPhone = onImportPhone
                    )
                }
                item {
                    StickerDockSectionHeader(
                        "Find anything",
                        "Search every sticker first, or filter the pack list below."
                    )
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = stickerQuery,
                                onValueChange = onStickerQueryChange,
                                label = { Text("Search every sticker") },
                                placeholder = { Text("Label, tag, filename or pack") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (stickerQuery.isBlank()) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = onQueryChange,
                                    label = { Text("Filter packs by name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(listOf("All", "Static", "Animated")) { type ->
                                        FilterChip(
                                            selected = packFilter == type,
                                            onClick = { onPackFilterChange(type) },
                                            label = { Text(type) }
                                        )
                                    }
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(listOf("Newest", "Name", "Largest")) { sort ->
                                        FilterChip(
                                            selected = packSort == sort,
                                            onClick = { onPackSortChange(sort) },
                                            label = { Text(sort) }
                                        )
                                    }
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item {
                                        FilterChip(
                                            selected = categoryFilter == null,
                                            onClick = { onCategoryChange(null) },
                                            label = { Text("All topics") }
                                        )
                                    }
                                    items(PackCategory.entries.size) { index ->
                                        val category = PackCategory.entries[index]
                                        FilterChip(
                                            selected = categoryFilter == category,
                                            onClick = { onCategoryChange(category) },
                                            label = { Text(category.label) }
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    "${stickerMatches.size} results",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item {
                    StickerDockSectionHeader(
                        if (stickerQuery.isBlank()) "Your packs" else "Sticker results",
                        if (stickerQuery.isBlank()) "${filteredPacks.size} shown • ${packs.size} total" else null
                    )
                }
                if (stickerQuery.isBlank()) {
                    if (filteredPacks.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = if (packs.isEmpty()) "No packs yet" else "No packs match",
                                body = if (packs.isEmpty())
                                    "Create a pack or import the stickers already stored on your phone."
                                else "Clear a filter, topic or pack-name search and try again.",
                                actionLabel = if (packs.isEmpty()) "Create first pack" else null,
                                onAction = if (packs.isEmpty()) onCreatePack else null
                            )
                        }
                    } else {
                        items(filteredPacks, key = { it.id }) { item ->
                            PackListCard(item, counts[item.id] ?: 0) { onOpenPack(item.id) }
                        }
                    }
                } else {
                    if (stickerMatches.isEmpty()) {
                        item {
                            EmptyStateCard(
                                "No sticker matches",
                                "Try a shorter label, another tag, filename, pack name or topic."
                            )
                        }
                    } else {
                        items(stickerMatches, key = { it.item.id }) { result ->
                            StickerSearchResultCard(result) { onOpenPack(result.pack.id) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ImportScreen(
        busy: Boolean,
        progress: FolderStickerImporter.Progress?,
        pauseRequested: Boolean,
        pendingUri: String?,
        onPause: () -> Unit,
        onResume: (String) -> Unit,
        onDiscard: () -> Unit,
        onPhone: () -> Unit,
        onZip: () -> Unit,
        onLinks: () -> Unit,
        onBackup: () -> Unit,
        onRestore: () -> Unit
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 960.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Bring your stickers together",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Import only files and folders you can access. StickerDock keeps the resulting library local unless you explicitly share it.",
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                progress?.let {
                    item { ImportProgressCard(it, pauseRequested, onPause) }
                }
                pendingUri?.takeIf { !busy }?.let { saved ->
                    item { PendingImportCard(onResume = { onResume(saved) }, onDiscard = onDiscard) }
                }
                if (busy && progress == null) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                item { StickerDockSectionHeader("Import sources", "Choose the safest source for your collection.") }
                item {
                    StickerDockActionGrid(
                        listOf(
                            StickerDockAction(
                                "Phone sticker folder",
                                "Scan a WhatsApp or WhatsApp Business media folder",
                                enabled = !busy,
                                primary = true,
                                onClick = onPhone
                            ),
                            StickerDockAction(
                                "Sticker ZIPs",
                                "StickerDock backups, .wastickers or generic ZIP batches",
                                enabled = !busy,
                                onClick = onZip
                            ),
                            StickerDockAction(
                                "Sticker.ly links",
                                "Save public pack bookmarks without scraping creator files",
                                enabled = !busy,
                                onClick = onLinks
                            )
                        )
                    )
                }
                item { StickerDockSectionHeader("Library safety", "Portable backup and restore for your own StickerDock data.") }
                item {
                    StickerDockActionGrid(
                        listOf(
                            StickerDockAction(
                                "Back up library",
                                "Export packs, metadata and sticker bytes to a ZIP",
                                enabled = !busy,
                                onClick = onBackup
                            ),
                            StickerDockAction(
                                "Restore backup",
                                "Import a StickerDock backup without deleting existing packs",
                                enabled = !busy,
                                onClick = onRestore
                            )
                        )
                    )
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Finding the WhatsApp folder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Personal: Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Business: Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Stickers",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Folder names vary by Android/WhatsApp version. The system picker controls what StickerDock can read.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FactoryScreen(
        loading: Boolean,
        packs: List<CommunityCatalog.Listing>,
        error: String?,
        importedIds: Set<String>,
        busy: Boolean,
        onRefresh: () -> Unit,
        onPack: (CommunityCatalog.Listing) -> Unit
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 960.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "From the Factory",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        "Reviewed general-audience sticker packs",
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                TextButton(enabled = !loading, onClick = onRefresh) { Text("Refresh") }
                            }
                            Text(
                                "Factory output stays private until reviewed. Only published GENERAL packs appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (error != null) {
                    item {
                        EmptyStateCard(
                            "Factory unavailable",
                            "Your local stickers still work offline. Refresh again when the network is available.",
                            "Try again",
                            onRefresh
                        )
                    }
                } else if (!loading && packs.isEmpty()) {
                    item {
                        EmptyStateCard(
                            "No reviewed packs yet",
                            "New Factory packs will appear after review and publication."
                        )
                    }
                } else {
                    items(packs, key = { it.id }) { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    entry.creator,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (entry.categories.isNotEmpty()) {
                                    Text(
                                        entry.categories.joinToString(" • ") { it.label },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                FilledTonalButton(
                                    enabled = !busy,
                                    onClick = { onPack(entry) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (entry.id in importedIds) "Open in library" else "Import pack")
                                }
                            }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("AI meme creator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "The private Creator Studio workflow exists, but the Android entry remains intentionally unavailable until hosted exposure is verified.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth()) {
                                Text("Coming soon")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PackDetailScreen(
        pack: StickerPack,
        stickers: List<StickerItem>,
        packs: List<StickerPack>,
        busy: Boolean,
        previewCache: StickerPreviewCache,
        onImport: () -> Unit,
        onRename: () -> Unit,
        onCategory: () -> Unit,
        onShare: () -> Unit,
        onWhatsApp: () -> Unit,
        onBusiness: () -> Unit,
        onDeletePack: () -> Unit,
        onManageSticker: (StickerItem) -> Unit
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(142.dp),
                modifier = Modifier.fillMaxSize().widthIn(max = 1100.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PackHeaderCard(pack, stickers.size)
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    StickerDockActionGrid(
                        listOf(
                            StickerDockAction(
                                "Import stickers",
                                if (pack.kind == StickerKind.ANIMATED) "Animated WebP • max 500 KB" else "PNG, JPEG or static WebP • max 100 KB",
                                enabled = !busy && stickers.size < 30,
                                primary = true,
                                onClick = onImport
                            ),
                            StickerDockAction("Rename pack", enabled = !busy, onClick = onRename),
                            StickerDockAction("Change topic", enabled = !busy, onClick = onCategory),
                            StickerDockAction(
                                "Share pack ZIP",
                                "Portable StickerDock archive",
                                enabled = !busy && stickers.isNotEmpty(),
                                onClick = onShare
                            )
                        )
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Add to WhatsApp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (stickers.size < 3)
                                    "Add at least 3 stickers before exporting this pack."
                                else "Choose the installed WhatsApp app that should receive this pack.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                if (maxWidth < 420.dp) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            enabled = stickers.size in 3..30,
                                            onClick = onWhatsApp,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("WhatsApp") }
                                        OutlinedButton(
                                            enabled = stickers.size in 3..30,
                                            onClick = onBusiness,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("WhatsApp Business") }
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            enabled = stickers.size in 3..30,
                                            onClick = onWhatsApp,
                                            modifier = Modifier.weight(1f)
                                        ) { Text("WhatsApp") }
                                        OutlinedButton(
                                            enabled = stickers.size in 3..30,
                                            onClick = onBusiness,
                                            modifier = Modifier.weight(1f)
                                        ) { Text("WhatsApp Business") }
                                    }
                                }
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StickerDockSectionHeader("Stickers", "Tap Manage for metadata, transfer or removal")
                        TextButton(enabled = !busy, onClick = onDeletePack) {
                            Text("Delete pack", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (stickers.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyStateCard(
                            "This pack is empty",
                            "Import images to build the pack. Static and animated stickers stay separated."
                        )
                    }
                } else {
                    gridItems(stickers, key = { it.id }) { item ->
                        StickerTile(
                            item = item,
                            kind = pack.kind,
                            previewCache = previewCache,
                            enabled = !busy,
                            onManage = { onManageSticker(item) }
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    @Composable
    private fun StickerTile(
        item: StickerItem,
        kind: StickerKind,
        previewCache: StickerPreviewCache,
        enabled: Boolean,
        onManage: () -> Unit
    ) {
        val path = files.stickerFile(item.packId, item.fileName).path
        val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path, kind) {
            value = withContext(Dispatchers.IO) {
                if (kind == StickerKind.STATIC) previewCache.load(path) else null
            }
        }
        val animation by produceState<android.graphics.drawable.Drawable?>(null, path, kind) {
            value = withContext(Dispatchers.IO) {
                if (kind == StickerKind.ANIMATED) {
                    try {
                        ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path)))
                    } catch (_: Exception) {
                        null
                    }
                } else null
            }
        }
        DisposableEffect(animation) {
            (animation as? AnimatedImageDrawable)?.start()
            onDispose { (animation as? AnimatedImageDrawable)?.stop() }
        }

        Card {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
                        if (kind == StickerKind.ANIMATED) {
                            if (animation == null) {
                                Text("Animation unavailable", style = MaterialTheme.typography.labelSmall)
                            } else {
                                AndroidView(
                                    factory = { context ->
                                        ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                                    },
                                    update = { it.setImageDrawable(animation) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else if (image == null) {
                            Text("Preview unavailable", style = MaterialTheme.typography.labelSmall)
                        } else {
                            image?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = item.displayName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.tags.isNotBlank()) {
                    Text(
                        item.tags,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(enabled = enabled, onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage")
                }
            }
        }
    }

    @Composable
    private fun SavedLinksDialog(
        input: String,
        links: List<StickerlyLinks.Link>,
        onInput: (String) -> Unit,
        onOpen: (StickerlyLinks.Link) -> Unit,
        onRemove: (StickerlyLinks.Link) -> Unit,
        onSave: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Sticker.ly pack bookmarks") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Paste public Sticker.ly pack links or codes. StickerDock saves bookmarks only and does not download creators' stickers.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInput,
                        label = { Text("Links or codes, one per line") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (links.isEmpty()) {
                        Text("No bookmarks saved.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(links, key = { it.code }) { link ->
                                Card(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { onOpen(link) }, modifier = Modifier.weight(1f)) {
                                            Text(link.code, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        TextButton(onClick = { onRemove(link) }) { Text("Remove") }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onSave, enabled = input.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
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
                scope.launch {
                    snackbar.showSnackbar("WhatsApp app not available or sticker import unsupported")
                }
            } else {
                startActivity(intent)
            }
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Unable to launch WhatsApp") }
        } catch (e: SecurityException) {
            scope.launch { snackbar.showSnackbar(e.message ?: "WhatsApp refused request") }
        }
    }
}
