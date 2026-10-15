package app.stickerdock.duo

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.concurrent.withLock

/** Private, opt-in backup. Neither server credentials nor passwords are stored in the APK. */
class CloudLibrary(private val context: Context) {
    private val prefs = context.getSharedPreferences("cloud_library", Context.MODE_PRIVATE)
    val email: String get() = prefs.getString("email", "").orEmpty()
    val signedIn: Boolean get() = prefs.contains("session")
    var enabled: Boolean
        get() = signedIn && prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply(); schedule(context, value) }
    val status: String get() = prefs.getString("status", "Not synced yet").orEmpty()
    private fun owner(): String = session().getJSONObject("user").getString("id")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey("stickerdock-cloud", null) as? SecretKey ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("stickerdock-cloud", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun saveSession(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.doFinal(value.toString().toByteArray())
        prefs.edit().putString("session", Base64.encodeToString(cipher.iv + bytes, Base64.NO_WRAP))
            .putString("email", value.getJSONObject("user").optString("email"))
            .putLong("expires", System.currentTimeMillis() + value.optLong("expires_in", 3600) * 1000).commit()
    }
    private fun session(): JSONObject {
        val bytes = Base64.decode(prefs.getString("session", null) ?: error("Sign in first"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }
    fun signIn(email: String, password: String, create: Boolean): String = synchronized(sessionLock) {
        val result = request(JSONObject().put("action", if (create) "signup" else "login")
            .put("email", email.trim()).put("password", password), false)
        if (result.has("access_token")) { saveSession(result); "Signed in. Enable private sync to back up your library." }
        else result.optString("message", "Confirm your email, then sign in")
    }
    fun resend(email: String): String = request(JSONObject().put("action", "resend").put("email", email.trim()), false).getString("message")
    fun signOut() = syncLock.withLock {
        enabled = false
        prefs.edit().remove("session").remove("email").putString("status", "Signed out; local stickers are safe").commit()
    }
    private fun token(): String = synchronized(sessionLock) {
        var current = session()
        if (prefs.getLong("expires", 0) < System.currentTimeMillis() + 120_000) {
            current = request(JSONObject().put("action", "refresh").put("refresh_token", current.getString("refresh_token")), false)
            saveSession(current)
        }
        current.getString("access_token")
    }
    internal fun request(payload: JSONObject, authenticated: Boolean = true): JSONObject {
        val bearer = if (authenticated) token() else null
        val connection = URL("https://stickerdock.vercel.app/api/cloud").openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000; connection.readTimeout = 90_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"; connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val result = stream?.use { JSONObject(String(StickerFiles.boundedRead(it, 4_000_000), Charsets.UTF_8)) }
                ?: error("Cloud unavailable; retry later")
            check(code in 200..299) { result.optString("error", "Cloud unavailable; retry later") }
            return result
        } finally { connection.disconnect() }
    }
    private fun action(name: String) = JSONObject().put("action", name)
    private fun mapping(owner: String, id: String) = "$owner:pack:$id"
    private fun packManifest(pack: StickerPack, items: List<StickerItem>, remoteId: String) = JSONObject()
        .put("id", remoteId).put("name", pack.name).put("rating", pack.rating.name).put("kind", pack.kind.name)
        .put("category", if (pack.category in setOf(PackCategory.GENERAL, PackCategory.OTHER)) "ALL" else pack.category.name)
        .put("stickers", JSONArray().also { array -> items.forEach {
            array.put(JSONObject().put("sha256", it.sha256).put("label", it.label).put("tags", it.tags))
        } })

    /** Idempotent uploads and commit checkpoints survive failure/process death. No cloud read deletes local files. */
    fun sync(progress: (String) -> Unit = {}, stopped: () -> Boolean = { false }): String {
        if (!enabled) return "Private sync is off"
        if (!syncLock.tryLock()) return "Sync is already running"
        try {
            val owner = owner()
            var saved = 0
            StickerStore(context).use { store ->
                val files = StickerFiles(context, store)
                val packs = store.listPacks().filter { it.rating == PackRating.GENERAL }
                val localIds = store.listPacks().mapTo(mutableSetOf()) { it.id }
                // Only remove cloud packs previously backed up by THIS installation.
                prefs.all.keys.filter { it.startsWith("$owner:pack:") }.forEach { mapKey ->
                    val localId = mapKey.removePrefix("$owner:pack:")
                    if (localId !in localIds) {
                        val map = JSONObject(prefs.getString(mapKey, "{}")!!)
                        request(action("delete").put("id", map.getString("id")))
                        prefs.edit().remove(mapKey).commit()
                    }
                }
                packs.forEachIndexed { index, pack ->
                    check(enabled && !stopped()) { "Sync paused; local library is safe" }
                    val mapKey = mapping(owner, pack.id)
                    val map = JSONObject(prefs.getString(mapKey, "{}")!!)
                    val items = store.listStickers(pack.id)
                    val manifest = packManifest(pack, items, map.optString("id", pack.id))
                    val localHash = digest(manifest.toString().toByteArray())
                    if (map.optString("localHash") == localHash) return@forEachIndexed
                    val message = "Backing up ${index + 1}/${packs.size}: ${pack.name}"
                    prefs.edit().putString("status", message).apply(); progress(message)
                    items.forEach { item ->
                        check(enabled && !stopped()) { "Sync paused; local library is safe" }
                        val assetKey = "$owner:asset:${item.sha256}"
                        if (!prefs.getBoolean(assetKey, false)) {
                            val bytes = files.stickerFile(item.packId, item.fileName).readBytes()
                            check(digest(bytes) == item.sha256) { "Sticker changed while syncing; retry" }
                            request(action("upload").put("kind", pack.kind.name).put("sha256", item.sha256)
                                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)))
                            prefs.edit().putBoolean(assetKey, true).commit()
                        }
                    }
                    val result = request(action("commit").put("manifest", manifest).put("revision", map.optInt("revision", 0))).getJSONObject("pack")
                    prefs.edit().putString(mapKey, JSONObject().put("id", result.getString("id"))
                        .put("revision", result.getInt("revision")).put("localHash", localHash).toString()).commit()
                    saved++
                }
            }
            val message = "Cloud up to date · $saved packs saved · " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
            prefs.edit().putString("status", message).apply()
            return message
        } catch (e: Exception) {
            prefs.edit().putString("status", "Needs retry: ${e.message ?: "network unavailable"}").apply()
            throw e
        } finally { syncLock.unlock() }
    }

    fun restore(): String {
        check(syncLock.tryLock()) { "Wait for the current sync to finish" }
        try {
            val owner = owner()
            val remote = JSONArray()
            var offset = 0
            do {
                val page = request(action("list").put("offset", offset))
                val packs = page.getJSONArray("packs")
                for (i in 0 until packs.length()) remote.put(packs.getJSONObject(i))
                offset = if (page.isNull("next_offset")) -1 else page.getInt("next_offset")
            } while (offset >= 0 && offset < 1000)
            var restored = 0
            StickerStore(context).use { store ->
                val files = StickerFiles(context, store)
                for (i in 0 until remote.length()) {
                    val row = remote.getJSONObject(i)
                    val remoteId = row.getString("id")
                    val linked = prefs.all.entries.firstOrNull { (k, v) ->
                        k.startsWith("$owner:pack:") && v is String && JSONObject(v).optString("id") == remoteId &&
                            store.getPack(k.removePrefix("$owner:pack:")) != null
                    }
                    if (linked != null && JSONObject(linked.value as String).optInt("revision") == row.getInt("revision")) continue
                    val manifest = row.getJSONObject("manifest")
                    val category = runCatching { PackCategory.valueOf(manifest.getString("category")) }.getOrDefault(PackCategory.GENERAL)
                    // A changed remote version becomes a separate copy; never overwrite local edits.
                    val pack = store.createPack(manifest.getString("name") + if (linked != null) " (cloud)" else "",
                        StickerKind.valueOf(manifest.getString("kind")), PackRating.GENERAL, category)
                    val items = manifest.getJSONArray("stickers")
                    try {
                        for (s in 0 until items.length()) {
                            val item = items.getJSONObject(s)
                            val data = request(action("download").put("id", remoteId).put("sha256", item.getString("sha256")))
                            val bytes = Base64.decode(data.getString("data"), Base64.NO_WRAP)
                            val result = files.restoreBytes(pack.id, bytes, item.getString("sha256"), item.optString("label"), item.optString("tags"))
                            check(result is ImportResult.Added || result is ImportResult.Duplicate) { (result as? ImportResult.Failed)?.reason ?: "Restore failed" }
                        }
                    } catch (e: Exception) {
                        // Remove only this incomplete new copy, never a pre-existing pack.
                        files.deletePack(pack.id)
                        throw e
                    }
                    if (linked != null) prefs.edit().putString(linked.key, JSONObject().put("id", java.util.UUID.randomUUID().toString()).toString()).commit()
                    val localManifest = packManifest(pack, store.listStickers(pack.id), remoteId)
                    prefs.edit().putString(mapping(owner, pack.id), JSONObject().put("id", remoteId)
                        .put("revision", row.getInt("revision")).put("localHash", digest(localManifest.toString().toByteArray())).toString()).commit()
                    restored++
                }
            }
            return "$restored cloud packs restored; local stickers preserved"
        } finally { syncLock.unlock() }
    }
    fun sharingStatus(packId: String): String {
        val owner = owner()
        val map = JSONObject(prefs.getString(mapping(owner, packId), "{}")!!)
        val remote = map.optString("id", packId)
        val submissions = request(action("list")).getJSONArray("submissions")
        for (i in 0 until submissions.length()) {
            val item = submissions.getJSONObject(i)
            if (item.getString("pack_id") == remote) return item.getString("status")
        }
        return "private"
    }
    fun share(pack: StickerPack, share: Boolean): String {
        check(pack.rating == PackRating.GENERAL) { "Mature packs stay offline" }
        if (share) { check(enabled) { "Enable private sync first" }; sync() }
        val map = JSONObject(prefs.getString(mapping(owner(), pack.id), "{}")!!)
        return request(action(if (share) "share" else "withdraw").put("id", map.optString("id", pack.id))
            .put("rights_confirmed", share).put("adult_confirmed", share)).getString("status")
    }
    fun report(id: String, reason: String): String {
        request(action("report").put("catalog_id", id).put("reason", reason))
        return "Report sent for review"
    }
    companion object {
        private val syncLock = ReentrantLock()
        private val sessionLock = Any()
        internal fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun schedule(context: Context, enabled: Boolean) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (!enabled) { scheduler.cancel(610); return }
            scheduler.schedule(JobInfo.Builder(610, ComponentName(context, CloudSyncJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                .setPeriodic(6 * 60 * 60 * 1000L).setBackoffCriteria(60_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
        }
    }
}

class CloudSyncJob : JobService() {
    @Volatile private var stopped = false
    override fun onStartJob(params: JobParameters): Boolean {
        stopped = false
        Thread {
            val retry = runCatching { CloudLibrary(applicationContext).sync(stopped = { stopped }) }.isFailure
            if (!stopped) jobFinished(params, retry)
        }.start()
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { stopped = true; return true }
}
