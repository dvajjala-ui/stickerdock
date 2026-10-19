package app.stickerdock.duo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val WhatsAppGreen = Color(0xFF087F5B)

@Composable
fun CreationCard(action: String, title: String, description: String, icon: String, enabled: Boolean,
                 onClick: () -> Unit, prominent: Boolean = true) {
    Surface(shape = MaterialTheme.shapes.extraLarge,
        color = if (prominent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (prominent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface.copy(alpha = .7f)) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { StickerDockNavIcon(icon) }
                }
                Spacer(Modifier.weight(1f))
                Text(if (prominent) "PHOTO STICKERS" else "ANIMATED STICKERS", style = MaterialTheme.typography.labelSmall)
            }
            Text(title, style = if (prominent) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (prominent) Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(action) }
            else OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}

@Composable
fun LibrarySearch(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    TextField(value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(label) },
        leadingIcon = { StickerDockNavIcon("Search") },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") },
            modifier = Modifier.semantics { contentDescription = "Clear search" }) { StickerDockNavIcon("Close") } },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        modifier = modifier.semantics { contentDescription = label })
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
fun LibrarySettingsSheet(onAccount: () -> Unit, onImport: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Library settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            ProductAction("Account & cloud backup", "Manage your optional private backup", "Cloud", onClick = onAccount)
            ProductAction("Import and backups", "Bring your collection from another phone or file", "Import", onClick = onImport)
            Text("Your packs stay on this device unless you choose to back up or share them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
    }
}
