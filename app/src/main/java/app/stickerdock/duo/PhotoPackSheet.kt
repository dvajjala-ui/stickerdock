package app.stickerdock.duo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Picking photos is a draft. No library mutation occurs until Save to pack. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPackSheet(count: Int, packs: List<StickerPack>, counts: Map<String, Int>, busy: Boolean,
    error: String?, onSave: (String, String?) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var targetId by rememberSaveable { mutableStateOf<String?>(null) }
    var chooseExisting by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val selected = packs.firstOrNull { it.id == targetId }
    val canSave = if (chooseExisting) selected != null && count <= 30 - (counts[selected.id] ?: 0) else title.isNotBlank()
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Save to a pack", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("$count selected · You can edit each sticker after saving.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!chooseExisting, { chooseExisting = false }, { Text("New pack") }, enabled = !busy)
                    FilterChip(chooseExisting, { chooseExisting = true }, { Text("Existing pack") }, enabled = !busy && packs.isNotEmpty())
                }
                if (!chooseExisting) {
                    OutlinedTextField(title, { title = it.take(60).filter { c -> c.code >= 32 } }, enabled = !busy,
                        singleLine = true, label = { Text("Pack name") }, placeholder = { Text("e.g. Everyday reactions") }, modifier = Modifier.fillMaxWidth())
                    if (count > 30) Text("We'll split these into numbered packs of up to 30 stickers.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LibrarySearch(query, { query = it }, "Find a pack", Modifier.fillMaxWidth())
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        items(packs.filter { it.name.contains(query, true) }, key = { it.id }) { pack ->
                            val size = counts[pack.id] ?: 0
                            Surface(onClick = { targetId = pack.id }, enabled = !busy && size < 30,
                                color = if (pack.id == targetId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.medium) {
                                ListItem(headlineContent = { Text(pack.name) }, supportingContent = {
                                    Text("$size/30 · ${if (pack.kind == StickerKind.STATIC) "Static" else "Animated"}") },
                                    leadingContent = { RadioButton(pack.id == targetId, onClick = null, enabled = !busy && size < 30) },
                                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                            }
                        }
                    }
                    selected?.let { if (count > 30 - (counts[it.id] ?: 0)) Text("Only ${30 - (counts[it.id] ?: 0)} spaces left. Extra images stay unsaved; choose a new pack to keep the whole selection.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Button(onClick = { onSave(if (chooseExisting) selected!!.name else title.trim(), if (chooseExisting) targetId else null) },
                enabled = !busy && canSave, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Saving stickers…" else "Save to pack") }
            TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
