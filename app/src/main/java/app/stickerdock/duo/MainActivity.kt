package app.stickerdock.duo

import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var store: StickerStore
    private lateinit var files: StickerFiles
    private lateinit var backup: StickerBackup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = StickerStore(applicationContext)
        files = StickerFiles(applicationContext, store)
        backup = StickerBackup(applicationContext, store, files)
        setContent { MaterialTheme { Home() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Home() {
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        var packs by remember { mutableStateOf<List<StickerPack>>(emptyList()) }
        var selectedId by remember { mutableStateOf<String?>(null) }
        var stickers by remember { mutableStateOf<List<StickerItem>>(emptyList()) }
        var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
        var query by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var dialog by remember { mutableStateOf<String?>(null) }
        var name by remember { mutableStateOf("") }
        var moving by remember { mutableStateOf<StickerItem?>(null) }
        var target by remember { mutableStateOf<String?>(null) }
        val pack = packs.firstOrNull { it.id == selectedId }

        fun refresh() {
            scope.launch {
                val data = withContext(Dispatchers.IO) {
                    val all = store.listPacks()
                    Triple(all, selectedId?.let(store::listStickers) ?: emptyList(),
                        all.associate { it.id to store.listStickers(it.id).size })
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
                } finally { busy = false }
            }
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val id = selectedId ?: return@rememberLauncherForActivityResult
            if (uris.isNotEmpty()) operation {
                var added = 0; var duplicates = 0; var failed = 0
                uris.forEach {
                    when (files.importUri(id, it)) {
                        ImportResult.Added -> added++
                        ImportResult.Duplicate -> duplicates++
                        is ImportResult.Failed -> failed++
                    }
                }
                "Imported $added, duplicate $duplicates, failed $failed"
            }
        }
        val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
            if (it != null) operation { backup.export(it); "Backup saved" }
        }
        val loadBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            if (it != null) operation {
                val s = backup.restore(it)
                "Restored ${s.packs} packs, ${s.stickers} stickers, ${s.rejected} skipped"
            }
        }
        LaunchedEffect(selectedId) { refresh() }
        Scaffold(topBar = {
            TopAppBar(title = { Text(pack?.name ?: "StickerDock") }, navigationIcon = {
                if (selectedId != null) TextButton(onClick = { selectedId = null }) { Text("Back") }
            })
        }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
                if (pack == null) {
                    Text("Your stickers, organized offline.")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { name = ""; dialog = "create" }) { Text("New pack") }
                        OutlinedButton(onClick = { saveBackup.launch("stickerdock.zip") }) { Text("Backup") }
                        OutlinedButton(onClick = { loadBackup.launch(arrayOf("application/zip")) }) { Text("Restore") }
                    }
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        label = { Text("Search packs") }, modifier = Modifier.fillMaxWidth())
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(packs.filter { it.name.contains(query, true) }, key = { it.id }) { item ->
                            Card(Modifier.fillMaxWidth().clickable { selectedId = item.id }) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.name, modifier = Modifier.weight(1f))
                                    Text("${counts[item.id] ?: 0} stickers")
                                }
                            }
                        }
                    }
                } else {
                    Text("${stickers.size}/30 stickers")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(enabled = !busy && stickers.size < 30, onClick = { picker.launch(arrayOf("image/*")) }) { Text("Import") }
                        OutlinedButton(onClick = { name = pack.name; dialog = "rename" }) { Text("Rename") }
                        OutlinedButton(onClick = { dialog = "delete" }) { Text("Delete") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((title, packageName) in listOf("WhatsApp" to "com.whatsapp", "WA Business" to "com.whatsapp.w4b")) {
                            OutlinedButton(enabled = stickers.size in 3..30,
                                onClick = { installPack(pack, packageName, snackbar, scope) }) { Text(title) }
                        }
                    }
                    if (stickers.size < 3) Text("Import at least 3 stickers to export.")
                    LazyVerticalGrid(columns = GridCells.Adaptive(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        gridItems(stickers, key = { it.id }) { item ->
                            val path = files.stickerFile(item.packId, item.fileName).path
                            val image = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
                            Card {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                                        if (image == null) Text("Missing")
                                        else Image(image, contentDescription = "Sticker", modifier = Modifier.fillMaxSize())
                                    }
                                    TextButton(onClick = { moving = item; target = packs.firstOrNull { it.id != item.packId }?.id },
                                        enabled = packs.size > 1 && !busy) { Text("Copy/Move") }
                                    TextButton(onClick = { operation { files.deleteSticker(item); "Removed" } },
                                        enabled = !busy) { Text("Remove") }
                                }
                            }
                        }
                    }
                }
            }
        }
        moving?.let { item ->
            AlertDialog(onDismissRequest = { moving = null },
                title = { Text("Transfer sticker") },
                text = {
                    Column {
                        packs.filter { it.id != item.packId }.forEach { candidate ->
                            TextButton(onClick = { target = candidate.id }) {
                                Text((if (target == candidate.id) "● " else "○ ") + candidate.name)
                            }
                        }
                    }
                },
                confirmButton = {
                    Row {
                        listOf(false to "Copy", true to "Move").forEach { (move, label) ->
                            TextButton(enabled = target != null && !busy, onClick = {
                                val dest = target
                                moving = null
                                if (dest != null) operation {
                                    when (val result = files.transferSticker(item, dest, move)) {
                                        ImportResult.Added -> "$label complete"
                                        ImportResult.Duplicate -> "Duplicate in target"
                                        is ImportResult.Failed -> result.reason
                                    }
                                }
                            }) { Text(label) }
                        }
                    }
                },
                dismissButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } })
        }
        dialog?.let { mode ->
            AlertDialog(onDismissRequest = { dialog = null },
                title = { Text(if (mode == "create") "New pack" else if (mode == "rename") "Rename pack" else "Delete pack?") },
                text = {
                    if (mode == "delete") Text("Local stickers and data will be deleted.")
                    else OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Pack name") })
                },
                confirmButton = {
                    TextButton(enabled = !busy && (mode == "delete" || name.isNotBlank()), onClick = {
                        val id = selectedId
                        val value = name
                        dialog = null
                        when (mode) {
                            "create" -> operation { store.createPack(value); "Pack created" }
                            "rename" -> if (id != null) operation { store.renamePack(id, value); "Renamed" }
                            "delete" -> if (id != null) operation { files.deletePack(id); "Deleted" }
                        }
                    }) { Text("Confirm") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } })
        }
    }
    private fun installPack(pack: StickerPack, packageName: String, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
        try {
            val intent = WhatsAppStickerProvider.installIntent(pack, packageName)
            if (intent.resolveActivity(packageManager) == null) {
                scope.launch { snackbar.showSnackbar("WhatsApp app not available or sticker import unsupported") }
            } else startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Unable to launch WhatsApp") }
        } catch (e: SecurityException) {
            scope.launch { snackbar.showSnackbar(e.message ?: "WhatsApp refused request") }
        }
    }
}
