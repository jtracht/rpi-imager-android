package de.jtracht.piimager.data

import android.content.Context
import android.net.Uri
import de.jtracht.piimager.domain.Imaging
import de.jtracht.piimager.domain.PreparedImage
import de.jtracht.piimager.domain.Customization
import de.jtracht.piimager.domain.CustomizationGenerator
import de.jtracht.piimager.domain.InitFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import org.tukaani.xz.XZInputStream
import java.io.*
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.net.ssl.HttpsURLConnection

class ImageRepository(private val context: Context) {
    fun rootUrl(): String = context.assets.open("upstream.json").bufferedReader().use {
        JSONObject(it.readText()).getString("catalog_url")
    }

    suspend fun catalog(url: String): CatalogDocument = remote(url) { source ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 8 * 1024 * 1024) { "Catalog too large" }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        val document = JSONObject(String(bytes, Charsets.UTF_8))
        val models = document.optJSONObject("imager")?.optJSONArray("devices")
        CatalogDocument(parseCatalog(document.getJSONArray("os_list")), models?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val tags = strings(item.optJSONArray("tags"))
                val matching = item.optString("matching_type")
                val name = item.optString("name").trim()
                // An explicit, local "show all" option covers empty/no-filter entries.
                if (name.isEmpty() || tags.isEmpty() || matching !in setOf("inclusive", "exclusive")) null
                else PiModel(name, item.optString("description"), tags, matching == "inclusive")
            }.distinctBy { it.name }
        } ?: emptyList())
    }

    private fun parseCatalog(array: JSONArray, depth: Int = 0): List<OsEntry> {
        require(depth <= 12) { "Catalog nesting limit" }
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            OsEntry(item.getString("name"), item.optString("description"), item.optString("url"),
                item.optString("extract_sha256"), item.optLong("extract_size"), item.optString("subitems_url"),
                item.optJSONArray("subitems")?.let { parseCatalog(it, depth + 1) } ?: emptyList(), item.optString("init_format"),
                strings(item.optJSONArray("capabilities")),
                if (item.has("devices")) strings(item.getJSONArray("devices")) else null)
        }
    }

    private fun strings(array: JSONArray?): Set<String> = array?.let { values ->
        (0 until values.length()).map { values.getString(it) }.toSet()
    } ?: emptySet()

    private suspend fun <T> remote(address: String, consume: suspend (InputStream) -> T): T {
        var url = URL(address)
        repeat(6) {
            currentCoroutineContext().ensureActive()
            require(url.protocol == "https" && url.userInfo == null) { "Only HTTPS URLs are supported" }
            val connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            try {
                val status = connection.responseCode
                currentCoroutineContext().ensureActive()
                if (status in listOf(301, 302, 303, 307, 308)) {
                    url = URL(url, connection.getHeaderField("Location") ?: error("Missing redirect"))
                } else {
                    check(status == 200) { "HTTP $status" }
                    return connection.inputStream.buffered().use { consume(it) }
                }
            } finally { connection.disconnect() }
        }
        error("Too many redirects")
    }

    suspend fun prepare(entry: OsEntry?, uri: Uri?, settings: Customization = Customization(),
                        localFormat: InitFormat = InitFormat.NONE, model: PiModel? = null, checkpoint: (Long) -> Unit): PreparedImage {
        val format = entry?.let { InitFormat.fromId(it.initFormat) } ?: localFormat
        settings.validate(format)
        entry?.validateCustomization(settings)
        require(entry == null || CatalogSelection.supports(entry, model)) { "This image does not match the selected Raspberry Pi model" }
        val destination = File(context.filesDir, "prepared.img")
        val temporary = File(context.filesDir, "preparing.tmp")
        destination.delete()
        try {
            val jobContext = currentCoroutineContext()
            val copy: suspend (InputStream) -> Unit = { original ->
                unpack(original.buffered()).use { source ->
                    FileOutputStream(temporary).use { output ->
                        val bytes = ByteArray(1024 * 1024)
                        var total = 0L
                        while (true) {
                            jobContext.ensureActive()
                            val count = source.read(bytes)
                            if (count < 0) break
                            total += count
                            if (entry != null && entry.size > 0) require(total <= entry.size) { "Image exceeds declared size" }
                            check(context.filesDir.usableSpace > count + 64L * 1024 * 1024) { "Not enough free app storage" }
                            output.write(bytes, 0, count)
                            checkpoint(total)
                        }
                        output.fd.sync()
                    }
                }
            }
            if (entry != null) {
                require(entry.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Catalog image has no valid SHA-256 checksum" }
                remote(entry.url, copy)
                if (entry.size > 0) check(temporary.length() == entry.size) { "Image size mismatch" }
            } else {
                requireNotNull(uri)
                (context.contentResolver.openInputStream(uri) ?: error("Cannot open image")).use { copy(it) }
            }
            check(temporary.length() > 0) { "Empty image" }
            var hash = Imaging.digest(temporary.inputStream()) { jobContext.ensureActive() }
            if (entry != null) check(hash.equals(entry.sha256, ignoreCase = true)) { "SHA-256 mismatch" }
            if (settings.enabled) {
                jobContext.ensureActive()
                val payload = CustomizationGenerator.generate(settings, format)
                BootImageEditor.apply(temporary, payload) { jobContext.ensureActive() }
                hash = Imaging.digest(temporary.inputStream()) { jobContext.ensureActive() }
            }
            jobContext.ensureActive()
            check(temporary.renameTo(destination)) { "Cannot store prepared image" }
            return PreparedImage(destination, entry?.name ?: uri?.lastPathSegment?.substringAfterLast('/') ?: "Image", hash)
        } finally { temporary.delete() }
    }

    private fun unpack(input: BufferedInputStream): InputStream {
        input.mark(8)
        val magic = ByteArray(6)
        val count = input.read(magic)
        input.reset()
        return when {
            count >= 6 && magic.contentEquals(byteArrayOf(0xfd.toByte(), 0x37, 0x7a, 0x58, 0x5a, 0x00)) -> XZInputStream(input, 65536)
            count >= 2 && magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte() -> GZIPInputStream(input)
            count >= 2 && magic[0] == 0x50.toByte() && magic[1] == 0x4b.toByte() -> {
                val zip = ZipInputStream(input)
                var item = zip.nextEntry
                while (item != null && (item.isDirectory || !item.name.lowercase().let { it.endsWith(".img") || it.endsWith(".iso") })) item = zip.nextEntry
                check(item != null) { "ZIP contains no IMG or ISO file" }
                zip
            }
            else -> input
        }
    }
}
