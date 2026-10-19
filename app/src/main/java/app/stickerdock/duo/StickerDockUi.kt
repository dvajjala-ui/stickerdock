package app.stickerdock.duo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class StickerDockAction(
    val title: String,
    val description: String = "",
    val enabled: Boolean = true,
    val primary: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun StickerDockHero(
    packCount: Int,
    stickerCount: Int,
    busy: Boolean,
    onCreatePack: () -> Unit,
    onImportPhone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Your sticker library",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "Organize personal sticker packs, find anything quickly, and keep them ready for WhatsApp.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("$packCount packs")
                SummaryPill("$stickerCount stickers")
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 420.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCreatePack, modifier = Modifier.fillMaxWidth()) { Text("Create pack") }
                        FilledTonalButton(
                            enabled = !busy,
                            onClick = onImportPhone,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Import from phone") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCreatePack, modifier = Modifier.weight(1f)) { Text("Create pack") }
                        FilledTonalButton(
                            enabled = !busy,
                            onClick = onImportPhone,
                            modifier = Modifier.weight(1f)
                        ) { Text("Import from phone") }
                    }
                }
            }
        }
    }
}

@Composable
fun StickerDockSectionHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StickerDockActionGrid(actions: List<StickerDockAction>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.chunked(2).forEach { rowActions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowActions.forEach { action ->
                            ActionButton(action, Modifier.weight(1f))
                        }
                        if (rowActions.size == 1) {
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { action -> ActionButton(action, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun ActionButton(action: StickerDockAction, modifier: Modifier) {
    if (action.primary) {
        FilledTonalButton(enabled = action.enabled, onClick = action.onClick, modifier = modifier,
            shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(16.dp)) {
            ActionButtonText(action)
        }
    } else {
        OutlinedButton(enabled = action.enabled, onClick = action.onClick, modifier = modifier,
            shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(16.dp)) {
            ActionButtonText(action)
        }
    }
}

@Composable
private fun ActionButtonText(action: StickerDockAction) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(action.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        if (action.description.isNotBlank()) {
            Text(
                action.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PackListCard(pack: StickerPack, stickerCount: Int, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (pack.kind == StickerKind.ANIMATED)
                    MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (pack.kind == StickerKind.ANIMATED) "GIF" else "STK",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    pack.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallPill(pack.category.label)
                    SmallPill(if (pack.kind == StickerKind.ANIMATED) "Animated" else "Static")
                    if (pack.rating == PackRating.MATURE) SmallPill("Mature", true)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$stickerCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "stickers",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StickerSearchResultCard(result: StickerSearchResult, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(result.item.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                "${result.pack.name} • ${result.pack.category.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (result.item.tags.isNotBlank()) {
                Text(result.item.tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun ImportProgressCard(
    progress: FolderStickerImporter.Progress,
    pauseRequested: Boolean,
    onPause: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (progress.resumed) "Resuming phone import" else "Importing from phone",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                "${progress.filesScanned} scanned • ${progress.added} added • ${progress.duplicates} duplicate • ${progress.rejected} rejected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            progress.currentFile?.let {
                Text(
                    it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            OutlinedButton(enabled = !pauseRequested, onClick = onPause) {
                Text(if (pauseRequested) "Pausing…" else "Pause import")
            }
        }
    }
}

@Composable
fun PendingImportCard(onResume: () -> Unit, onDiscard: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Paused import ready",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                "Continue the saved phone-folder scan, or discard only this unfinished import session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onResume) { Text("Resume") }
                TextButton(onClick = onDiscard) { Text("Discard") }
            }
        }
    }
}

@Composable
fun EmptyStateCard(title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) {
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun PackHeaderCard(pack: StickerPack, stickerCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                pack.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallPill(pack.category.label)
                SmallPill(if (pack.kind == StickerKind.ANIMATED) "Animated" else "Static")
                SmallPill("$stickerCount / 30")
                if (pack.rating == PackRating.MATURE) SmallPill("Mature", true)
            }
        }
    }
}

@Composable
private fun SummaryPill(label: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SmallPill(label: String, danger: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
