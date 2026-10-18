package de.jtracht.piimager.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.jtracht.piimager.R
import de.jtracht.piimager.data.*
import de.jtracht.piimager.domain.PreparedImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ScreenState(
    val entry: OsEntry? = null, val uri: Uri? = null, val prepared: PreparedImage? = null,
    val devices: List<Storage> = emptyList(), val target: Storage? = null,
    val entries: List<OsEntry> = emptyList(), val catalogOpen: Boolean = false,
    val catalogBusy: Boolean = false, val preparing: Boolean = false, val probing: Boolean = false,
    val bytes: Long = 0, val error: String? = null
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
    fun selectLocal(uri: Uri) {
        if (locked()) return
        mutable.update { it.copy(uri = uri, entry = null, prepared = null, error = null) }
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
                it.copy(entry = entry, uri = null, prepared = null, catalogOpen = false, error = null)
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
                    repository.prepare(selected.entry, selected.uri) { bytes -> mutable.update { it.copy(bytes = bytes) } }
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
                val capacity = withContext(Dispatchers.IO) {
                    usb.open(storage).use { device ->
                        require(device.blocks in 1..0xFFFFFFFFL) { "Unsupported USB capacity" }
                        Math.multiplyExact(device.blocks, device.blockSize.toLong())
                    }
                }
                mutable.update { it.copy(target = storage.copy(capacity = capacity)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e.message ?: "USB initialization failed") }
            finally { mutable.update { it.copy(probing = false) } }
        }
    }

    private fun locked() = mutable.value.preparing || mutable.value.probing || ImagingService.state.value.busy
}
