package de.jtracht.piimager.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val entries: List<OsEntry> = emptyList(), val catalogDepth: Int = 0,
    val catalogBusy: Boolean = false, val preparing: Boolean = false, val probing: Boolean = false,
    val bytes: Long = 0, val error: String? = null,
    /** First-boot settings the user confirmed in the settings step. */
    val customization: Customization = Customization(), val localFormat: InitFormat = InitFormat.NONE,
    /** True while confirmed settings still have to be written into the image. */
    val settingsPending: Boolean = false,
    val draft: Customization = Customization(), val draftFormat: InitFormat = InitFormat.NONE, val draftSection: Int = 0,
    val models: List<PiModel> = emptyList(), val model: PiModel? = null, val catalogQuery: String = "",
    /** Counts accepted image choices, so the screen can move on even when the same image is chosen again. */
    val selection: Int = 0
) {
    /** Also true for an image a new screen took over from a preparation that kept running in the background. */
    val imageChosen get() = entry != null || uri != null || prepared != null || preparing
    /** The loaded image matches the confirmed settings and nothing is running. */
    val imageReady get() = prepared != null && !preparing && !settingsPending
}

class ImagerViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ImageRepository(app)
    val usb = UsbStorage(app)
    private val mutable = MutableStateFlow(ScreenState())
    val state = mutable.asStateFlow()
    private var clearingImage = false
    private val history = ArrayDeque<List<OsEntry>>()
    private var catalogJob: Job? = null
    private var catalogGeneration = 0
    /** Settings confirmed while an image was still loading; applied as soon as it is ready. */
    private var pendingSettings: Customization? = null

    init {
        viewModelScope.launch {
            PreparationService.state.collect { progress ->
                mutable.update { current ->
                    current.copy(preparing = progress.busy || clearingImage, bytes = progress.bytes, prepared = progress.image,
                        // A new screen in the same process adopts the settings already written into the image.
                        customization = if (current.customization == Customization() && progress.image != null) progress.image.settings else current.customization,
                        error = progress.error ?: current.error)
                }
                if (!progress.busy) reconcile()
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

    /** Starts editing in the settings step from the confirmed settings. Editing never touches the image. */
    fun beginSettings() {
        mutable.update { it.copy(draft = it.customization, draftFormat = format(it), draftSection = 0) }
    }
    fun editConfiguration(settings: Customization, format: InitFormat) { mutable.update { it.copy(draft = settings, draftFormat = format) } }
    fun configurationSection(section: Int) { mutable.update { it.copy(draftSection = section) } }

    /**
     * Confirms the draft. Invalid settings throw [IllegalArgumentException] with a message for the user.
     * The settings are written into the loaded image now, or once loading has finished.
     */
    fun confirmSettings() {
        val current = mutable.value
        check(current.imageChosen) { "Choose an image" }
        val format = current.entry?.let { InitFormat.fromId(it.initFormat) } ?: current.draftFormat
        val settings = current.draft
        settings.validate(format)
        current.entry?.validateCustomization(settings)
        pendingSettings = if (settings.enabled) settings else Customization()
        mutable.update { it.copy(customization = settings, localFormat = format, settingsPending = true, error = null) }
        reconcile()
    }

    /** Brings the prepared image in line with the confirmed settings, reusing the loaded image where possible. */
    private fun reconcile() {
        val wanted = pendingSettings ?: return
        val current = mutable.value
        if (current.preparing || PreparationService.state.value.busy || !current.imageChosen) return
        val image = current.prepared
        if (image != null && image.settings == wanted) {
            pendingSettings = null
            mutable.update { it.copy(settingsPending = false) }
            return
        }
        // An unmodified image is customized in place; anything else is loaded again with the settings.
        val base = image?.takeIf { wanted.enabled && !it.settings.enabled }
        // If the job cannot start, the settings stay pending so the old image is never taken as ready.
        if (start(PreparationRequest(current.entry, current.uri, wanted, current.localFormat, current.model, base))) {
            pendingSettings = null
            mutable.update { it.copy(settingsPending = false) }
        }
    }

    fun forgetImage() {
        if (locked()) return
        clearingImage = true
        pendingSettings = null
        PreparationService.clearResult()
        mutable.update { it.copy(prepared = null, entry = null, uri = null, customization = Customization(), draft = Customization(),
            settingsPending = false, preparing = true, bytes = 0) }
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

    /** Takes a local image and starts loading it right away. Returns whether it was accepted. */
    fun selectLocal(uri: Uri): Boolean {
        if (locked()) return false
        choose(entry = null, uri = uri)
        return true
    }

    /** Loads the board list shown in the model step, once, unless a catalog request is already running. */
    fun loadModels() {
        val current = mutable.value
        if (current.models.isEmpty() && !current.catalogBusy) loadCatalog(repository.rootUrl(), root = true)
    }
    /** Returns whether the choice was accepted, so the caller can move on to the next step. */
    fun selectModel(model: PiModel?): Boolean {
        if (locked() || mutable.value.catalogBusy) return false
        if (model == mutable.value.model) return true
        history.clear()
        pendingSettings = null
        PreparationService.clearResult()
        // A board change invalidates both the image choice and its session-only credentials.
        mutable.update { it.copy(model = model, entry = null, uri = null, prepared = null, customization = Customization(), draft = Customization(),
            settingsPending = false, localFormat = InitFormat.NONE, catalogQuery = "", entries = emptyList(), catalogDepth = 0, error = null) }
        return true
    }
    fun searchCatalog(query: String) { mutable.update { it.copy(catalogQuery = query.take(200)) } }
    fun visibleEntries(state: ScreenState) = CatalogSelection.search(CatalogSelection.compatible(state.entries, state.model), state.catalogQuery)

    /** Shows the top level of the OS catalog in the image step. */
    fun browseCatalog() {
        val current = mutable.value
        if (current.catalogBusy || (current.entries.isNotEmpty() && history.isEmpty())) return
        history.clear()
        mutable.update { it.copy(catalogQuery = "", catalogDepth = 0) }
        loadCatalog(repository.rootUrl(), root = true)
    }
    /** Returns false at the top level, so the caller can go back a step instead. */
    fun backCatalog(): Boolean {
        if (history.isEmpty() || mutable.value.catalogBusy) return false
        mutable.update { it.copy(entries = history.removeLast(), catalogQuery = "", catalogDepth = history.size) }
        return true
    }
    /** Opens a category, or chooses an image and starts loading it. Returns true when an image was chosen. */
    fun selectEntry(entry: OsEntry): Boolean {
        if (mutable.value.catalogBusy || locked()) return false
        val selected = mutable.value.entries.firstOrNull { it.name == entry.name && it.url == entry.url && it.sublist == entry.sublist } ?: return false
        when {
            selected.children.isNotEmpty() -> {
                history.addLast(mutable.value.entries)
                mutable.update { it.copy(entries = selected.children, catalogQuery = "", catalogDepth = history.size) }
            }
            selected.sublist.isNotBlank() -> {
                history.addLast(mutable.value.entries)
                mutable.update { it.copy(catalogDepth = history.size) }
                loadCatalog(selected.sublist)
            }
            selected.url.isNotBlank() -> {
                if (!CatalogSelection.supports(selected, mutable.value.model)) { error("Image does not match the selected model"); return false }
                choose(entry = selected, uri = null)
                return true
            }
            else -> error("This catalog entry contains no supported image")
        }
        return false
    }

    /** Loads the chosen image again after a cancelled or failed load; confirmed settings are applied afterwards. */
    fun reloadImage() {
        val current = mutable.value
        if (locked() || !current.imageChosen || current.prepared != null) return
        if (start(PreparationRequest(current.entry, current.uri, Customization(), InitFormat.NONE, current.model)) && current.customization.enabled) {
            pendingSettings = current.customization
            mutable.update { it.copy(settingsPending = true) }
        }
    }

    /** A new image replaces the previous one and its settings, and loads immediately without settings. */
    private fun choose(entry: OsEntry?, uri: Uri?) {
        pendingSettings = null
        PreparationService.clearResult()
        mutable.update { it.copy(entry = entry, uri = uri, prepared = null, error = null, customization = Customization(),
            draft = Customization(), settingsPending = false, localFormat = InitFormat.NONE, selection = it.selection + 1) }
        start(PreparationRequest(entry, uri, Customization(), InitFormat.NONE, mutable.value.model))
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

    private fun start(request: PreparationRequest): Boolean = try {
        PreparationService.begin(getApplication(), request)
        mutable.update { it.copy(preparing = true, prepared = null, bytes = 0, error = null) }
        true
    } catch (e: Exception) { error(e.message ?: "Cannot start image preparation"); false }
    fun cancelPreparation() { pendingSettings = null; mutable.update { it.copy(settingsPending = false) }; PreparationService.cancel(getApplication()) }

    private fun format(state: ScreenState) = state.entry?.let { InitFormat.fromId(it.initFormat) } ?: state.localFormat

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
