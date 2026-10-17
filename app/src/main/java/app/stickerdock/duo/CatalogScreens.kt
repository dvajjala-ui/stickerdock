package app.stickerdock.duo

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FactoryScreen(
    loading: Boolean,
    packs: List<CommunityCatalog.Listing>,
    error: String?,
    importedIds: Set<String>,
    busy: Boolean,
    onRefresh: () -> Unit,
    onPack: (CommunityCatalog.Listing) -> Unit,
    onPreview: suspend (CommunityCatalog.Listing) -> ByteArray?,
    previewRevision: Int = 0,
    onHide: ((CommunityCatalog.Listing) -> Unit)? = null,
    onReport: ((CommunityCatalog.Listing) -> Unit)? = null,
    onShowHidden: (() -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf<StickerKind?>(null) }
    val visible = packs.filter { entry ->
        (query.isBlank() || entry.name.contains(query, true) || entry.creator.contains(query, true) ||
            entry.categories.any { it.label.contains(query, true) }) &&
            (selectedKind == null || selectedKind in entry.kinds)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(columns = GridCells.Adaptive(240.dp), modifier = Modifier.fillMaxHeight().widthIn(max = 960.dp),
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Find your next reaction", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Preview a pack. Save the ones you love.", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(enabled = !loading, onClick = onRefresh) { Text("Refresh") }
                    }
                    OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search packs, creators or topics") })
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selectedKind == null, { selectedKind = null }, { Text("All packs") }) }
                        items(StickerKind.entries) { kind ->
                            FilterChip(selectedKind == kind, { selectedKind = kind },
                                { Text(if (kind == StickerKind.STATIC) "Static" else "Animated") })
                        }
                    }
                }
            }
            if (loading) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (error != null) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyStateCard("Couldn't refresh Discover", "Your saved packs are safe. Check your connection and try again.", "Try again", onRefresh)
            }
            when {
                !loading && error == null && packs.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyStateCard("More reactions are on the way", "Reviewed packs will appear here. Create or import your own stickers in the meantime.")
                }
                !loading && packs.isNotEmpty() && visible.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyStateCard("No matching packs", "Try another search or show all packs.", "Clear filters", { query = ""; selectedKind = null })
                }
                else -> gridItems(visible, key = { it.id + it.sha256 }) { entry ->
                    Card(onClick = { if (!busy) onPack(entry) }, modifier = Modifier.fillMaxWidth()) {
                        CatalogArtwork(entry, onPreview, compact = true, revision = previewRevision)
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(entry.creator + (entry.stickerCount?.let { " · $it stickers" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (entry.categories.isNotEmpty()) Text(entry.categories.joinToString(" · ") { it.label }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            FilledTonalButton(enabled = !busy, onClick = { onPack(entry) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (entry.id in importedIds) "Open in library" else "Preview pack")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (onHide != null) TextButton(onClick = { onHide(entry) }) { Text(if (entry.publisherId.isBlank()) "Hide pack" else "Hide creator") }
                                if (onReport != null) TextButton(onClick = { onReport(entry) }) { Text("Report") }
                            }
                        }
                    }
                }
            }
            if (onShowHidden != null) item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = onShowHidden) { Text("Reset hidden packs & creators") }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private data class CatalogArtworkState(val bitmap: ImageBitmap? = null, val loading: Boolean = true, val failed: Boolean = false)

@Composable
private fun CatalogArtwork(entry: CommunityCatalog.Listing, load: suspend (CommunityCatalog.Listing) -> ByteArray?,
    compact: Boolean = false, revision: Int = 0) {
    var attempt by remember(entry.id, entry.sha256) { mutableIntStateOf(0) }
    val state by produceState(CatalogArtworkState(), entry.id, entry.sha256, attempt, revision) {
        value = CatalogArtworkState()
        value = try {
            val image = withContext(Dispatchers.IO) {
                load(entry)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
            }
            CatalogArtworkState(image, loading = false)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { CatalogArtworkState(loading = false, failed = true) }
    }
    val artworkSize = if (compact) Modifier.height(168.dp) else Modifier.aspectRatio(1.5f)
    Box(Modifier.fillMaxWidth().then(artworkSize).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        when {
            state.bitmap != null -> Image(state.bitmap!!, "Sticker previews from ${entry.name}",
                Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
            state.loading -> CircularProgressIndicator(Modifier.size(24.dp))
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("✦", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                Text(if (state.failed) "Preview couldn't load" else "Tap Preview pack to see the stickers",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.failed) TextButton(onClick = { attempt++ }) { Text("Retry preview") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogPackSheet(entry: CommunityCatalog.Listing, saved: Boolean, busy: Boolean, saveError: String?,
    onPreview: suspend (CommunityCatalog.Listing) -> ByteArray?, onSave: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(entry.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("By ${entry.creator}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CatalogArtwork(entry, onPreview)
                Text("A preview of this pack. Saving adds its stickers to your offline library. You can then add the pack to WhatsApp.",
                    style = MaterialTheme.typography.bodyMedium)
                if (saveError != null) Text(saveError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(enabled = !busy, onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Saving pack…" else if (saved) "Open in library" else "Save to library")
            }
            TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Keep browsing") }
        }
    }
}
