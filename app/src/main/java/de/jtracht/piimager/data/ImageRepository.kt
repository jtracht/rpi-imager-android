package de.jtracht.piimager.data

import de.jtracht.piimager.domain.*

import android.content.Context
import android.net.Uri
import de.jtracht.piimager.domain.Imaging
import de.jtracht.piimager.domain.PreparedImage
import de.jtracht.piimager.domain.Customization
import de.jtracht.piimager.domain.CustomizationGenerator
import de.jtracht.piimager.domain.InitFormat
import de.jtracht.piimager.domain.ImageInput
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.URL
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
            requireOperation(output.size() + count <= 8 * 1024 * 1024, OperationProblem.CATALOG_INVALID)
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
        requireOperation(depth <= 12, OperationProblem.CATALOG_INVALID)
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
            requireOperation(url.protocol == "https" && url.userInfo == null, OperationProblem.HTTPS_ONLY)
            val connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            try {
                val status = connection.responseCode
                currentCoroutineContext().ensureActive()
                if (status in listOf(301, 302, 303, 307, 308)) {
                    url = URL(url, connection.getHeaderField("Location") ?: operationFailure(OperationProblem.REDIRECT))
                } else {
                    checkOperation(status == 200, OperationProblem.HTTP_STATUS) { status.toString() }
                    return connection.inputStream.buffered().use { consume(it) }
                }
            } finally { connection.disconnect() }
        }
        operationFailure(OperationProblem.REDIRECT)
    }

    suspend fun prepare(entry: OsEntry?, uri: Uri?, settings: Customization = Customization(),
                        localFormat: InitFormat = InitFormat.NONE, model: PiModel? = null, checkpoint: (Long) -> Unit): PreparedImage {
        val format = entry?.let { InitFormat.fromId(it.initFormat) } ?: localFormat
        settings.validate(format)
        entry?.validateCustomization(settings)
        requireOperation(entry == null || CatalogSelection.supports(entry, model), OperationProblem.MODEL_MISMATCH)
        val destination = File(context.filesDir, "prepared.img")
        val temporary = File(context.filesDir, "preparing.tmp")
        destination.delete()
        try {
            val jobContext = currentCoroutineContext()
            val copy: suspend (InputStream) -> Unit = { original ->
                ImageInput.unpack(original.buffered()).use { source ->
                    FileOutputStream(temporary).use { output ->
                        val bytes = ByteArray(1024 * 1024)
                        var total = 0L
                        while (true) {
                            jobContext.ensureActive()
                            val count = source.read(bytes)
                            if (count < 0) break
                            total += count
                            if (entry != null && entry.size > 0) requireOperation(total <= entry.size, OperationProblem.DECLARED_SIZE)
                            checkOperation(context.filesDir.usableSpace > count + 64L * 1024 * 1024, OperationProblem.STORAGE_FULL)
                            output.write(bytes, 0, count)
                            checkpoint(total)
                        }
                        output.fd.sync()
                    }
                }
            }
            if (entry != null) {
                requireOperation(entry.sha256.matches(Regex("[a-fA-F0-9]{64}")), OperationProblem.NO_CHECKSUM)
                remote(entry.url, copy)
                if (entry.size > 0) checkOperation(temporary.length() == entry.size, OperationProblem.DECLARED_SIZE)
            } else {
                requireNotNull(uri)
                (context.contentResolver.openInputStream(uri) ?: operationFailure(OperationProblem.CANNOT_OPEN_IMAGE)).use { copy(it) }
            }
            checkOperation(temporary.length() > 0, OperationProblem.EMPTY_IMAGE)
            var hash = Imaging.digest(temporary.inputStream()) { jobContext.ensureActive() }
            if (entry != null) checkOperation(hash.equals(entry.sha256, ignoreCase = true), OperationProblem.CHECKSUM_MISMATCH)
            if (settings.enabled) {
                jobContext.ensureActive()
                val payload = CustomizationGenerator.generate(settings, format)
                BootImageEditor.apply(temporary, payload) { jobContext.ensureActive() }
                hash = Imaging.digest(temporary.inputStream()) { jobContext.ensureActive() }
            }
            jobContext.ensureActive()
            checkOperation(temporary.renameTo(destination), OperationProblem.STORAGE_FAILED)
            return PreparedImage(destination, entry?.name ?: uri?.lastPathSegment?.substringAfterLast('/') ?: "Image", hash,
                if (settings.enabled) settings else Customization())
        } finally { temporary.delete() }
    }

    /**
     * Writes first-boot settings into an already loaded, still unmodified image, so choosing settings after
     * loading needs no second download. The image is edited under its temporary name: an interrupted edit
     * never leaves a half-customized file that looks prepared.
     */
    suspend fun customize(base: PreparedImage, entry: OsEntry?, settings: Customization, localFormat: InitFormat): PreparedImage {
        val format = entry?.let { InitFormat.fromId(it.initFormat) } ?: localFormat
        requireOperation(settings.enabled, OperationProblem.PREPARATION_STATE)
        requireOperation(!base.settings.enabled, OperationProblem.PREPARATION_STATE)
        settings.validate(format)
        entry?.validateCustomization(settings)
        val destination = File(context.filesDir, "prepared.img")
        val temporary = File(context.filesDir, "preparing.tmp")
        checkOperation(base.file == destination && destination.exists(), OperationProblem.PREPARED_IMAGE_CHANGED)
        temporary.delete()
        checkOperation(destination.renameTo(temporary), OperationProblem.STORAGE_FAILED)
        try {
            val jobContext = currentCoroutineContext()
            checkOperation(Imaging.digest(temporary.inputStream()) { jobContext.ensureActive() }.equals(base.sha256, ignoreCase = true), OperationProblem.PREPARED_IMAGE_CHANGED)
            BootImageEditor.apply(temporary, CustomizationGenerator.generate(settings, format)) { jobContext.ensureActive() }
            val hash = Imaging.digest(temporary.inputStream()) { jobContext.ensureActive() }
            jobContext.ensureActive()
            checkOperation(temporary.renameTo(destination), OperationProblem.STORAGE_FAILED)
            return PreparedImage(destination, base.name, hash, settings)
        } finally { temporary.delete() }
    }

}
