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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalLayoutApi::class)
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
    packFilter: String = "All", onPackFilterChange: (String) -> Unit = {},
    favoriteStickers: List<StickerSearchResult> = emptyList(), favoriteCount: Int = 0,
    favoritesOnly: Boolean = false, onFavoritesOnlyChange: (Boolean) -> Unit = {},
    onOpenFavorite: (StickerItem) -> Unit = {}, onRemoveFavorite: (StickerItem) -> Unit = {},
    onQuickAddPack: (StickerPack) -> Unit = {},
    personalWhatsAppInstalled: Boolean = true,
    businessWhatsAppInstalled: Boolean = false,
    activeWhatsAppTarget: WhatsAppTarget = WhatsAppTarget.PERSONAL,
    onSelectWhatsAppTarget: (WhatsAppTarget) -> Unit = {},
    onOpenWhatsAppHub: () -> Unit = {}
) {
    val matchesByPack = remember(stickerMatches) { stickerMatches.groupBy { it.pack.id } }
    val visiblePackIds = remember(filteredPacks) { filteredPacks.mapTo(mutableSetOf()) { it.id } }
    val visibleStickers = remember(stickerMatches, visiblePackIds) { stickerMatches.filter { it.pack.id in visiblePackIds } }
    var resultsView by remember(searchText.isNotBlank()) { mutableStateOf("Stickers") }
    var isGridView by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val searchHistory = remember(context.applicationContext) { StickerSearchHistory(context.applicationContext) }
    var recentSearches by remember { mutableStateOf(searchHistory.snapshot()) }
    val quickSuggestions = listOf("monday mood", "thanks", "lol", "not today", "okay", "work done", "send help", "love this")

    fun onSelectSearch(term: String) {
        val cleaned = term.trim()
        if (cleaned.isNotBlank()) recentSearches = searchHistory.record(cleaned)
        onSearchChange(term)
    }

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
            if (packs.isNotEmpty() && searchText.isBlank() && !favoritesOnly) {
                item {
                    WhatsAppConnectHero(
                        personalInstalled = personalWhatsAppInstalled,
                        businessInstalled = businessWhatsAppInstalled,
                        activeTarget = activeWhatsAppTarget,
                        onSelectTarget = onSelectWhatsAppTarget,
                        onOpenHub = onOpenWhatsAppHub
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LibrarySearch(searchText, onSearchChange, "monday mood, thanks, lol…", Modifier.weight(1f),
                        accessibilityLabel = "Search library", onSubmit = { recentSearches = searchHistory.record(it) })
                    TextButton(onClick = onOpenFilters) { Text(if (activeFilterCount == 0) "Filters" else "Filters ($activeFilterCount)") }
                }
            }
            if (searchText.isBlank() && !favoritesOnly && packs.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        item { Text(if (recentSearches.isEmpty()) "Try:" else "Recent:",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(recentSearches.ifEmpty { quickSuggestions }) { suggestion ->
                            SuggestionChip(
                                onClick = { onSelectSearch(suggestion) },
                                label = { Text(suggestion, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                        if (recentSearches.isNotEmpty()) item {
                            TextButton(onClick = { searchHistory.clear(); recentSearches = emptyList() },
                                modifier = Modifier.semantics { contentDescription = "Clear recent searches" }) { Text("Clear") }
                        }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = !favoritesOnly && packFilter == "All",
                            onClick = { onFavoritesOnlyChange(false); onPackFilterChange("All") },
                            label = { Text("All packs") }
                        )
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = { onFavoritesOnlyChange(true) },
                            label = { Text("Favorites ($favoriteCount)") },
                            leadingIcon = { StickerDockNavIcon("Favorite") }
                        )
                        FilterChip(
                            selected = !favoritesOnly && packFilter == "Animated",
                            onClick = { onFavoritesOnlyChange(false); onPackFilterChange("Animated") },
                            label = { Text("Animated ✦") }
                        )
                        FilterChip(
                            selected = !favoritesOnly && packFilter == "Static",
                            onClick = { onFavoritesOnlyChange(false); onPackFilterChange("Static") },
                            label = { Text("Static") }
                        )
                }
            }
            if (drafts.isNotEmpty() && searchText.isBlank() && !favoritesOnly) item {
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
                    Text(if (favoritesOnly) "${favoriteStickers.size} favorite stickers" else if (searchText.isBlank()) "${packs.size} packs · ${counts.values.sum()} stickers" else "${visibleStickers.size} stickers · ${filteredPacks.size} packs",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!favoritesOnly && (searchText.isBlank() || resultsView == "Packs")) {
                        IconButton(
                            onClick = { isGridView = !isGridView },
                            modifier = Modifier.size(32.dp).semantics { contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view" }
                        ) {
                            StickerDockNavIcon(if (isGridView) "List" else "Grid")
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    TextButton(enabled = !busy, onClick = onCreatePack) { Text("New pack") }
                }
            }
            if (favoritesOnly) {
                if (favoriteStickers.isEmpty()) item {
                    EmptyStateCard(if (favoriteCount == 0) "Keep your best reactions here" else "No favorites match",
                        if (favoriteCount == 0) "Open a sticker and choose Add to favorites. It stays in its original pack, with the same privacy rating." else "Try another search or clear your filters.",
                        if (favoriteCount == 0) "Browse your packs" else "Clear search & filters",
                        { if (favoriteCount == 0) onFavoritesOnlyChange(false) else { onSearchChange(""); onClearFilters() } })
                }
                items(favoriteStickers, key = { it.item.id }) { result ->
                    FavoriteStickerCard(result, files, previewCache, !busy, { onOpenFavorite(result.item) }, { onRemoveFavorite(result.item) })
                }
                item { Text("Favorites stay on this device. Backups and cloud copies contain the saved stickers, without favorite shortcuts.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Spacer(Modifier.height(20.dp)) }
                return@LazyColumn
            }
            if (searchText.isNotBlank()) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Stickers", "Packs")) { view ->
                        FilterChip(selected = resultsView == view, onClick = { resultsView = view }, label = { Text(view) })
                    }
                }
            }
            if (searchText.isNotBlank() && resultsView == "Stickers") {
                items(visibleStickers, key = { it.item.id }) { result ->
                    SearchStickerCard(result, files, previewCache, counts[result.pack.id] ?: 0, !busy,
                        { onOpenFavorite(result.item) }, { onQuickAddPack(result.pack) })
                }
                if (visibleStickers.isEmpty()) item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No stickers match \"$searchText\"", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Try searching for one of these situations:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(quickSuggestions.take(4)) { suggestion ->
                                    SuggestionChip(
                                        onClick = { onSelectSearch(suggestion) },
                                        label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            TextButton(onClick = { resultsView = "Packs" }) { Text("Search in packs instead") }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
                return@LazyColumn
            }
            if (isGridView && !compactPane) {
                items(filteredPacks.chunked(2), key = { pair -> pair.joinToString("-") { it.id } }) { rowPacks ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (pack in rowPacks) {
                            Box(Modifier.weight(1f)) {
                                val matching = matchesByPack[pack.id].orEmpty()
                                val previews = if (searchText.isNotBlank() && matching.isNotEmpty()) matching.take(4).map { it.item } else previewItems[pack.id].orEmpty()
                                GridPackCard(pack, counts[pack.id] ?: 0, previews, files, previewCache,
                                    selectedPackId == pack.id, onClick = { onOpenPack(pack.id) },
                                    onQuickAdd = { onQuickAddPack(pack) })
                            }
                        }
                        if (rowPacks.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(filteredPacks, key = { it.id }) { pack ->
                    val matching = matchesByPack[pack.id].orEmpty()
                    VisualPackCard(pack, counts[pack.id] ?: 0,
                        if (searchText.isNotBlank() && matching.isNotEmpty()) matching.take(4).map { it.item } else previewItems[pack.id].orEmpty(),
                        files, previewCache, selectedPackId == pack.id,
                        if (searchText.isNotBlank() && matching.isNotEmpty()) "Matching stickers" else null,
                        onClick = { onOpenPack(pack.id) },
                        onQuickAdd = { onQuickAddPack(pack) })
                }
            }
            if (filteredPacks.isEmpty()) item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (searchText.isBlank()) "No packs match these filters" else "No matching packs for \"$searchText\"",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Try a situation reaction or clear filters:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(quickSuggestions.take(4)) { suggestion ->
                                SuggestionChip(
                                    onClick = { onSelectSearch(suggestion) },
                                    label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        TextButton(onClick = { onSearchChange(""); onClearFilters() }) { Text("Clear search & filters") }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
@Composable
private fun SearchStickerCard(result: StickerSearchResult, files: StickerFiles, previewCache: StickerPreviewCache,
    packCount: Int, enabled: Boolean, onOpen: () -> Unit, onAddPack: () -> Unit) {
    val visibleTags = remember(result.item.tags) {
        result.item.tags.split(',').map { it.trim() }.filter { it.isNotBlank() }.take(4)
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PackPreviewImage(files.stickerFile(result.pack.id, result.item.fileName).path,
                    result.item.displayName, previewCache, Modifier.size(88.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(result.item.displayName, style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(result.pack.name, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${if (result.pack.kind == StickerKind.ANIMATED) "Animated" else "Static"}${if (result.pack.rating == PackRating.MATURE) " · Private" else ""}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (visibleTags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(top = 2.dp)) {
                            items(visibleTags) { tag ->
                                SuggestionChip(onClick = {}, label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = enabled, onClick = onOpen) { Text("Preview") }
                TextButton(enabled = enabled && packCount >= 3, onClick = onAddPack) {
                    Text(if (packCount >= 3) "Add pack to WhatsApp" else "Add ${3 - packCount} more first")
                }
            }
        }
    }
}

@Composable
private fun FavoriteStickerCard(result: StickerSearchResult, files: StickerFiles, previewCache: StickerPreviewCache,
    enabled: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    Surface(onClick = onOpen, enabled = enabled, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PackPreviewImage(files.stickerFile(result.pack.id, result.item.fileName).path, result.item.displayName, previewCache, Modifier.size(80.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(result.item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(result.pack.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${if (result.pack.kind == StickerKind.ANIMATED) "Animated" else "Static"}${if (result.pack.rating == PackRating.MATURE) " · Private" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(enabled = enabled, onClick = onRemove, modifier = Modifier.semantics { contentDescription = "Remove ${result.item.displayName} from favorites" }) { StickerDockNavIcon("FavoriteFilled") }
        }
    }
}

@Composable
private fun LibraryOnboardingCard(busy: Boolean, onCreatePack: () -> Unit, onImportPhone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        StickerDockBrandLockup()
        Text("Your library starts here", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Build the reaction shelf you actually use. Make a sticker from your photos or bring over your existing WhatsApp folders. Your library stays on this phone until you choose to share or back it up.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProductAction("Choose photos", "Cut out a subject, add a caption and save your first pack", "Photo", !busy, onCreatePack)
        OutlinedButton(enabled = !busy, onClick = onImportPhone, modifier = Modifier.fillMaxWidth()) {
            StickerDockNavIcon("Import"); Spacer(Modifier.width(8.dp)); Text("Bring stickers from this phone")
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StickerDockNavIcon("Library")
                Text("Local by default · no account required", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun VisualPackCard(pack: StickerPack, stickerCount: Int, previews: List<StickerItem>, files: StickerFiles,
    previewCache: StickerPreviewCache, selected: Boolean, matchLabel: String?, onClick: () -> Unit,
    onQuickAdd: (() -> Unit)? = null) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected; role = Role.Button }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(pack.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).clickable(onClick = onClick))
                        if (pack.kind == StickerKind.ANIMATED) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text("✦ Motion", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(matchLabel ?: buildString {
                        append("$stickerCount ${if (stickerCount == 1) "sticker" else "stickers"}")
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
            if (onQuickAdd != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    WhatsAppActionButton(stickerCount = stickerCount, enabled = true, onClick = onQuickAdd)
                }
            }
        }
    }
}

@Composable
private fun GridPackCard(
    pack: StickerPack,
    stickerCount: Int,
    previews: List<StickerItem>,
    files: StickerFiles,
    previewCache: StickerPreviewCache,
    selected: Boolean,
    onClick: () -> Unit,
    onQuickAdd: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected; role = Role.Button }
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(4.dp)
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (idx in 0..1) {
                            val item = previews.getOrNull(idx)
                            if (item != null) {
                                PackPreviewImage(
                                    files.stickerFile(pack.id, item.fileName).path,
                                    item.displayName,
                                    previewCache,
                                    Modifier.weight(1f).fillMaxHeight()
                                )
                            } else {
                                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    if (idx == 0) StickerDockNavIcon("Create")
                                }
                            }
                        }
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (idx in 2..3) {
                            val item = previews.getOrNull(idx)
                            if (item != null) {
                                PackPreviewImage(
                                    files.stickerFile(pack.id, item.fileName).path,
                                    item.displayName,
                                    previewCache,
                                    Modifier.weight(1f).fillMaxHeight()
                                )
                            } else {
                                Box(Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        pack.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clickable(onClick = onClick)
                    )
                    if (pack.kind == StickerKind.ANIMATED) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text("✦", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$stickerCount ${if (stickerCount == 1) "sticker" else "stickers"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    WhatsAppActionButton(
                        stickerCount = stickerCount,
                        enabled = true,
                        onClick = onQuickAdd
                    )
                }
            }
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
fun CreateScreen(
    busy: Boolean,
    onPhotos: () -> Unit,
    onClip: () -> Unit,
    onImport: () -> Unit,
    onCreateEmptyPack: () -> Unit = {},
    onGenerateAi: ((String) -> Unit)? = null
) {
    var prompt by rememberSaveable { mutableStateOf("") }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.fillMaxHeight().widthIn(max = 800.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sticker Studio", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Turn thoughts, photos or video clips into stickers ready for WhatsApp.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // AI Prompt Studio Card
            item {
                AiStudioCard(
                    promptText = prompt,
                    onPromptChange = { prompt = it },
                    onGenerate = { onGenerateAi?.invoke(prompt) },
                    busy = busy
                )
            }

            // Media creators
            item {
                CreationCard("Make stickers from photos", "Smart cutout from photos", "Pick camera or gallery photos. Automatic cutout, stroke outline and custom captions.", "Photo", !busy, onPhotos, prominent = true)
            }
            item {
                CreationCard("Make an animated reaction", "Looped moving sticker", "Trim a video or GIF, add text overlay, and convert into a 512×512 animated WebP.", "Video", !busy, onClip, prominent = false)
            }
            item {
                Surface(
                    onClick = onCreateEmptyPack,
                    enabled = !busy,
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                StickerDockNavIcon("Create")
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Create empty pack", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Name a custom pack and add stickers manually over time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StickerDockNavIcon("Forward")
                    }
                }
            }
            item { CreationFlowCard() }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun SettingsScreen(
    personalInstalled: Boolean,
    businessInstalled: Boolean,
    activeTarget: WhatsAppTarget,
    onSelectTarget: (WhatsAppTarget) -> Unit,
    onOpenWhatsAppHub: () -> Unit,
    onImportFolder: () -> Unit,
    onImportZip: () -> Unit,
    onBackupEditable: () -> Unit,
    onBackupZip: () -> Unit,
    onCloud: () -> Unit,
    cloudEnabled: Boolean,
    onToggleMature: () -> Unit,
    showMature: Boolean,
    onRoadmap: () -> Unit,
    onClearCache: () -> Unit,
    busy: Boolean
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.fillMaxHeight().widthIn(max = 800.dp),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Settings & Tools", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Manage WhatsApp handoff, phone migration, backups and content preferences.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // WhatsApp section
            item {
                Text("WHATSAPP INTEGRATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            item {
                WhatsAppConnectHero(
                    personalInstalled = personalInstalled,
                    businessInstalled = businessInstalled,
                    activeTarget = activeTarget,
                    onSelectTarget = onSelectTarget,
                    onOpenHub = onOpenWhatsAppHub
                )
            }

            // Migration & Import
            item {
                Text("IMPORT & MIGRATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            item {
                ProductAction(
                    title = "Bring stickers from this phone",
                    description = "Scan local WhatsApp or camera folders with deduplication and progress resume",
                    icon = "Import",
                    enabled = !busy,
                    onClick = onImportFolder
                )
            }
            item {
                ProductAction(
                    title = "Import shared ZIP archive",
                    description = "Bring StickerDock, Telegram or portable sticker archives into your collection",
                    icon = "Library",
                    enabled = !busy,
                    onClick = onImportZip
                )
            }

            // Backup & Cloud
            item {
                Text("BACKUP & CLOUD STORAGE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            item {
                ProductAction(
                    title = "Export full editable backup",
                    description = "Retains originals, masks and drafts for complete Android restore",
                    icon = "Cloud",
                    enabled = !busy,
                    onClick = onBackupEditable
                )
            }
            item {
                ProductAction(
                    title = "Export portable sticker backup",
                    description = "Standard ZIP with clean 512×512 WebP stickers for any device",
                    icon = "Library",
                    enabled = !busy,
                    onClick = onBackupZip
                )
            }
            item {
                ProductAction(
                    title = if (cloudEnabled) "Private Cloud Library · Active" else "Private Cloud Library",
                    description = "Optional encrypted remote backup and multi-device sync",
                    icon = "Cloud",
                    enabled = !busy,
                    onClick = onCloud
                )
            }

            // Privacy & Content
            item {
                Text("PRIVACY & VAULT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            item {
                Surface(
                    onClick = onToggleMature,
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Private Mature Content Vault", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Keep mature reaction packs segregated and private from general discovery",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = showMature, onCheckedChange = { onToggleMature() })
                    }
                }
            }

            // Diagnostics & About
            item {
                Text("ABOUT & HEALTH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StickerDockBrandLockup(compact = true)
                            Spacer(Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("v0.12.0", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("StickerDock Duo • Native Android & WhatsApp Provider",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onRoadmap) { Text("Roadmap") }
                            TextButton(onClick = onClearCache) { Text("Clear preview cache") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CreationFlowCard() {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                "1" to "Pick a photo, video or GIF",
                "2" to "Cut out, crop and caption",
                "3" to "Save to a pack and send"
            ).forEach { (number, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            Text(number, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
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
                    StickerDockAction("Back up library", "Keep editable projects or export portable stickers", !busy, false, onBackup),
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
    onAddAgain: (WhatsAppTarget) -> Unit = {},
    favoriteIds: Set<String> = emptySet()
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
                        onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onStartSelection(item) },
                        favorite = item.id in favoriteIds
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
    onLongClick: () -> Unit,
    favorite: Boolean = false
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
                if (favorite && !selectionMode) {
                    Surface(Modifier.align(Alignment.TopStart).padding(5.dp).size(28.dp).semantics { contentDescription = "Favorite" },
                        shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .95f)) {
                        Box(contentAlignment = Alignment.Center) { StickerDockNavIcon("FavoriteFilled") }
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
fun WhatsAppHubSheet(packs: List<StickerPack>, counts: Map<String, Int>, busy: Boolean,
    onPack: (StickerPack) -> Unit, onAdd: (StickerPack) -> Unit, onCreate: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = WhatsAppGreen, contentColor = androidx.compose.ui.graphics.Color.White) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { StickerDockNavIcon("WhatsApp") }
                }
                Column {
                    Text("Your WhatsApp packs", style = MaterialTheme.typography.headlineSmall)
                    Text("Choose a pack to add", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("WhatsApp asks you to confirm each pack. If both WhatsApp and Business are installed, choose the app when adding.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (packs.isEmpty()) {
                EmptyStateCard("No packs yet", "Create or import a pack with at least three stickers to use it in WhatsApp.", "Create stickers", onCreate)
            } else {
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(packs, key = { it.id }) { candidate ->
                        val count = counts[candidate.id] ?: 0
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(if (count < 3) "$count stickers · add ${3 - count} more" else "$count stickers · ${if (candidate.kind == StickerKind.ANIMATED) "Animated" else "Static"}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (count < 3) TextButton(enabled = !busy, onClick = { onPack(candidate) }) { Text("Open") }
                                else FilledTonalButton(enabled = !busy, onClick = { onAdd(candidate) }) { Text("Add") }
                            }
                        }
                    }
                }
            }
            Text("StickerDock can send its own packs; it cannot manage stickers from other apps or choose between accounts inside one WhatsApp app.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
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
    onDismiss: () -> Unit,
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            StickerLargePreview(item, kind, files, previewCache)
            if (onToggleFavorite != null) OutlinedButton(enabled = !busy, onClick = onToggleFavorite, modifier = Modifier.fillMaxWidth()) {
                StickerDockNavIcon(if (favorite) "FavoriteFilled" else "Favorite")
                Spacer(Modifier.width(8.dp))
                Text(if (favorite) "Remove from favorites" else "Add to favorites")
            }
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
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Keep the actions reachable with long libraries, large text and the keyboard.
            // The form and destinations share one scroll area rather than competing for height.
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
                item { Text("Choose a compatible pack. StickerDock prevents duplicates and never mixes static and animated stickers.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { OutlinedTextField(newName, { newName = it.take(60) }, label = { Text("New pack name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { TextButton(enabled = enabled && newName.isNotBlank(), onClick = { onCreatePack(newName); newName = "" }) { Text("Create new pack") } }
                items(packs, key = { it.id }) { pack ->
                    FilterChip(selected = targetId == pack.id, onClick = { onTarget(pack.id) }, label = { Text(pack.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.fillMaxWidth())
                }
            }
            packs.firstOrNull { it.id == targetId }?.let { target ->
                Text("To: ${target.name}", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
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
