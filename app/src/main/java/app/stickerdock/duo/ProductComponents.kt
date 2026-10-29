package app.stickerdock.duo

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val WhatsAppGreen = Color(0xFF087F5B)

@Composable
fun StickerDockBrandLockup(compact: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        val ink = MaterialTheme.colorScheme.onPrimaryContainer
        val lime = MaterialTheme.colorScheme.primaryContainer
        val paper = MaterialTheme.colorScheme.surface
        Canvas(Modifier.size(if (compact) 32.dp else 44.dp)) {
            val u = size.width / 44f
            drawRoundRect(lime, Offset(7f * u, 2f * u),
                Size(35f * u, 35f * u), CornerRadius(9f * u))
            drawRoundRect(paper, Offset(2f * u, 7f * u),
                Size(35f * u, 35f * u), CornerRadius(9f * u))
            val bubble = Path().apply {
                moveTo(12f * u, 16f * u); lineTo(28f * u, 16f * u)
                cubicTo(32f * u, 16f * u, 34f * u, 18f * u, 34f * u, 22f * u)
                lineTo(34f * u, 26f * u); cubicTo(34f * u, 30f * u, 32f * u, 32f * u, 28f * u, 32f * u)
                lineTo(19f * u, 32f * u); lineTo(11f * u, 37f * u); lineTo(13f * u, 31f * u)
                cubicTo(10f * u, 30f * u, 8f * u, 28f * u, 8f * u, 25f * u)
                lineTo(8f * u, 22f * u); cubicTo(8f * u, 18f * u, 10f * u, 16f * u, 12f * u, 16f * u); close()
            }
            drawPath(bubble, ink)
            val bolt = Path().apply {
                moveTo(18f * u, 24f * u); lineTo(24f * u, 18f * u); lineTo(24f * u, 22f * u)
                lineTo(30f * u, 22f * u); lineTo(23f * u, 29f * u); lineTo(23f * u, 25f * u); close()
            }
            drawPath(bolt, lime)
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text("StickerDock", style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            if (!compact) Text("YOUR REACTIONS, READY TO SEND", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CreationCard(action: String, title: String, description: String, icon: String, enabled: Boolean,
                 onClick: () -> Unit, prominent: Boolean = true) {
    Surface(shape = MaterialTheme.shapes.extraLarge,
        color = if (prominent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (prominent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (prominent) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface.copy(alpha = .92f)) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { StickerDockNavIcon(icon) }
                }
                Spacer(Modifier.weight(1f))
                Text(if (prominent) "START WITH PHOTOS" else "START WITH MOTION", style = MaterialTheme.typography.labelSmall)
            }
            Text(title, style = if (prominent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (prominent) Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentColor = MaterialTheme.colorScheme.primaryContainer)) { Text(action) }
            else OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}

@Composable
fun LibrarySearch(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
                  accessibilityLabel: String = label, onSubmit: (String) -> Unit = {}) {
    val focusManager = LocalFocusManager.current
    TextField(value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit(value); focusManager.clearFocus() }),
        leadingIcon = { StickerDockNavIcon("Search") },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") },
            modifier = Modifier.semantics { contentDescription = "Clear search" }) { StickerDockNavIcon("Close") } },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        modifier = modifier.semantics { contentDescription = accessibilityLabel })
}

@Composable
fun ProductAction(title: String, description: String, icon: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { StickerDockNavIcon(icon) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StickerDockNavIcon("Forward")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettingsSheet(onAccount: () -> Unit, onImport: () -> Unit, onRoadmap: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Library settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            ProductAction("Account & cloud backup", "Manage your optional private backup", "Cloud", onClick = onAccount)
            ProductAction("Import and backups", "Bring your collection from another phone or file", "Import", onClick = onImport)
            ProductAction("Coming next", "See planned features without mixing unfinished tools into the app", "Forward", onClick = onRoadmap)
            Text("Your packs stay on this device unless you choose to back up or share them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductRoadmapSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Coming next", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("These ideas are visible here so the everyday Library, Create and Discover flows stay focused. They are not active controls yet.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RoadmapItem("Smarter sticker suggestions", "Reviewable tag and caption suggestions for your own images.")
            RoadmapItem("Community profiles", "Creator identity, reports, blocking, age protection and moderation before public uploads.")
            RoadmapItem("More reviewed packs", "Daily source collection feeds private review first; only rights-cleared packs reach Discover.")
            RoadmapItem("Large-library search", "Faster indexed search and richer filters for very large local collections.")
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RoadmapItem(title: String, body: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("SOON", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
