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
    packs: List<StickerPack>,
    counts: Map<String, Int>,
    previewItems: Map<String, List<StickerItem>>,
    filteredPacks: List<StickerPack>,
    searchText: String,
    stickerMatches: List<StickerSearchResult>,
    activeFilterCount: Int,
    selectedPackId: String?,
    busy: Boolean,
    files: StickerFiles,
    previewCache: StickerPreviewCache,
    compactPane: Boolean,
    onSearchChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onCreatePack: () -> Unit,
    onImportPhone: () -> Unit,
    onOpenPack: (String) -> Unit,
    onPhotos: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight().widthIn(max = if (compactPane) 520.dp else 980.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (packs.isEmpty() && searchText.isBlank()) {
                item { LibraryOnboardingCard(busy, onPhotos, onImportPhone) }
            } else {
                item { LibrarySummaryCard(packs.size, counts.values.sum(), compactPane) }
                item {
                    FilledTonalButton(enabled = !busy, onClick = onPhotos, modifier = Modifier.fillMaxWidth()) { Text("Make stickers from photos") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = onSearchChange,
                        label = { Text("Search library") },
                        placeholder = { Text("Pack, sticker label, tag or topic") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(onClick = onOpenFilters) {
                            Text(if (activeFilterCount == 0) "Filters" else "Filters ($activeFilterCount)")
                        }
                        if (activeFilterCount > 0) TextButton(onClick = onClearFilters) { Text("Clear") }
                        Spacer(Modifier.weight(1f))
                        if (searchText.isNotBlank()) {
                            Text(
                                "${filteredPacks.size + stickerMatches.size} results",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (packs.isEmpty() && searchText.isBlank()) return@LazyColumn
            if (filteredPacks.isNotEmpty()) {
                item {
                    StickerDockSectionHeader(
                        if (searchText.isBlank()) "Your packs" else "Matching packs",
                        "${filteredPacks.size} shown • ${packs.size} total"
                    )
                }
                items(filteredPacks, key = { it.id }) { pack ->
                    VisualPackCard(
                        pack = pack,
                        stickerCount = counts[pack.id] ?: 0,
                        previews = previewItems[pack.id].orEmpty(),
                        files = files,
                        previewCache = previewCache,
                        selected = selectedPackId == pack.id,
                        onClick = { onOpenPack(pack.id) }
                    )
                }
            } else if (searchText.isBlank()) {
                item {
                    EmptyStateCard(
                        "No packs match these filters",
                        "Clear the filters or choose another type, topic or sort order.",
                        "Clear filters",
                        onClearFilters
                    )
                }
            }
            if (searchText.isNotBlank()) {
                item {
                    StickerDockSectionHeader(
                        "Matching stickers",
                        if (stickerMatches.isEmpty()) "No sticker metadata matched" else "${stickerMatches.size} sticker results"
                    )
                }
                if (stickerMatches.isEmpty() && filteredPacks.isEmpty()) {
                    item { EmptyStateCard("Nothing found", "Try a shorter name, another tag, or clear a filter.") }
                } else {
                    items(stickerMatches, key = { it.item.id }) { result ->
                        StickerSearchResultCard(result) { onOpenPack(result.pack.id) }
                    }
                }
            }
            item { Spacer(Modifier.height(76.dp)) }
        }
    }
}

@Composable
private fun LibrarySummaryCard(packCount: Int, stickerCount: Int, compact: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(if (compact) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Your sticker library",
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text("$packCount packs • $stickerCount stickers", color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "Search first, filter only when you need to, and open a pack to manage or export it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun LibraryOnboardingCard(busy: Boolean, onCreatePack: () -> Unit, onImportPhone: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Build your first sticker pack",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "StickerDock keeps your collection local and helps turn loose sticker files into reusable WhatsApp packs.",
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            OnboardingStep("1", "Create or import", "Start a fresh pack or scan a sticker folder on your phone.")
            OnboardingStep("2", "Organize", "Label, tag, move and group stickers so they stay easy to find.")
            OnboardingStep("3", "Use anywhere", "Add a finished pack to WhatsApp or share a portable StickerDock ZIP.")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 420.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = onCreatePack, modifier = Modifier.fillMaxWidth()) { Text("Choose photos") }
                        FilledTonalButton(enabled = !busy, onClick = onImportPhone, modifier = Modifier.fillMaxWidth()) { Text("Import from phone") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = onCreatePack, modifier = Modifier.weight(1f)) { Text("Choose photos") }
                        FilledTonalButton(enabled = !busy, onClick = onImportPhone, modifier = Modifier.weight(1f)) { Text("Import from phone") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingStep(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(Modifier.size(32.dp), CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)) {
            Box(contentAlignment = Alignment.Center) { Text(number, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VisualPackCard(
    pack: StickerPack,
    stickerCount: Int,
    previews: List<StickerItem>,
    files: StickerFiles,
    previewCache: StickerPreviewCache,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected; role = Role.Button }.combinedClickable(onClick = onClick),
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors()
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StickerCollage(pack, previews, files, previewCache)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(pack.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${pack.category.label} • ${if (pack.kind == StickerKind.ANIMATED) "Animated" else "Static"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$stickerCount sticker${if (stickerCount == 1) "" else "s"}${if (pack.rating == PackRating.MATURE) " • Mature" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (pack.rating == PackRating.MATURE) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StickerCollage(pack: StickerPack, previews: List<StickerItem>, files: StickerFiles, previewCache: StickerPreviewCache) {
    Surface(Modifier.size(82.dp), MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        if (previews.isEmpty()) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (pack.kind == StickerKind.ANIMATED) "GIF" else "STK", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        } else {
            Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                previews.take(4).chunked(2).forEach { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { item ->
                            PackPreviewImage(
                                files.stickerFile(pack.id, item.fileName).path,
                                item.displayName,
                                previewCache,
                                Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PackPreviewImage(path: String, description: String, previewCache: StickerPreviewCache, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, path) { value = withContext(Dispatchers.IO) { previewCache.load(path) } }
    Box(modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        if (image == null) Text("•", color = MaterialTheme.colorScheme.outline)
        else Image(image!!, description, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Filter your library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Keep the main Library clean; narrow it down only when you need to.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onPhotos: () -> Unit
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

@Composable
fun FactoryScreen(
    loading: Boolean,
    packs: List<CommunityCatalog.Listing>,
    error: String?,
    importedIds: Set<String>,
    busy: Boolean,
    onRefresh: () -> Unit,
    onPack: (CommunityCatalog.Listing) -> Unit,
    onHide: ((CommunityCatalog.Listing) -> Unit)? = null,
    onReport: ((CommunityCatalog.Listing) -> Unit)? = null,
    onShowHidden: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.fillMaxHeight().widthIn(max = 960.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("New stickers", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text("Fresh packs ready for your chats", color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            TextButton(enabled = !loading, onClick = onRefresh) { Text("Refresh") }
                        }
                        Text("New approved packs are added to your library automatically. Your saved stickers keep working offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        if (onShowHidden != null) TextButton(onClick = onShowHidden) { Text("Reset hidden packs & creators") }
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            when {
                error != null -> item { EmptyStateCard("New stickers unavailable", "Your saved stickers still work offline. Try again when the network is available.", "Try again", onRefresh) }
                !loading && packs.isEmpty() -> item { EmptyStateCard("Nothing new yet", "Fresh sticker packs will appear here automatically.") }
                else -> items(packs, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (entry.categories.isNotEmpty()) Text(entry.categories.joinToString(" • ") { it.label }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            FilledTonalButton(enabled = !busy, onClick = { onPack(entry) }, modifier = Modifier.fillMaxWidth()) { Text(if (entry.id in importedIds) "Open in library" else "Add to library") }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (onHide != null) TextButton(onClick = { onHide(entry) }) { Text(if (entry.publisherId.isBlank()) "Hide pack" else "Hide creator") }
                                if (onReport != null) TextButton(onClick = { onReport(entry) }) { Text("Report") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    onBulkDelete: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(142.dp),
            modifier = Modifier.fillMaxSize().widthIn(max = 1120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { PackHeaderCard(pack, stickers.size) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 520.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy && stickers.size < 30, onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import stickers") }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(enabled = !busy, onClick = onPackOptions, modifier = Modifier.weight(1f)) { Text("Pack options") }
                                OutlinedButton(enabled = !busy && stickers.isNotEmpty(), onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share ZIP") }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy && stickers.size < 30, onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import stickers") }
                            OutlinedButton(enabled = !busy, onClick = onPackOptions, modifier = Modifier.weight(1f)) { Text("Pack options") }
                            OutlinedButton(enabled = !busy && stickers.isNotEmpty(), onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share ZIP") }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
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
                                    Button(enabled = !busy && stickers.size in 3..30 && personal?.installed != false, onClick = onWhatsApp, modifier = Modifier.fillMaxWidth()) { Text(personalLabel) }
                                    OutlinedButton(enabled = !busy && stickers.size in 3..30 && business?.installed != false, onClick = onBusiness, modifier = Modifier.fillMaxWidth()) { Text(businessLabel) }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = !busy && stickers.size in 3..30 && personal?.installed != false, onClick = onWhatsApp, modifier = Modifier.weight(1f)) { Text(personalLabel) }
                                    OutlinedButton(enabled = !busy && stickers.size in 3..30 && business?.installed != false, onClick = onBusiness, modifier = Modifier.weight(1f)) { Text(businessLabel) }
                                }
                            }
                        }
                    }
                }
            }
            if (selectedStickerIds.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SelectionToolbar(selectedStickerIds.size, !busy, onClearSelection, onBulkTransfer, onBulkDelete) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                StickerDockSectionHeader("Stickers", if (selectedStickerIds.isEmpty()) "Long-press a sticker to select multiple" else "Tap more stickers to add or remove them from selection")
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
    val image by produceState<ImageBitmap?>(null, path, kind) { value = withContext(Dispatchers.IO) { if (kind == StickerKind.STATIC) previewCache.load(path) else null } }
    val animation by produceState<android.graphics.drawable.Drawable?>(null, path, kind) {
        value = withContext(Dispatchers.IO) { if (kind == StickerKind.ANIMATED) try { ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path))) } catch (_: Exception) { null } else null }
    }
    DisposableEffect(animation) {
        (animation as? AnimatedImageDrawable)?.start()
        onDispose { (animation as? AnimatedImageDrawable)?.stop() }
    }
    Card(
        modifier = Modifier.semantics {
            this.selected = selected
            role = Role.Button
            contentDescription = buildString { append(item.displayName); if (item.tags.isNotBlank()) append(", tags ${item.tags}"); if (selected) append(", selected") }
        }.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Surface(Modifier.fillMaxSize(), MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                    Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
                        when {
                            kind == StickerKind.ANIMATED && animation != null -> AndroidView(
                                factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                update = { it.setImageDrawable(animation) },
                                modifier = Modifier.fillMaxSize()
                            )
                            kind == StickerKind.ANIMATED -> Text("Preview unavailable", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
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
            Text(item.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.tags.isNotBlank()) Text(item.tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    val image by produceState<ImageBitmap?>(null, path, kind) { value = withContext(Dispatchers.IO) { if (kind == StickerKind.STATIC) previewCache.load(path) else null } }
    val animation by produceState<android.graphics.drawable.Drawable?>(null, path, kind) {
        value = withContext(Dispatchers.IO) { if (kind == StickerKind.ANIMATED) try { ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path))) } catch (_: Exception) { null } else null }
    }
    DisposableEffect(animation) {
        (animation as? AnimatedImageDrawable)?.start()
        onDispose { (animation as? AnimatedImageDrawable)?.stop() }
    }
    Surface(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 320.dp), MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
            when {
                kind == StickerKind.ANIMATED && animation != null -> AndroidView(
                    factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
