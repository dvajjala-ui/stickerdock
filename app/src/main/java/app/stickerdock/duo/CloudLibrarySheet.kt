package app.stickerdock.duo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudLibrarySheet(cloud: CloudLibrary, pack: StickerPack?, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var email by remember { mutableStateOf(cloud.email) }
    var password by remember { mutableStateOf("") }
    var signedIn by remember { mutableStateOf(cloud.signedIn) }
    var enabled by remember { mutableStateOf(cloud.enabled) }
    var message by remember { mutableStateOf(cloud.status) }
    var busy by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf("loading") }
    var confirmShare by remember { mutableStateOf(false) }
    var adult by remember { mutableStateOf(false) }
    var rights by remember { mutableStateOf(false) }
    fun run(action: () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try { message = withContext(Dispatchers.IO) { action() }; onChanged() }
            catch (e: Exception) { message = e.message ?: "Please retry; local stickers are safe" }
            finally { signedIn = cloud.signedIn; enabled = cloud.enabled; busy = false }
        }
    }
    LaunchedEffect(pack?.id, signedIn) {
        if (signedIn && pack != null && pack.rating == PackRating.GENERAL) {
            sharing = runCatching { withContext(Dispatchers.IO) { cloud.sharingStatus(pack.id) } }.getOrDefault("unavailable")
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (pack == null) "Your cloud library" else "Backup & sharing", style = MaterialTheme.typography.headlineSmall)
            Text("Your stickers stay on this phone. Private sync copies general-audience packs, categories, labels and tags to your account. Mature packs stay offline.")
            if (!signedIn) {
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password · at least 10 characters") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy && email.isNotBlank() && password.length >= 10, onClick = {
                    val entered = password; password = ""; run { cloud.signIn(email, entered, false) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
                OutlinedButton(enabled = !busy && email.isNotBlank() && password.length >= 10, onClick = {
                    val entered = password; password = ""; run { cloud.signIn(email, entered, true) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Create account") }
                TextButton(enabled = !busy && email.isNotBlank(), onClick = { run { cloud.resend(email) } }) { Text("Resend confirmation email") }
                TextButton(enabled = !busy, onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://stickerdock.vercel.app/account"))) }
                        .onFailure { message = "Open stickerdock.vercel.app/account to reset your password" }
                }) { Text("Forgot password?") }
                Text("Confirm your email, then sign in. Cloud sync and public sharing are optional.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(cloud.email, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("Private cloud sync"); Text("Automatic after changes and periodic retry", style = MaterialTheme.typography.bodySmall) }
                    Switch(checked = enabled, enabled = !busy, onCheckedChange = {
                        cloud.enabled = it; enabled = it
                        if (it) run { cloud.sync(progress = { progress -> scope.launch { message = progress } }) }
                    })
                }
                Text("Deleting a previously synced local pack also removes its cloud entry on the next sync. Turning sync off keeps existing cloud copies.", style = MaterialTheme.typography.bodySmall)
                Button(enabled = enabled && !busy, onClick = { run { cloud.sync(progress = { progress -> scope.launch { message = progress } }) } }, modifier = Modifier.fillMaxWidth()) { Text("Sync now") }
                OutlinedButton(enabled = !busy, onClick = { run { cloud.restore() } }, modifier = Modifier.fillMaxWidth()) { Text("Restore cloud packs") }
                if (pack != null) {
                    HorizontalDivider()
                    Text(pack.name, style = MaterialTheme.typography.titleMedium)
                    if (pack.rating == PackRating.MATURE) Text("This mature pack stays offline and cannot be shared publicly.")
                    else {
                        val sharingOn = sharing in setOf("pending", "draft")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Share with everyone")
                                Text(when (sharing) { "pending" -> "Waiting for review"; "draft" -> "In moderator review / approved catalog"; "rejected" -> "Not approved; update and resubmit"; "unavailable" -> "Status unavailable; retry before changing"; else -> "Private unless you submit this pack" }, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(sharingOn, enabled = !busy && sharing !in setOf("loading", "unavailable"), onCheckedChange = { value ->
                                if (value) confirmShare = true else run { cloud.share(pack, false).also { sharing = it }; "Sharing withdrawn. Downloaded copies cannot be recalled." }
                            })
                        }
                        Text("Only the submitted version is reviewed. Edits stay private until you switch sharing off and submit again. Reviews include content and rights checks.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(enabled = !busy, onClick = { run { cloud.signOut(); "Signed out. Local stickers preserved." } }) { Text("Sign out") }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (enabled) TextButton(onClick = { cloud.enabled = false; enabled = false; message = "Pausing after the current request…" }) { Text("Pause sync") }
            }
            Text(message, style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Done") }
        }
    }
    if (confirmShare && pack != null) AlertDialog(onDismissRequest = { confirmShare = false },
        title = { Text("Submit for public sharing?") }, text = {
            Column {
                Text("People can download and reuse approved stickers. Your email stays private. Reviewers see this pack only; no AI training permission is granted.")
                Row { Checkbox(adult, { adult = it }); Text("I am at least 18 years old", Modifier.padding(top = 12.dp)) }
                Row { Checkbox(rights, { rights = it }); Text("I own or have redistribution rights for every image, and this pack has no private personal data.", Modifier.padding(top = 12.dp)) }
            }
        }, confirmButton = { TextButton(enabled = adult && rights, onClick = {
            confirmShare = false
            run { cloud.share(pack, true).also { sharing = it }; "Submitted. Other users can use it after approval and publication." }
        }) { Text("Submit for review") } }, dismissButton = { TextButton(onClick = { confirmShare = false }) { Text("Cancel") } })
}
