package app.stickerdock.duo

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    packs: List<StickerPack>, counts: Map<String, Int>, previewItems: Map<String, List<StickerItem>>,
    filteredPacks: List<StickerPack>, searchText: String, stickerMatches: List<StickerSearchResult>,
    activeFilterCount: Int, selectedPackId: String?, busy: Boolean, files: StickerFiles,
    previewCache: StickerPreviewCache, compactPane: Boolean,
    onSearchChange: (String) -> Unit, onOpenFilters: () -> Unit, onClearFilters: () -> Unit,
    onCreatePack: () -> Unit, onImportPhone: () -> Unit, onOpenPack: (String) -> Unit,
    onPhotos: () -> Unit, drafts: List<StickerItem> = emptyList(),
    onResumeDraft: (StickerItem) -> Unit = {}, onClip: () -> Unit = {},
    packFilter: String = "All", onPackFilterChange: (String) -> Unit = {}
) {
    val matchesByPack = remember(stickerMatches) { stickerMatches.groupBy { it.pack.id } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.fillMaxHeight().widthIn(max = if (compactPane) 520.dp else 800.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (packs.isEmpty() && searchText.isBlank()) {
                item { LibraryOnboardingCard(busy, onPhotos, onImportPhone) }
                item { ProductAction("Make an animated reaction", "Turn a video or GIF into a sticker", "Video", !busy, onClip) }
                item { TextButton(enabled = !busy, onClick = onCreatePack) { Text("Create an empty pack") } }
                return@LazyColumn
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LibrarySearch(searchText, onSearchChange, "Search library", Modifier.weight(1f))
                    TextButton(onClick = onOpenFilters) { Text(if (activeFilterCount == 0) "Filters" else "Filters ($activeFilterCount)") }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All", "Static", "Animated")) { type ->
                        FilterChip(selected = packFilter == type, onClick = { onPackFilterChange(type) }, label = { Text(if (type == "All") "All packs" else type) })
                    }
                }
            }
            if (drafts.isNotEmpty() && searchText.isBlank()) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(drafts, key = { it.id }) { draft ->
                        Surface(onClick = { onResumeDraft(draft) }, enabled = !busy,
                            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                            Row(Modifier.width(240.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PackPreviewImage(files.stickerFile(draft.packId, draft.fileName).path, draft.displayName, previewCache, Modifier.size(48.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Resume edit", style = MaterialTheme.typography.labelLarge)
                                    Text(draft.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (searchText.isBlank()) "${packs.size} packs · ${counts.values.sum()} stickers" else "${filteredPacks.size} matching packs",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(enabled = !busy, onClick = onCreatePack) { Text("New pack") }
                }
            }
            items(filteredPacks, key = { it.id }) { pack ->
                val matching = matchesByPack[pack.id].orEmpty()
                VisualPackCard(pack, counts[pack.id] ?: 0,
                    if (searchText.isNotBlank() && matching.isNotEmpty()) matching.take(4).map { it.item } else previewItems[pack.id].orEmpty(),
                    files, previewCache, selectedPackId == pack.id,
                    if (searchText.isNotBlank() && matching.isNotEmpty()) "Matching stickers" else null,
                    onClick = { onOpenPack(pack.id) })
            }
            if (filteredPacks.isEmpty()) item {
                EmptyStateCard(if (searchText.isBlank()) "No packs match these filters" else "No matching packs",
                    "Try another name, label or tag, or clear your filters.", "Clear search & filters",
                    { onSearchChange(""); onClearFilters() })
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun LibraryOnboardingCard(busy: Boolean, onCreatePack: () -> Unit, onImportPhone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your library starts here", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("A pack for every conversation.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProductAction("Choose photos", "Make your first sticker pack", "Photo", !busy, onCreatePack)
        TextButton(enabled = !busy, onClick = onImportPhone) { Text("Import from phone") }
    }
}

@Composable
private fun VisualPackCard(pack: StickerPack, stickerCount: Int, previews: List<StickerItem>, files: StickerFiles,
    previewCache: StickerPreviewCache, selected: Boolean, matchLabel: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected; role = Role.Button }) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(pack.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(matchLabel ?: buildString {
                        append("$stickerCount stickers")
                        if (pack.kind == StickerKind.ANIMATED) append(" · Animated")
                        if (pack.category != PackCategory.GENERAL) append(" · ${pack.category.label}")
                        if (pack.rating == PackRating.MATURE) append(" · Private")
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StickerDockNavIcon("Forward")
            }
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { index ->
                    val item = previews.getOrNull(index)
                    if (item != null) PackPreviewImage(files.stickerFile(pack.id, item.fileName).path, item.displayName,
                        previewCache, Modifier.weight(1f).aspectRatio(1f))
                    else Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (index == 0) StickerDockNavIcon("Create")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
        }
    }
}

@Composable
private fun PackPreviewImage(path: String, description: String, previewCache: StickerPreviewCache, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, path) { value = withContext(Dispatchers.IO) { previewCache.load(path, 256) } }
    Box(modifier, contentAlignment = Alignment.Center) {
        image?.let { Image(it, description, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFiltersSheet(
    packFilter: String,
    packSort: String,
    categoryFilter: PackCategory?,
    onPackFilterChange: (String) -> Unit,
    onPackSortChange: (String) -> Unit,
    onCategoryChange: (PackCategory?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Filter your library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Sticker type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All", "Static", "Animated")) { type -> FilterChip(packFilter == type, { onPackFilterChange(type) }, { Text(type) }) }
            }
            Text("Sort", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Newest", "Name", "Largest")) { sort -> FilterChip(packSort == sort, { onPackSortChange(sort) }, { Text(sort) }) }
            }
            Text("Topic", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(categoryFilter == null, { onCategoryChange(null) }, { Text("All topics") }) }
                items(PackCategory.entries) { category -> FilterChip(categoryFilter == category, { onCategoryChange(category) }, { Text(category.label) }) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Clear filters") }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Show results") }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
fun CreateScreen(busy: Boolean, onPhotos: () -> Unit, onClip: () -> Unit, onImport: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.fillMaxHeight().widthIn(max = 800.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Make it yours.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Small stickers. Big reactions.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { ProductAction("Make stickers from photos", "Choose photos, name a pack, then add your finishing touches.", "Photo", !busy, onPhotos) }
            item { ProductAction("Make an animated reaction", "Trim a video or GIF. Add a caption and preview it moving.", "Video", !busy, onClip) }
            item { Spacer(Modifier.height(8.dp)) }
            item { TextButton(enabled = !busy, onClick = onImport) { Text("Import and backups") } }
        }
    }
}

@Composable
fun ImportScreen(
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
    onRestore: () -> Unit,
    onPhotos: () -> Unit,
    onClip: () -> Unit = {}
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.fillMaxHeight().widthIn(max = 960.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bring your stickers together", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Pick a source below. StickerDock keeps imported data local unless you explicitly share or export it.", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            progress?.let { item { ImportProgressCard(it, pauseRequested, onPause) } }
            pendingUri?.takeIf { !busy }?.let { saved -> item { PendingImportCard({ onResume(saved) }, onDiscard) } }
            if (busy && progress == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { StickerDockSectionHeader("Import sources", "Choose the source that matches what you already have.") }
            item {
                StickerDockActionGrid(listOf(
                    StickerDockAction("Make stickers from photos", "Pick images; packs are created and grouped automatically", !busy, true, onPhotos),
                    StickerDockAction("Make an animated reaction", "Trim a video or GIF, add a caption and preview locally", !busy, true, onClip),
                    StickerDockAction("Phone sticker folder", "Scan a WhatsApp or WhatsApp Business media folder", !busy, true, onPhone),
                    StickerDockAction("Sticker ZIPs", "StickerDock backups, .wastickers or generic ZIP batches", !busy, false, onZip),
                    StickerDockAction("Sticker.ly bookmarks", "Save public pack links without scraping creator files", !busy, false, onLinks)
                ))
            }
            item { StickerDockSectionHeader("Library safety", "Back up your own local collection before changing devices.") }
            item {
                StickerDockActionGrid(listOf(
                    StickerDockAction("Back up library", "Export packs, metadata and sticker bytes to a ZIP", !busy, false, onBackup),
                    StickerDockAction("Restore backup", "Merge a StickerDock backup into this library", !busy, false, onRestore)
                ))
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Where WhatsApp usually stores stickers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Personal: Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers", style = MaterialTheme.typography.bodySmall)
                        Text("Business: Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Stickers", style = MaterialTheme.typography.bodySmall)
                        Text("Android's system picker decides what the app can read, and paths may vary by WhatsApp version.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PackDetailScreen(
    pack: StickerPack,
    stickers: List<StickerItem>,
    busy: Boolean,
    previewCache: StickerPreviewCache,
    files: StickerFiles,
    selectedStickerIds: Set<String>,
    onImport: () -> Unit,
    onPackOptions: () -> Unit,
    onShare: () -> Unit,
    whatsAppStatus: Map<WhatsAppTarget, WhatsAppPackStatus>,
    onWhatsApp: () -> Unit,
    onBusiness: () -> Unit,
    onManageSticker: (StickerItem) -> Unit,
    onToggleSelection: (StickerItem) -> Unit,
    onStartSelection: (StickerItem) -> Unit,
    onClearSelection: () -> Unit,
    onBulkTransfer: () -> Unit,
    onBulkDelete: () -> Unit,
    onAddAgain: (WhatsAppTarget) -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    var showWhatsApp by remember(pack.id) { mutableStateOf(false) }
    val singleTarget = PackPresentation.singleTarget(whatsAppStatus)
    val primaryLabel = if (stickers.size < 3) "Add ${3 - stickers.size} more stickers" else PackPresentation.actionLabel(singleTarget, whatsAppStatus)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp),
            modifier = Modifier.fillMaxSize().widthIn(max = 1120.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${stickers.size}/30 • ${if (pack.kind == StickerKind.ANIMATED) "Animated" else "Static"}",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    if (pack.rating == PackRating.MATURE) Text("Private mature pack", style = MaterialTheme.typography.labelSmall)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(enabled = !busy && stickers.size < 30, onClick = onImport) { Text("Add stickers") }
                    TextButton(enabled = !busy, onClick = onPackOptions) { Text("Pack options") }
                    TextButton(enabled = !busy && stickers.isNotEmpty(), onClick = onShare) { Text("Share pack") }
                }
            }
            if (selectedStickerIds.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SelectionToolbar(selectedStickerIds.size, !busy, onClearSelection, onBulkTransfer, onBulkDelete) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(if (selectedStickerIds.isNotEmpty()) "Tap stickers to select" else if (pack.kind == StickerKind.ANIMATED) "Tap to play · Hold to select" else "Tap to edit · Hold to select",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (stickers.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyStateCard("This pack is empty", "Import images to build the pack. Static and animated stickers stay separated.", "Import stickers", onImport) }
            } else {
                gridItems(stickers, key = { it.id }) { item ->
                    val selected = item.id in selectedStickerIds
                    StickerTile(
                        item, pack.kind, previewCache, files, !busy, selected, selectedStickerIds.isNotEmpty(),
                        onClick = { if (selectedStickerIds.isNotEmpty()) onToggleSelection(item) else onManageSticker(item) },
                        onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onStartSelection(item) }
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(20.dp)) }
        }
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !busy, onClick = {
                    when {
                        stickers.size < 3 -> onImport()
                        singleTarget == WhatsAppTarget.PERSONAL -> onWhatsApp()
                        singleTarget == WhatsAppTarget.BUSINESS -> onBusiness()
                        else -> showWhatsApp = true
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = androidx.compose.ui.graphics.Color.White), modifier = Modifier.weight(1f)) {
                    Text(primaryLabel, textAlign = TextAlign.Center)
                }
                if (singleTarget != null && stickers.size >= 3) IconButton(onClick = { showWhatsApp = true },
                    modifier = Modifier.semantics { contentDescription = "Choose WhatsApp app or add again" }) { StickerDockNavIcon("More") }
            }
        }
    }
    if (showWhatsApp) {
        ModalBottomSheet(onDismissRequest = { showWhatsApp = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Use in WhatsApp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                stickers.size < 3 -> "Add at least 3 stickers before sending this pack."
                                whatsAppStatus.values.any { it.added } -> "Added packs update through WhatsApp. After editing, open WhatsApp and its sticker tray to see the latest version."
                                else -> "Choose the WhatsApp app that should receive this pack."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val personal = whatsAppStatus[WhatsAppTarget.PERSONAL]
                            val business = whatsAppStatus[WhatsAppTarget.BUSINESS]
                            val personalLabel = when {
                                personal?.installed == false -> "WhatsApp not installed"
                                personal?.added == true -> "Open WhatsApp"
                                else -> "Add to WhatsApp"
                            }
                            val businessLabel = when {
                                business?.installed == false -> "Business not installed"
                                business?.added == true -> "Open Business"
                                else -> "Add to Business"
                            }
                            if (maxWidth < 420.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = !busy && stickers.size in 3..30 && personal?.installed == true, onClick = onWhatsApp, modifier = Modifier.fillMaxWidth()) { Text(personalLabel) }
                                    OutlinedButton(enabled = !busy && stickers.size in 3..30 && business?.installed == true, onClick = onBusiness, modifier = Modifier.fillMaxWidth()) { Text(businessLabel) }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = !busy && stickers.size in 3..30 && personal?.installed == true, onClick = onWhatsApp, modifier = Modifier.weight(1f)) { Text(personalLabel) }
                                    OutlinedButton(enabled = !busy && stickers.size in 3..30 && business?.installed == true, onClick = onBusiness, modifier = Modifier.weight(1f)) { Text(businessLabel) }
                                }
                            }
                        }
                    }
                }
                WhatsAppTarget.entries.filter { whatsAppStatus[it]?.added == true }.forEach { target ->
                    TextButton(enabled = !busy && stickers.size in 3..30, onClick = { onAddAgain(target) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Add again to ${target.label}")
                    }
                }
                TextButton(onClick = { showWhatsApp = false }, modifier = Modifier.align(Alignment.End)) { Text("Done") }
            }
        }
    }
}


@Composable
private fun SelectionToolbar(count: Int, enabled: Boolean, onClear: () -> Unit, onTransfer: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().animateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
            if (maxWidth < 520.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("$count selected", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = onClear) { Text("Clear") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(enabled = enabled, onClick = onTransfer, modifier = Modifier.weight(1f)) { Text("Copy / move") }
                        OutlinedButton(enabled = enabled, onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$count selected", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onClear) { Text("Clear") }
                    FilledTonalButton(enabled = enabled, onClick = onTransfer) { Text("Copy / move") }
                    OutlinedButton(enabled = enabled, onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTile(
    item: StickerItem,
    kind: StickerKind,
    previewCache: StickerPreviewCache,
    files: StickerFiles,
    enabled: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val path = files.stickerFile(item.packId, item.fileName).path
    // BitmapFactory decodes a single poster for animated WebP too. Never hold
    // one native animation decoder per tile in a large/scrolling library.
    val image by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { previewCache.load(path, 256) }
    }
    Card(
        modifier = Modifier.semantics {
            this.selected = selected
            role = Role.Button
            contentDescription = buildString { append(item.displayName); if (item.tags.isNotBlank()) append(", tags ${item.tags}"); if (selected) append(", selected") }
        }.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Surface(Modifier.fillMaxSize(), MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
                        when {
                            image != null -> Image(image!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            else -> Text("Preview unavailable", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        }
                    }
                }
                if (selectionMode || selected) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(28.dp),
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(if (selected) "✓" else "○", color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
        }
    }
}

@Composable
fun WideLibraryPlaceholder(onCreatePack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        EmptyStateCard("Choose a pack", "Select a pack on the left to preview and manage its stickers without leaving your library.", "Create new pack", onCreatePack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerManageSheet(
    item: StickerItem,
    kind: StickerKind,
    files: StickerFiles,
    previewCache: StickerPreviewCache,
    canTransfer: Boolean,
    busy: Boolean,
    onEdit: () -> Unit,
    onEditArtwork: () -> Unit,
    onTransfer: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            StickerLargePreview(item, kind, files, previewCache)
            if (kind == StickerKind.STATIC) {
                Button(enabled = !busy, onClick = onEditArtwork, modifier = Modifier.fillMaxWidth()) { Text("Edit artwork and captions") }
            }
            if (item.tags.isNotBlank()) Text(item.tags, color = MaterialTheme.colorScheme.secondary)
            FilledTonalButton(enabled = !busy, onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit label and tags") }
            OutlinedButton(enabled = !busy, onClick = onTransfer, modifier = Modifier.fillMaxWidth()) { Text(if (canTransfer) "Copy or move to another pack" else "Copy or move to a new pack") }
            TextButton(enabled = !busy, onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Remove sticker", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StickerLargePreview(item: StickerItem, kind: StickerKind, files: StickerFiles, previewCache: StickerPreviewCache) {
    val path = files.stickerFile(item.packId, item.fileName).path
    val image by produceState<ImageBitmap?>(null, path, kind) { value = withContext(Dispatchers.IO) { if (kind == StickerKind.STATIC) previewCache.load(path, 512) else null } }
    val animation by produceState<android.graphics.drawable.Drawable?>(null, path, kind) {
        value = withContext(Dispatchers.IO) { if (kind == StickerKind.ANIMATED) try { ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path))) } catch (_: Exception) { null } else null }
    }
    AnimateWhileResumed(animation as? AnimatedImageDrawable)
    Surface(Modifier.fillMaxWidth().height(220.dp), MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
            when {
                kind == StickerKind.ANIMATED && animation != null -> AndroidView(
                    factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                    onRelease = { it.setImageDrawable(null) },
                    update = { it.setImageDrawable(animation) },
                    modifier = Modifier.fillMaxSize()
                )
                kind == StickerKind.ANIMATED -> Text("Animated preview unavailable")
                image != null -> Image(image!!, item.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else -> Text("Preview unavailable")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerEditSheet(
    item: StickerItem,
    label: String,
    tags: String,
    busy: Boolean,
    onLabelChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sticker details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(label, onLabelChange, label = { Text("Label") }, supportingText = { Text("Readable name used in search") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tags, onTagsChange, label = { Text("Tags") }, supportingText = { Text("Comma-separated, e.g. funny, work, reaction") }, modifier = Modifier.fillMaxWidth())
            Text("Stored file: ${item.fileName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = !busy, onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save changes") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerTransferSheet(
    title: String,
    packs: List<StickerPack>,
    targetId: String?,
    enabled: Boolean,
    onTarget: (String) -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onCreatePack: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Choose a compatible pack. StickerDock prevents duplicates and never mixes static and animated stickers.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(packs, key = { it.id }) { pack ->
                    FilterChip(selected = targetId == pack.id, onClick = { onTarget(pack.id) }, label = { Text(pack.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.fillMaxWidth())
                }
            }
            OutlinedTextField(newName, { newName = it.take(60) }, label = { Text("New pack name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(enabled = enabled && newName.isNotBlank(), onClick = { onCreatePack(newName); newName = "" }) { Text("Create new pack") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = enabled && targetId != null, onClick = onCopy, modifier = Modifier.weight(1f)) { Text("Copy") }
                Button(enabled = enabled && targetId != null, onClick = onMove, modifier = Modifier.weight(1f)) { Text("Move") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackOptionsSheet(pack: StickerPack, busy: Boolean, onRename: () -> Unit, onCategory: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(pack.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("${pack.category.label} • ${if (pack.kind == StickerKind.ANIMATED) "Animated" else "Static"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(enabled = !busy, onClick = onRename, modifier = Modifier.fillMaxWidth()) { Text("Rename pack") }
            OutlinedButton(enabled = !busy, onClick = onCategory, modifier = Modifier.fillMaxWidth()) { Text("Change topic") }
            TextButton(enabled = !busy, onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete pack", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SavedLinksDialog(
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
                Text("Paste public Sticker.ly pack links or codes. StickerDock saves bookmarks only and does not download creators' stickers.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(input, onInput, label = { Text("Links or codes, one per line") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                if (links.isEmpty()) Text("No bookmarks saved.", style = MaterialTheme.typography.bodySmall)
                else LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(links, key = { it.code }) { link ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onOpen(link) }, modifier = Modifier.weight(1f)) { Text(link.code, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                TextButton(onClick = { onRemove(link) }) { Text("Remove") }
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

@Composable
fun AnimatedScreenSwap(key: String, content: @Composable (String) -> Unit) {
    AnimatedContent(
        targetState = key,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
        label = "screen-swap"
    ) { targetKey -> content(targetKey) }
}
