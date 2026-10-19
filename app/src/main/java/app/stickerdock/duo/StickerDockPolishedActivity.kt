package app.stickerdock.duo

import android.app.Activity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PolishedHomeSection(val label: String, val mark: String) {
    LIBRARY("Library", "▦"),
    CREATE("Create", "+"),
    IMPORT("Import", "⇩"),
    FACTORY("Discover", "✦")
}

private data class LibrarySnapshot(
    val packs: List<StickerPack>,
    val stickers: List<StickerItem>,
    val counts: Map<String, Int>,
    val previews: Map<String, List<StickerItem>>,
    val drafts: List<StickerItem>
)

class StickerDockPolishedActivity : ComponentActivity() {
    private val libraryPreviewCache = StickerPreviewCache(32)
    private lateinit var store: StickerStore
    private lateinit var files: StickerFiles
    private lateinit var backup: StickerBackup
    private var incomingPackCode by mutableStateOf<String?>(null)
    private var incomingShare by mutableStateOf<Intent?>(null)

    private fun packCode(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_VIEW -> intent.dataString?.let(PublicPackLinks::parse)
        Intent.ACTION_SEND -> if (intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT)?.let(PublicPackLinks::fromSharedText) else null
        else -> null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingPackCode = packCode(intent)
        incomingShare = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = StickerStore(applicationContext)
        files = StickerFiles(applicationContext, store)
        backup = StickerBackup(applicationContext, store, files)
        if (savedInstanceState == null) { incomingPackCode = packCode(intent); incomingShare = intent }
        setContent { StickerDockTheme { StickerDockApp() } }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) libraryPreviewCache.clear()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        libraryPreviewCache.clear()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun StickerDockApp() {
        val scope = rememberCoroutineScope()
        val cloud = remember { CloudLibrary(applicationContext) }
        var showCloud by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var pendingPhotoUris by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
        var photoSaveError by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingSharedFiles by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
        var pendingSharedType by rememberSaveable { mutableStateOf("application/zip") }
        var sharedImportError by rememberSaveable { mutableStateOf<String?>(null) }
        var showBackupOptions by rememberSaveable { mutableStateOf(false) }
        var editableBackup by rememberSaveable { mutableStateOf(true) }
        var cloudRevision by remember { mutableStateOf(0) }
        var libraryReady by remember { mutableStateOf(false) }
        var reportingPack by remember { mutableStateOf<CommunityCatalog.Listing?>(null) }
        var reportReason by remember { mutableStateOf("") }
        val snackbar = remember { SnackbarHostState() }
        val previewCache = remember { libraryPreviewCache }
        val artworkProjects = remember { ArtworkProjects(applicationContext, store, files) }
        val pausePhoneImport = remember { AtomicBoolean(false) }

        var sectionName by rememberSaveable { mutableStateOf(PolishedHomeSection.LIBRARY.name) }
        var importReturnSectionName by rememberSaveable { mutableStateOf(PolishedHomeSection.LIBRARY.name) }
        val section = PolishedHomeSection.valueOf(sectionName)
        var packs by remember { mutableStateOf<List<StickerPack>>(emptyList()) }
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        var stickers by remember { mutableStateOf<List<StickerItem>>(emptyList()) }
        var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
        var previewItems by remember { mutableStateOf<Map<String, List<StickerItem>>>(emptyMap()) }
        var artworkDrafts by remember { mutableStateOf<List<StickerItem>>(emptyList()) }
        var clipUri by rememberSaveable { mutableStateOf<String?>(null) }

        var searchText by rememberSaveable { mutableStateOf("") }
        var stickerMatches by remember { mutableStateOf<List<StickerSearchResult>>(emptyList()) }
        var matchedStickerPackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
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
        var communityLoaded by remember { mutableStateOf(false) }
        var communityPacks by remember { mutableStateOf<List<CommunityCatalog.Listing>>(emptyList()) }
        var communityError by remember { mutableStateOf<String?>(null) }
        var catalogImportedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        var previewingCatalogPack by remember { mutableStateOf<CommunityCatalog.Listing?>(null) }
        var catalogSaveError by remember { mutableStateOf<String?>(null) }
        var catalogArtworkRevision by remember { mutableStateOf(0) }
        var showPackCode by rememberSaveable { mutableStateOf(false) }
        var packCodeInput by rememberSaveable { mutableStateOf("") }
        var packCodeError by remember { mutableStateOf<String?>(null) }
        var pendingPackCode by rememberSaveable { mutableStateOf<String?>(null) }
        var whatsAppStatus by remember { mutableStateOf<Map<WhatsAppTarget, WhatsAppPackStatus>>(emptyMap()) }
        var whatsAppStatusRefresh by remember { mutableStateOf(0) }
        var pendingWhatsAppTargetName by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingWhatsAppPackId by rememberSaveable { mutableStateOf<String?>(null) }

        var showPackOptions by remember { mutableStateOf(false) }
        var managingSticker by remember { mutableStateOf<StickerItem?>(null) }
        var editingSticker by remember { mutableStateOf<StickerItem?>(null) }
        var artworkSticker by rememberSaveable(stateSaver = Saver<StickerItem?, String>(
            save = { item -> item?.let { org.json.JSONArray().put(it.id).put(it.packId).put(it.fileName)
                .put(it.sha256).put(it.createdAt).put(it.label).put(it.tags).toString() } },
            restore = { raw -> runCatching { org.json.JSONArray(raw).let {
                StickerItem(it.getString(0), it.getString(1), it.getString(2), it.getString(3), it.getLong(4), it.getString(5), it.getString(6))
            } }.getOrNull() }
        )) { mutableStateOf<StickerItem?>(null) }
        var refreshJob by remember { mutableStateOf<Job?>(null) }
        var stickerLabel by remember { mutableStateOf("") }
        var stickerTags by remember { mutableStateOf("") }
        var transferItems by remember { mutableStateOf<List<StickerItem>?>(null) }
        var transferTarget by remember { mutableStateOf<String?>(null) }
        var selectedStickerIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        var bulkDeletePending by remember { mutableStateOf(false) }

        val pack = packs.firstOrNull { it.id == selectedId }
        val cloudFingerprint = packs.joinToString(";") { "${it.id}:${it.version}" }
        LaunchedEffect(cloudFingerprint, cloudRevision, libraryReady) {
            if (cloud.enabled && libraryReady) {
                delay(3000)
                // A previous snapshot may still finish its current HTTP call
                // after this effect is cancelled by another local edit.
                val syncJob = kotlinx.coroutines.currentCoroutineContext()[Job]
                for (attempt in 0 until 60) {
                    val result = runCatching {
                        withContext(Dispatchers.IO) { cloud.sync(stopped = { syncJob?.isActive == false }) }
                    }
                    if (result.getOrNull() != "Sync is already running") break
                    delay(5000)
                }
            }
        }
        val filteredPacks = packs.filter {
            PackPresentation.matches(it, searchText, packFilter, categoryFilter, matchedStickerPackIds)
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
            val requestedId = selectedId
            refreshJob?.cancel()
            refreshJob = scope.launch {
                val snapshot = withContext(Dispatchers.IO) {
                    val all = store.listPacks()
                    LibrarySnapshot(
                        packs = all,
                        stickers = requestedId?.let(store::listStickers).orEmpty(),
                        counts = store.stickerCounts(),
                        previews = store.stickerPreviews(),
                        drafts = store.artworkDrafts()
                    )
                }
                packs = snapshot.packs
                libraryReady = true
                if (selectedId == requestedId) stickers = snapshot.stickers
                counts = snapshot.counts
                previewItems = snapshot.previews
                artworkDrafts = snapshot.drafts
                if (selectedId != null && packs.none { it.id == selectedId }) selectedId = null
                selectedStickerIds = selectedStickerIds.intersect(stickers.mapTo(mutableSetOf()) { it.id })
            }
        }

        fun operation(label: String, action: suspend () -> String) {
            if (busy) return
            busyLabel = label
            scope.launch {
                val message = try {
                    val message = withContext(Dispatchers.IO) { action() }
                    refresh()
                    message
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.message ?: "Operation failed"
                } finally {
                    busyLabel = null
                }
                snackbar.showSnackbar(message)
            }
        }

        fun deleteStickerWithUndo(item: StickerItem) {
            if (busy) return
            managingSticker = null
            busyLabel = "Removing sticker"
            scope.launch {
                try {
                    val deleted = withContext(Dispatchers.IO) {
                        val source = files.stickerFile(item.packId, item.fileName)
                        val snapshot = source.readBytes()
                        val project = artworkProjects.snapshot(item)
                        files.deleteSticker(item)
                        snapshot to project
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
                            files.restoreBytes(item.packId, deleted.first, item.sha256, item.label, item.tags) { restored ->
                                deleted.second?.let { artworkProjects.restoreSnapshot(restored, it) }
                            }
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
                        val community = CommunityCatalog(this@StickerDockPolishedActivity, backup)
                        CatalogDiscovery(this@StickerDockPolishedActivity, store, community::browse).refresh()
                    }
                    communityPacks = result.packs
                    catalogImportedIds = result.savedIds
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    communityError = e.message ?: "Unable to load new stickers"
                } finally {
                    communityLoaded = true
                    communityLoading = false
                }
            }
        }

        fun importFactoryPack(entry: CommunityCatalog.Listing) {
            operation("Adding sticker pack") {
                val summary = try {
                    CommunityCatalog(this@StickerDockPolishedActivity, backup).downloadAndImport(entry)
                } catch (error: Exception) {
                    runOnUiThread { catalogSaveError = error.message ?: "Couldn't save this pack. Please retry." }
                    throw error
                }
                val importedIds = CatalogImports(this@StickerDockPolishedActivity, store).importedPackIds(entry.id, entry.sha256)
                runOnUiThread {
                    previewingCatalogPack = null
                    catalogImportedIds = catalogImportedIds + entry.id
                    selectedId = importedIds.firstOrNull()
                    sectionName = PolishedHomeSection.LIBRARY.name
                }
                if (summary.stickers == 0) "Pack already in your library" else "Imported ${summary.stickers} stickers"
            }
        }

        fun openPublicPackCode(raw: String) {
            val id = PublicPackLinks.parse(raw)
            if (id == null) { packCodeError = "Enter a StickerDock code starting with SD: or a StickerDock pack link."; return }
            packCodeError = null
            selectedId = null
            sectionName = PolishedHomeSection.FACTORY.name
            refreshFactory()
            pendingPackCode = id
        }

        LaunchedEffect(incomingPackCode) {
            incomingPackCode?.let { id ->
                packCodeInput = PublicPackLinks.code(id)
                showPackCode = true
                openPublicPackCode(packCodeInput)
                incomingPackCode = null
            }
        }
        LaunchedEffect(pendingPackCode, communityLoading, communityLoaded, communityPacks, communityError) {
            val id = pendingPackCode ?: return@LaunchedEffect
            if (communityLoading || !communityLoaded) return@LaunchedEffect
            val entry = communityPacks.firstOrNull { it.id == id }
            when {
                communityError != null -> packCodeError = "Couldn't check this pack. Check your connection and try again."
                entry == null -> packCodeError = "This pack is unavailable or hidden. Check the code, or show hidden packs in Discover options."
                else -> { catalogSaveError = null; previewingCatalogPack = entry; showPackCode = false }
            }
            pendingPackCode = null
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

        val clipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) {}
                clipUri = uri.toString()
            }
        }

        val photoPackPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                if (uris.size > 100) scope.launch { snackbar.showSnackbar("Choose up to 100 photos at once") }
                else {
                    uris.forEach { uri ->
                        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) {}
                    }
                    // A single GIF belongs in the moving creator; never silently flatten it.
                    if (uris.size == 1 && contentResolver.getType(uris.first()) == "image/gif") clipUri = uris.first().toString()
                    else { pendingPhotoUris = uris.map { it.toString() }; photoSaveError = null }
                }
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
                var firstError: String? = null
                uris.forEach { uri ->
                    try {
                        val imported = PackZipImporter(this@StickerDockPolishedActivity, store, files).import(uri)
                        val result = Triple(imported.packs, imported.imported, imported.skipped + imported.duplicates)
                        packsCreated += result.first
                        added += result.second
                        skipped += result.third
                    } catch (error: Exception) {
                        failed++
                        if (firstError == null) firstError = error.message?.take(160) ?: "Cannot read this archive"
                    }
                }
                "${uris.size} ZIPs • $packsCreated packs • $added stickers • $skipped skipped • $failed failed" + firstError?.let { ". $it" }.orEmpty()
            }
        }

        val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) {
                val includeProjects = editableBackup
                operation("Creating backup") {
                    if (includeProjects) EditableBackup(this@StickerDockPolishedActivity, store, files).export(uri) else backup.export(uri)
                    if (includeProjects) "Editable backup saved" else "Sticker backup saved"
                }
            }
        }
        val loadBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) operation("Restoring backup") {
                val restored = EditableBackup(this@StickerDockPolishedActivity, store, files).restoreOrSaved(uri)
                "Restored ${restored.packs} packs • ${restored.stickers} stickers • ${restored.rejected} skipped"
            }
        }
        val whatsAppInstaller = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val target = pendingWhatsAppTargetName?.let(WhatsAppTarget::valueOf)
            val requestedPack = pendingWhatsAppPackId
            pendingWhatsAppTargetName = null
            pendingWhatsAppPackId = null
            whatsAppStatusRefresh++
            scope.launch {
                val validationError = result.data?.getStringExtra("validation_error")
                val verified = if (target != null && requestedPack != null) withContext(Dispatchers.IO) {
                    WhatsAppIntegration.status(this@StickerDockPolishedActivity, requestedPack, target).added
                } else false
                snackbar.showSnackbar(when {
                    !validationError.isNullOrBlank() -> "WhatsApp rejected this pack: $validationError"
                    verified -> "Added to ${target?.label ?: "WhatsApp"}"
                    result.resultCode == Activity.RESULT_OK -> "Request accepted. Open WhatsApp's sticker tray to check the pack."
                    else -> "Pack wasn't added. Your stickers are still saved here."
                })
            }
        }

        fun launchWhatsApp(packToInstall: StickerPack, target: WhatsAppTarget, addAgain: Boolean = false) {
            if (busy) return
            busyLabel = "Preparing WhatsApp pack"
            scope.launch {
                var failure: String? = null
                try {
                    val status = withContext(Dispatchers.IO) {
                        WhatsAppIntegration.validatePack(packToInstall, store, files)
                        WhatsAppIntegration.status(this@StickerDockPolishedActivity, packToInstall.id, target)
                    }
                    require(status.installed) { "${target.label} is not installed on this phone" }
                    if (status.added && !addAgain) {
                        val openIntent = packageManager.getLaunchIntentForPackage(target.packageName)
                            ?: error("Unable to open ${target.label}")
                        startActivity(openIntent)
                    } else {
                        pendingWhatsAppTargetName = target.name
                        pendingWhatsAppPackId = packToInstall.id
                        whatsAppInstaller.launch(WhatsAppStickerProvider.installIntent(packToInstall, target.packageName))
                    }
                } catch (_: ActivityNotFoundException) {
                    failure = "Update ${target.label} to enable sticker packs, then try again."
                } catch (e: Exception) {
                    failure = e.message ?: "Unable to add sticker pack"
                } finally { busyLabel = null }
                failure?.let { snackbar.showSnackbar(it) }
            }
        }

        LaunchedEffect(selectedId) {
            selectedStickerIds = emptySet()
            refresh()
        }
        LaunchedEffect(searchText, cloudFingerprint) {
            val requested = searchText
            stickerMatches = emptyList()
            matchedStickerPackIds = emptySet()
            if (requested.isNotBlank()) {
                delay(250)
                val (ids, matches) = withContext(Dispatchers.IO) {
                    store.searchStickerPackIds(requested) to store.searchStickers(requested)
                }
                if (searchText == requested) {
                    matchedStickerPackIds = ids
                    stickerMatches = matches
                }
            }
        }
        LaunchedEffect(pack?.id, pack?.version, whatsAppStatusRefresh) {
            whatsAppStatus = emptyMap()
            whatsAppStatus = if (pack == null) emptyMap() else withContext(Dispatchers.IO) {
                WhatsAppTarget.entries.associateWith {
                    WhatsAppIntegration.status(this@StickerDockPolishedActivity, pack.id, it)
                }
            }
        }
        LaunchedEffect(Unit) {
            refreshFactory()
        }
        LaunchedEffect(incomingShare) {
            val shared = incomingShare ?: return@LaunchedEffect
            if (packCode(shared) == null && shared.action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW)) {
                try {
                    val streams = IncomingStickers.streams(shared)
                    when {
                        streams.isNotEmpty() && shared.type?.startsWith("image/") == true -> { pendingPhotoUris = streams.map { it.toString() }; photoSaveError = null }
                        streams.isNotEmpty() -> { pendingSharedFiles = streams.map { it.toString() }; pendingSharedType = shared.type ?: "application/zip"; sharedImportError = null }
                        shared.action == Intent.ACTION_SEND -> operation("Saving shared links") { IncomingStickers(this@StickerDockPolishedActivity, store, files).import(shared) }
                    }
                } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Couldn't open shared files") }
            }
            incomingShare = null
        }
        DisposableEffect(Unit) { onDispose { previewCache.clear() } }
        DisposableEffect(Unit) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshFactory()
                    cloudRevision++
                    whatsAppStatusRefresh++
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        BackHandler(enabled = selectedStickerIds.isNotEmpty() || selectedId != null || section == PolishedHomeSection.IMPORT) {
            when {
                selectedStickerIds.isNotEmpty() -> selectedStickerIds = emptySet()
                selectedId != null -> selectedId = null
                else -> sectionName = importReturnSectionName
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (pack == null) section.label else pack.name, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        if (pack != null || section == PolishedHomeSection.IMPORT) IconButton(
                            onClick = { if (pack != null) selectedId = null else sectionName = importReturnSectionName },
                            modifier = Modifier.semantics { contentDescription = "Back" }) { StickerDockNavIcon("Back") }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }, modifier = Modifier.semantics { contentDescription = "Library settings" }) {
                            StickerDockNavIcon("More")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                if (pack == null) {
                    BoxWithConstraints {
                        if (maxWidth < 720.dp) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                                PolishedHomeSection.entries.filter { it != PolishedHomeSection.IMPORT }.forEach { destination ->
                                    NavigationBarItem(
                                        selected = section == destination || (section == PolishedHomeSection.IMPORT && importReturnSectionName == destination.name),
                                        onClick = { sectionName = destination.name },
                                        icon = { StickerDockNavIcon(destination.label) },
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
                                PolishedHomeSection.entries.filter { it != PolishedHomeSection.IMPORT }.forEach { destination ->
                                    NavigationRailItem(
                                        selected = section == destination || (section == PolishedHomeSection.IMPORT && importReturnSectionName == destination.name),
                                        onClick = {
                                            sectionName = destination.name
                                            if (destination != PolishedHomeSection.LIBRARY) selectedId = null
                                        },
                                        icon = { StickerDockNavIcon(destination.label) },
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
                                                { importReturnSectionName = PolishedHomeSection.LIBRARY.name; sectionName = PolishedHomeSection.IMPORT.name; folderImporter.launch(null) },
                                                { selectedId = it },
                                                { photoPackPicker.launch(arrayOf("image/*")) },
                                                artworkDrafts, { selectedId = it.packId; artworkSticker = it },
                                                { clipPicker.launch(arrayOf("video/*", "image/gif")) }, packFilter, { packFilter = it }
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
                                                whatsAppStatus,
                                                { launchWhatsApp(pack, WhatsAppTarget.PERSONAL) },
                                                { launchWhatsApp(pack, WhatsAppTarget.BUSINESS) },
                                                { managingSticker = it },
                                                { item -> selectedStickerIds = if (item.id in selectedStickerIds) selectedStickerIds - item.id else selectedStickerIds + item.id },
                                                { item -> selectedStickerIds = selectedStickerIds + item.id },
                                                { selectedStickerIds = emptySet() },
                                                {
                                                    val chosen = stickers.filter { it.id in selectedStickerIds }
                                                    transferItems = chosen
                                                    transferTarget = packs.firstOrNull { it.id != pack.id && it.kind == pack.kind }?.id
                                                },
                                                { bulkDeletePending = true },
                                                { target -> launchWhatsApp(pack, target, addAgain = true) }
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
                                    whatsAppStatus,
                                    { launchWhatsApp(pack, WhatsAppTarget.PERSONAL) },
                                    { launchWhatsApp(pack, WhatsAppTarget.BUSINESS) },
                                    { managingSticker = it },
                                    { item -> selectedStickerIds = if (item.id in selectedStickerIds) selectedStickerIds - item.id else selectedStickerIds + item.id },
                                    { item -> selectedStickerIds = selectedStickerIds + item.id },
                                    { selectedStickerIds = emptySet() },
                                    {
                                        transferItems = stickers.filter { it.id in selectedStickerIds }
                                        transferTarget = packs.firstOrNull { it.id != pack.id && it.kind == pack.kind }?.id
                                    },
                                    { bulkDeletePending = true },
                                    { target -> launchWhatsApp(pack, target, addAgain = true) }
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
                                        { importReturnSectionName = PolishedHomeSection.LIBRARY.name; sectionName = PolishedHomeSection.IMPORT.name; folderImporter.launch(null) },
                                        { selectedId = it },
                                        { photoPackPicker.launch(arrayOf("image/*")) },
                                        artworkDrafts, { selectedId = it.packId; artworkSticker = it },
                                        { clipPicker.launch(arrayOf("video/*", "image/gif")) }, packFilter, { packFilter = it }
                                    )
                                }
                                section == PolishedHomeSection.CREATE -> AnimatedScreenSwap("create") {
                                    CreateScreen(busy,
                                        { photoPackPicker.launch(arrayOf("image/*")) },
                                        { clipPicker.launch(arrayOf("video/*", "image/gif")) },
                                        { importReturnSectionName = PolishedHomeSection.CREATE.name; sectionName = PolishedHomeSection.IMPORT.name })
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
                                                try { withContext(Dispatchers.IO) { backup.estimate() }; showBackupOptions = true }
                                                catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Cannot create backup") }
                                            }
                                        },
                                        { loadBackup.launch(arrayOf("application/zip")) },
                                        { photoPackPicker.launch(arrayOf("image/*")) },
                                        { clipPicker.launch(arrayOf("video/*", "image/gif")) }
                                    )
                                }
                                else -> AnimatedScreenSwap("factory") {
                                    FactoryScreen(communityLoading, communityPacks, communityError, catalogImportedIds, busy, ::refreshFactory,
                                        onPack = {
                                            catalogSaveError = null
                                            val entry = it
                                            scope.launch {
                                                val localIds = withContext(Dispatchers.IO) {
                                                    CatalogImports(this@StickerDockPolishedActivity, store).importedPackIds(entry.id, entry.sha256)
                                                }
                                                if (localIds.isNotEmpty()) {
                                                    selectedId = localIds.first()
                                                    sectionName = PolishedHomeSection.LIBRARY.name
                                                } else previewingCatalogPack = entry
                                            }
                                        },
                                        onPreview = { entry -> withContext(Dispatchers.IO) {
                                            CommunityCatalog(this@StickerDockPolishedActivity, backup).preview(entry)
                                        } },
                                        previewRevision = catalogArtworkRevision,
                                        onHide = { entry ->
                                            CommunityCatalog(this@StickerDockPolishedActivity, backup).hide(entry)
                                            refreshFactory()
                                            scope.launch { snackbar.showSnackbar("Hidden from new stickers. Saved local packs are kept.") }
                                        },
                                        onReport = { entry -> reportingPack = entry; reportReason = "" },
                                        onShowHidden = { CommunityCatalog(this@StickerDockPolishedActivity, backup).clearHidden(); refreshFactory() },
                                        onOpenCode = { packCodeError = null; showPackCode = true })
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSettings) LibrarySettingsSheet(
            onAccount = { showSettings = false; showCloud = true },
            onImport = { showSettings = false; selectedId = null; importReturnSectionName = sectionName; sectionName = PolishedHomeSection.IMPORT.name },
            onDismiss = { showSettings = false })

        if (pendingPhotoUris.isNotEmpty()) PhotoPackSheet(pendingPhotoUris.size, packs, counts, busy, photoSaveError,
            onSave = { title, target ->
                val chosen = pendingPhotoUris.map(android.net.Uri::parse)
                photoSaveError = null
                operation("Making stickers") {
                    try {
                        val result = PhotoPackImporter(this@StickerDockPolishedActivity, store, files)
                            .import(chosen, title, target, deduplicateLibrary = false)
                        runOnUiThread {
                            pendingPhotoUris = result.failedUris.map { it.toString() }
                            photoSaveError = result.firstError?.let { "${result.added} saved · ${result.failed} need attention. $it" }
                            result.packIds.firstOrNull()?.let { selectedId = it; sectionName = PolishedHomeSection.LIBRARY.name }
                        }
                        buildString {
                            append("${result.added} ${if (result.added == 1) "sticker" else "stickers"} saved")
                            if (result.duplicates > 0) append(" · ${result.duplicates} already in this selection")
                            if (result.failed > 0) append(" · ${result.failed} need attention")
                        }
                    } catch (error: Exception) {
                        runOnUiThread { photoSaveError = error.message ?: "Couldn't save. Your selection is still here." }
                        throw error
                    }
                }
            }, onDismiss = { pendingPhotoUris = emptyList(); photoSaveError = null })

        previewingCatalogPack?.let { entry ->
            CatalogPackSheet(entry, entry.id in catalogImportedIds, busy, catalogSaveError,
                onPreview = { listing ->
                    val bytes = withContext(Dispatchers.IO) {
                        CommunityCatalog(this@StickerDockPolishedActivity, backup).preview(listing, allowLegacyDownload = true)
                    }
                    if (bytes != null) catalogArtworkRevision++
                    bytes
                },
                onSave = { catalogSaveError = null; importFactoryPack(entry) },
                onDismiss = { if (!busy) previewingCatalogPack = null },
                onShare = {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, PublicPackLinks.shareText(entry.name, entry.id))
                    }, "Share sticker pack"))
                })
        }

        if (showBackupOptions) AlertDialog(onDismissRequest = { showBackupOptions = false },
            title = { Text("Back up your library") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Choose what to keep. Both options include saved packs, labels and tags.")
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        editableBackup = true; showBackupOptions = false; saveBackup.launch("stickerdock-editable.zip")
                    }) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Editable backup", style = MaterialTheme.typography.titleMedium)
                            Text("For Android restore. Also keeps original images, cutout masks and unfinished drafts. Keep this file private.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        editableBackup = false; showBackupOptions = false; saveBackup.launch("stickerdock.zip")
                    }) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Sticker backup", style = MaterialTheme.typography.titleMedium)
                            Text("Portable saved stickers for Android, iPhone and older versions. Does not include editable projects.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBackupOptions = false }) { Text("Cancel") } })

        if (showPackCode) AlertDialog(onDismissRequest = { pendingPackCode = null; showPackCode = false },
            title = { Text("Open pack code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Preview a shared pack before saving it to your library.")
                    OutlinedTextField(value = packCodeInput, onValueChange = { packCodeInput = it.take(256); packCodeError = null },
                        label = { Text("Pack code or link") }, singleLine = true, enabled = pendingPackCode == null)
                    if (pendingPackCode != null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    packCodeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = pendingPackCode == null && packCodeInput.isNotBlank(), onClick = { openPublicPackCode(packCodeInput) }) { Text("Preview pack") } },
            dismissButton = { TextButton(onClick = { pendingPackCode = null; showPackCode = false }) { Text("Cancel") } })

        if (pendingSharedFiles.isNotEmpty()) AlertDialog(onDismissRequest = { if (!busy) pendingSharedFiles = emptyList() },
            title = { Text("Import shared packs?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${pendingSharedFiles.size} ${if (pendingSharedFiles.size == 1) "file is" else "files are"} ready to import. They will be added as packs in your library. Your existing packs stay as they are.")
                sharedImportError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                val shared = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = pendingSharedType
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(pendingSharedFiles.map(android.net.Uri::parse)))
                }
                operation("Importing shared packs") {
                    val result = IncomingStickers(this@StickerDockPolishedActivity, store, files).importReport(shared)
                    runOnUiThread {
                        pendingSharedFiles = result.failedUris.map { it.toString() }
                        sharedImportError = if (result.failedUris.isEmpty()) null else result.message
                        sectionName = PolishedHomeSection.LIBRARY.name
                    }
                    result.message
                }
            }) { Text(if (busy) "Importing…" else "Import packs") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingSharedFiles = emptyList() }) { Text("Cancel") } })

        if (showFilters) {
            LibraryFiltersSheet(
                packFilter, packSort, categoryFilter,
                { packFilter = it }, { packSort = it }, { categoryName = it?.name.orEmpty() },
                ::clearFilters, { showFilters = false }
            )
        }

        clipUri?.let { chosen ->
            ClipStickerEditor(android.net.Uri.parse(chosen), packs,
                onSave = { bytes, caption, target, title ->
                    val (saved, size) = withContext(Dispatchers.IO) {
                        val created = ClipStickerSaver(store, files).save(bytes, caption, target, title)
                        created to store.stickerCount(created.id)
                    }
                    clipUri = null; selectedId = saved.id; sectionName = PolishedHomeSection.LIBRARY.name
                    refresh()
                    scope.launch { snackbar.showSnackbar(if (size < 3) "Animation saved. Add ${3 - size} more to use this pack in WhatsApp." else "Animation saved. Your pack is ready to add to WhatsApp.") }
                }, onDismiss = { clipUri = null })
        }
        if (showCloud) CloudLibrarySheet(cloud, pack, { cloudRevision++; refresh() }, { showCloud = false })
        reportingPack?.let { entry ->
            AlertDialog(onDismissRequest = { reportingPack = null }, title = { Text("Report ${entry.name}") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Report unsafe content, private information or copyright concerns. You can also contact dvjjala@gmail.com.")
                    if (cloud.signedIn) OutlinedTextField(reportReason, { reportReason = it.take(1000) }, label = { Text("Reason") })
                    else Text("Sign in through Cloud to send an in-app report, or use the support email.")
                } },
                confirmButton = { TextButton(enabled = cloud.signedIn && reportReason.isNotBlank(), onClick = {
                    reportingPack = null
                    operation("Sending report") { cloud.report(entry.id, reportReason) }
                }) { Text("Send report") } }, dismissButton = { TextButton(onClick = { reportingPack = null }) { Text("Cancel") } })
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
                onEditArtwork = {
                    managingSticker = null
                    artworkSticker = item
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

        artworkSticker?.let { item ->
            StickerArtworkEditor(item, artworkProjects,
                onSave = { history ->
                    withContext(Dispatchers.IO) { artworkProjects.save(item, history) }
                    artworkSticker = null
                    refresh()
                    scope.launch { snackbar.showSnackbar("Sticker updated. Open WhatsApp to refresh your pack.") }
                },
                onDismiss = { artworkSticker = null; refresh() }
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
                onCreatePack = { packName ->
                    if (sourcePack != null) operation("Creating pack") {
                        val created = store.createPack(packName, sourcePack.kind, sourcePack.rating, sourcePack.category)
                        runOnUiThread { transferTarget = created.id }
                        "Pack created. Tap Copy or Move to finish."
                    }
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

}
