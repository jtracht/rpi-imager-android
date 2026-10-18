package de.jtracht.piimager.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.jtracht.piimager.R
import de.jtracht.piimager.data.*
import de.jtracht.piimager.domain.PreparedImage
import de.jtracht.piimager.domain.Customization
import de.jtracht.piimager.domain.InitFormat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ScreenState(
    val entry: OsEntry? = null, val uri: Uri? = null, val prepared: PreparedImage? = null,
    val devices: List<Storage> = emptyList(), val target: Storage? = null,
    val entries: List<OsEntry> = emptyList(), val catalogOpen: Boolean = false,
    val catalogBusy: Boolean = false, val preparing: Boolean = false, val probing: Boolean = false,
    val bytes: Long = 0, val error: String? = null,
    val customization: Customization = Customization(), val localFormat: InitFormat = InitFormat.NONE,
    val configurationOpen: Boolean = false, val draft: Customization = Customization(),
    val draftFormat: InitFormat = InitFormat.NONE, val draftSection: Int = 0
)

class ImagerViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ImageRepository(app)
    val usb = UsbStorage(app)
    private val mutable = MutableStateFlow(ScreenState())
    val state = mutable.asStateFlow()
    private var prepareJob: Job? = null
    private val history = ArrayDeque<List<OsEntry>>()

    init { refresh() }
    fun refresh() {
        if (ImagingService.state.value.busy) return
        val devices = usb.devices()
        mutable.update { it.copy(devices = devices, target = null) }
    }

    fun error(message: String) { mutable.update { it.copy(error = message) } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun openConfiguration() {
        if (locked()) return
        mutable.update { it.copy(configurationOpen = true, draft = it.customization,
            draftFormat = it.entry?.let { entry -> InitFormat.fromId(entry.initFormat) } ?: it.localFormat, draftSection = 0) }
    }
    fun closeConfiguration() { mutable.update { it.copy(configurationOpen = false, draft = Customization(), draftFormat = InitFormat.NONE) } }
    fun editConfiguration(settings: Customization, format: InitFormat) {
        if (locked()) return
        mutable.update { it.copy(draft = settings, draftFormat = format) }
    }
    fun configurationSection(section: Int) { mutable.update { it.copy(draftSection = section) } }
    fun configure(settings: Customization, localFormat: InitFormat) {
        if (locked()) return
        val format = mutable.value.entry?.let { InitFormat.fromId(it.initFormat) } ?: localFormat
        settings.validate(format)
        mutable.update { it.copy(customization = settings, localFormat = localFormat, prepared = null, error = null) }
    }
    fun forgetImage() {
        if (locked()) return
        mutable.update { it.copy(prepared = null, entry = null, uri = null, customization = Customization(), draft = Customization(), configurationOpen = false, preparing = true, bytes = 0) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val file = java.io.File(getApplication<Application>().filesDir, "prepared.img")
                    check(!file.exists() || file.delete()) { "Cannot remove prepared image" }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e.message ?: "Cannot remove image") }
            finally { mutable.update { it.copy(preparing = false) } }
        }
    }
    fun selectLocal(uri: Uri) {
        if (locked()) return
        mutable.update { it.copy(uri = uri, entry = null, prepared = null, error = null, localFormat = InitFormat.NONE, customization = Customization()) }
    }

    fun openCatalog() {
        if (locked()) return
        history.clear()
        mutable.update { it.copy(catalogOpen = true, error = null) }
        loadCatalog(repository.rootUrl())
    }
    fun closeCatalog() { mutable.update { it.copy(catalogOpen = false) } }
    fun backCatalog() {
        if (history.isEmpty()) closeCatalog()
        else mutable.update { it.copy(entries = history.removeLast()) }
    }
    fun selectEntry(entry: OsEntry) {
        if (mutable.value.catalogBusy) return
        when {
            entry.children.isNotEmpty() -> {
                history.addLast(mutable.value.entries)
                mutable.update { it.copy(entries = entry.children) }
            }
            entry.sublist.isNotBlank() -> {
                history.addLast(mutable.value.entries)
                loadCatalog(entry.sublist)
            }
            entry.url.isNotBlank() -> mutable.update {
                it.copy(entry = entry, uri = null, prepared = null, catalogOpen = false, error = null, customization = Customization())
            }
            else -> error("This catalog entry contains no supported image")
        }
    }

    private fun loadCatalog(url: String) {
        viewModelScope.launch {
            mutable.update { it.copy(catalogBusy = true) }
            try {
                val entries = withContext(Dispatchers.IO) { repository.catalog(url) }
                mutable.update { it.copy(entries = entries) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e.message ?: "Catalog unavailable") }
            finally { mutable.update { it.copy(catalogBusy = false) } }
        }
    }

    fun prepare() {
        if (locked()) return
        val selected = mutable.value
        if (selected.entry == null && selected.uri == null) return
        mutable.update { it.copy(preparing = true, prepared = null, bytes = 0, error = null) }
        prepareJob = viewModelScope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    repository.prepare(selected.entry, selected.uri, selected.customization, selected.localFormat) { bytes -> mutable.update { it.copy(bytes = bytes) } }
                }
                mutable.update { it.copy(prepared = image) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e.message ?: "Image preparation failed") }
            finally { mutable.update { it.copy(preparing = false) } }
        }
    }
    fun cancelPreparation() { prepareJob?.cancel() }

    fun probe(storage: Storage) {
        if (locked()) return
        mutable.update { it.copy(probing = true, target = null, error = null) }
        viewModelScope.launch {
            try {
                val units = withContext(Dispatchers.IO) {
                    val candidates = if (storage.capacity > 0) listOf(storage) else usb.logicalUnits(storage)
                    var lastError: Exception? = null
                    val found = candidates.mapNotNull { candidate ->
                        try {
                            usb.open(candidate).use { device ->
                                require(device.blocks in 1..0xFFFFFFFFL) { "Unsupported USB capacity" }
                                candidate.copy(capacity = Math.multiplyExact(device.blocks, device.blockSize.toLong()))
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { lastError = e; null }
                    }
                    if (found.isEmpty()) throw (lastError ?: IllegalStateException("No media in USB reader"))
                    found
                }
                mutable.update { current ->
                    val devices = if (storage.capacity > 0) current.devices else current.devices.filter { it.id != storage.id } + units
                    current.copy(target = units.firstOrNull { it.lun == storage.lun } ?: units.first(), devices = devices)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e.message ?: "USB initialization failed") }
            finally { mutable.update { it.copy(probing = false) } }
        }
    }

    private fun locked() = mutable.value.preparing || mutable.value.probing || ImagingService.state.value.busy
}
