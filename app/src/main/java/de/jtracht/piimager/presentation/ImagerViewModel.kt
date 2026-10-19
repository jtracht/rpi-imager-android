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
    val draftFormat: InitFormat = InitFormat.NONE, val draftSection: Int = 0,
    val models: List<PiModel> = emptyList(), val model: PiModel? = null,
    val modelsOpen: Boolean = false, val catalogQuery: String = ""
)

class ImagerViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ImageRepository(app)
    val usb = UsbStorage(app)
    private val mutable = MutableStateFlow(ScreenState())
    val state = mutable.asStateFlow()
    private var clearingImage = false
    private val history = ArrayDeque<List<OsEntry>>()
    private var catalogJob: Job? = null
    private var catalogGeneration = 0

    init {
        viewModelScope.launch {
            PreparationService.state.collect { progress ->
                mutable.update { it.copy(preparing = progress.busy || clearingImage, bytes = progress.bytes,
                    prepared = progress.image, error = progress.error ?: it.error) }
            }
        }
        refresh()
    }
    fun refresh() {
        if (ImagingService.state.value.busy) return
        val devices = usb.devices()
        mutable.update { it.copy(devices = devices, target = null) }
    }

    fun error(message: String) { mutable.update { it.copy(error = message) } }
    fun clearError() { PreparationService.dismissError(); mutable.update { it.copy(error = null) } }
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
        mutable.value.entry?.validateCustomization(settings)
        PreparationService.clearResult()
        mutable.update { it.copy(customization = settings, localFormat = localFormat, prepared = null, error = null) }
    }
    fun forgetImage() {
        if (locked()) return
        clearingImage = true
        PreparationService.clearResult()
        mutable.update { it.copy(prepared = null, entry = null, uri = null, customization = Customization(), draft = Customization(), configurationOpen = false, preparing = true, bytes = 0) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    for (name in listOf("prepared.img", "preparing.tmp")) {
                        val file = java.io.File(getApplication<Application>().filesDir, name)
                        check(!file.exists() || file.delete()) { "Cannot remove prepared image" }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e.message ?: "Cannot remove image") }
            finally { clearingImage = false; mutable.update { it.copy(preparing = PreparationService.state.value.busy) } }
        }
    }
    fun selectLocal(uri: Uri) {
        if (locked()) return
        PreparationService.clearResult()
        mutable.update { it.copy(uri = uri, entry = null, prepared = null, error = null, localFormat = InitFormat.NONE, customization = Customization()) }
    }

    fun openModels() {
        if (locked()) return
        mutable.update { it.copy(modelsOpen = true, catalogOpen = false, catalogQuery = "", error = null) }
        if (mutable.value.models.isEmpty()) loadCatalog(repository.rootUrl(), root = true)
    }
    fun closeModels() { mutable.update { it.copy(modelsOpen = false) } }
    fun selectModel(model: PiModel?) {
        if (locked() || mutable.value.catalogBusy) return
        if (model == mutable.value.model) { closeModels(); return }
        history.clear()
        PreparationService.clearResult()
        // A board change invalidates both the image choice and its session-only credentials.
        mutable.update { it.copy(model = model, modelsOpen = false, entry = null, uri = null,
            prepared = null, customization = Customization(), draft = Customization(), configurationOpen = false,
            localFormat = InitFormat.NONE, catalogQuery = "", entries = emptyList(), error = null) }
    }
    fun searchCatalog(query: String) { mutable.update { it.copy(catalogQuery = query.take(200)) } }
    fun visibleEntries(state: ScreenState) = CatalogSelection.search(CatalogSelection.compatible(state.entries, state.model), state.catalogQuery)

    fun openCatalog() {
        if (locked()) return
        history.clear()
        mutable.update { it.copy(catalogOpen = true, modelsOpen = false, catalogQuery = "", entries = emptyList(), error = null) }
        loadCatalog(repository.rootUrl(), root = true)
    }
    fun closeCatalog() { mutable.update { it.copy(catalogOpen = false) } }
    fun backCatalog() {
        if (history.isEmpty()) closeCatalog()
        else mutable.update { it.copy(entries = history.removeLast(), catalogQuery = "") }
    }
    fun selectEntry(entry: OsEntry) {
        if (mutable.value.catalogBusy) return
        val selected = mutable.value.entries.firstOrNull { it.name == entry.name && it.url == entry.url && it.sublist == entry.sublist } ?: return
        when {
            selected.children.isNotEmpty() -> {
                history.addLast(mutable.value.entries)
                mutable.update { it.copy(entries = selected.children, catalogQuery = "") }
            }
            selected.sublist.isNotBlank() -> {
                history.addLast(mutable.value.entries)
                loadCatalog(selected.sublist)
            }
            selected.url.isNotBlank() -> {
                require(CatalogSelection.supports(selected, mutable.value.model)) { "Image does not match the selected model" }
                PreparationService.clearResult()
                mutable.update { it.copy(entry = selected, uri = null, prepared = null, catalogOpen = false, error = null, customization = Customization()) }
            }
            else -> error("This catalog entry contains no supported image")
        }
    }

    private fun loadCatalog(url: String, root: Boolean = false) {
        catalogJob?.cancel()
        val generation = ++catalogGeneration
        mutable.update { it.copy(catalogBusy = true, catalogQuery = "") }
        catalogJob = viewModelScope.launch {
            try {
                val document = withContext(Dispatchers.IO) { repository.catalog(url) }
                mutable.update { it.copy(entries = document.entries, models = if (root) document.models else it.models) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e.message ?: "Catalog unavailable") }
            finally { if (generation == catalogGeneration) mutable.update { it.copy(catalogBusy = false) } }
        }
    }

    fun prepare() {
        if (locked()) return
        val selected = mutable.value
        if (selected.entry == null && selected.uri == null) return
        try {
            PreparationService.begin(getApplication(), PreparationRequest(selected.entry, selected.uri, selected.customization, selected.localFormat, selected.model))
            mutable.update { it.copy(preparing = true, prepared = null, bytes = 0, error = null) }
        } catch (e: Exception) { error(e.message ?: "Cannot start image preparation") }
    }
    fun cancelPreparation() { PreparationService.cancel(getApplication()) }

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

    private fun locked() = mutable.value.preparing || mutable.value.probing || ImagingService.state.value.busy || PreparationService.state.value.busy
}
