package de.jtracht.piimager.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jtracht.piimager.BuildConfig
import de.jtracht.piimager.R
import de.jtracht.piimager.data.ImagingService
import de.jtracht.piimager.data.Storage
import de.jtracht.piimager.presentation.ImagerViewModel
import de.jtracht.piimager.presentation.ScreenState
import de.jtracht.piimager.domain.InitFormat

private val Raspberry = Color(0xFFC51A4A)
private val LightColors = lightColorScheme(primary = Raspberry, secondary = Color(0xFF42684A), background = Color(0xFFF5F3EF), surface = Color(0xFFFFFBF7))
private val DarkColors = darkColorScheme(primary = Color(0xFFFFB1C4), secondary = Color(0xFFAACFAF), background = Color(0xFF161316), surface = Color(0xFF211D20))

@Composable fun ImagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

@Composable
fun ImagerScreen(model: ImagerViewModel, prepareImage: () -> Unit, pickImage: () -> Unit, selectTarget: (Storage) -> Unit,
                 startWrite: (Storage, String) -> Unit, cancelWrite: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val write by ImagingService.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val locked = state.preparing || state.probing || write.busy
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.safeDrawingPadding()) {
            val wide = maxWidth >= 840.dp
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineLarge)
                        Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.unofficial, BuildConfig.UPSTREAM_TAG), style = MaterialTheme.typography.labelMedium)
                    }
                }
                item {
                    StepCard(stringResource(R.string.model_title)) {
                        Text(state.model?.name ?: stringResource(R.string.model_all), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.model_hint), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = model::openModels, enabled = !locked, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.model_choose)) }
                    }
                }
                item {
                    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(Modifier.weight(1f)) { ImageCard(state, locked, model::openCatalog, pickImage, prepareImage, model::cancelPreparation, model::openConfiguration, model::forgetImage) }
                        Box(Modifier.weight(1f)) { StorageCard(state, locked, model::refresh, selectTarget) }
                    } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        ImageCard(state, locked, model::openCatalog, pickImage, prepareImage, model::cancelPreparation, model::openConfiguration, model::forgetImage)
                        StorageCard(state, locked, model::refresh, selectTarget)
                    }
                }
                item {
                    StepCard(stringResource(R.string.write_step)) {
                        Text(stringResource(R.string.write_hint))
                        Button(onClick = { confirm = true }, enabled = !locked && state.prepared != null && state.target != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.write)) }
                        if (write.busy) {
                            Text(stringResource(write.phase))
                            LinearProgressIndicator(progress = { write.progress }, modifier = Modifier.fillMaxWidth())
                            Text("${(write.progress * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(R.string.working))
                            OutlinedButton(onClick = cancelWrite) { Text(stringResource(R.string.cancel)) }
                        } else if (write.phase != 0) Text(stringResource(write.phase), color = MaterialTheme.colorScheme.primary)
                        write.error?.let { Text(stringResource(R.string.failed, it), color = MaterialTheme.colorScheme.error) }
                    }
                }
                item { Text(stringResource(R.string.limitations), style = MaterialTheme.typography.bodySmall) }
                item { Text("${BuildConfig.VERSION_NAME} · ${BuildConfig.UPSTREAM_COMMIT.take(12)}", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
    if (state.catalogOpen) Catalog(model, state)
    if (state.modelsOpen) Models(model, state)
    if (state.configurationOpen) CustomizationDialog(state.draft, state.draftFormat, state.draftSection, state.uri != null,
        connectAvailable = state.uri != null || state.entry?.capabilities?.contains("rpi_connect") == true,
        change = model::editConfiguration, changeSection = model::configurationSection,
        dismiss = model::closeConfiguration, save = model::configure)
    if (confirm) {
        val target = state.target
        val image = state.prepared
        if (target != null && image != null) AlertDialog(onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.erase_title)) },
            text = { Text(stringResource(R.string.erase_body, "${target.name} [${target.id}, LUN ${target.lun}]", size(target.capacity), image.name)) },
            confirmButton = { TextButton(onClick = { confirm = false; startWrite(target, image.sha256) }, enabled = !locked) { Text(stringResource(R.string.erase_confirm)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
    }
    state.error?.let { message -> AlertDialog(onDismissRequest = model::clearError,
        text = { Text(stringResource(R.string.failed, message)) },
        confirmButton = { TextButton(onClick = model::clearError) { Text(stringResource(R.string.close)) } }) }
}

@Composable private fun StepCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable private fun ImageCard(state: ScreenState, locked: Boolean, catalog: () -> Unit, pick: () -> Unit, prepare: () -> Unit, cancel: () -> Unit, configure: () -> Unit, clear: () -> Unit) {
    StepCard(stringResource(R.string.image_step)) {
        state.entry?.let { Text(it.name, style = MaterialTheme.typography.titleMedium); Text(it.description) }
        if (state.uri != null) Text(stringResource(R.string.local_selected))
        if (state.entry == null && state.uri == null) state.prepared?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }
        OutlinedButton(onClick = catalog, enabled = !locked, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.catalog)) }
        OutlinedButton(onClick = pick, enabled = !locked, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.local_image)) }
        Text(stringResource(R.string.image_formats), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = configure, enabled = !locked && (state.entry != null || state.uri != null), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.custom_button))
        }
        if (state.customization.enabled) Text(stringResource(R.string.custom_summary, state.customization.hostname, state.customization.username), style = MaterialTheme.typography.bodySmall)
        if (state.preparing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("${stringResource(R.string.preparing)} ${size(state.bytes)}")
            Text(stringResource(R.string.preparation_background), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = cancel) { Text(stringResource(R.string.cancel)) }
        } else if (state.prepared != null) {
            Text(stringResource(R.string.ready, size(state.prepared.file.length())), color = MaterialTheme.colorScheme.primary)
            if (state.customization.enabled && state.customization.connect) Text(stringResource(R.string.connect_reuse_hint), style = MaterialTheme.typography.bodySmall)
        } else Button(onClick = prepare, enabled = !locked && (state.entry != null || state.uri != null), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.prepare))
        }
        TextButton(onClick = clear, enabled = !locked) { Text(stringResource(R.string.clear_image)) }
    }
}

@Composable private fun StorageCard(state: ScreenState, locked: Boolean, refresh: () -> Unit, select: (Storage) -> Unit) {
    StepCard(stringResource(R.string.storage_step)) {
        Text(stringResource(R.string.no_usb), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = refresh, enabled = !locked) { Text(stringResource(R.string.refresh)) }
        state.devices.forEach { device ->
            OutlinedButton(onClick = { select(device) }, enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text("${device.name} · LUN ${device.lun}")
                    Text(device.id, style = MaterialTheme.typography.labelSmall)
                    if (device.capacity > 0) Text(size(device.capacity), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.allow_usb), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (state.probing) CircularProgressIndicator()
        state.target?.let { Text(stringResource(R.string.target, "${it.name} · LUN ${it.lun}", size(it.capacity)), color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable private fun Catalog(model: ImagerViewModel, state: ScreenState) {
    val entries = remember(state.entries, state.model, state.catalogQuery) { model.visibleEntries(state) }
    Dialog(onDismissRequest = model::closeCatalog, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp).imePadding()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = model::backCatalog, enabled = !state.catalogBusy) { Text(stringResource(R.string.back)) }
                    TextButton(onClick = model::closeCatalog) { Text(stringResource(R.string.close)) }
                }
                Text(state.model?.name ?: stringResource(R.string.model_all), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = state.catalogQuery, onValueChange = model::searchCatalog,
                    label = { Text(stringResource(R.string.catalog_search)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), trailingIcon = {
                        if (state.catalogQuery.isNotEmpty()) TextButton(onClick = { model.searchCatalog("") }) { Text(stringResource(R.string.search_clear)) }
                    })
                Text(stringResource(R.string.catalog_search_hint), style = MaterialTheme.typography.bodySmall)
                if (state.catalogBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (entries.isEmpty() && !state.catalogBusy) Text(stringResource(R.string.catalog_empty), Modifier.padding(vertical = 16.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                    items(entries) { entry ->
                        Card(onClick = { model.selectEntry(entry) }, enabled = !state.catalogBusy, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                                if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Models(model: ImagerViewModel, state: ScreenState) {
    Dialog(onDismissRequest = model::closeModels, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.model_choose), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = model::closeModels) { Text(stringResource(R.string.close)) }
                }
                Text(stringResource(R.string.model_change_hint), style = MaterialTheme.typography.bodySmall)
                if (state.catalogBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                    item {
                        ModelOption(stringResource(R.string.model_all), stringResource(R.string.model_all_hint), state.model == null, !state.catalogBusy) { model.selectModel(null) }
                    }
                    items(state.models) { item ->
                        ModelOption(item.name, item.description, state.model == item, !state.catalogBusy) { model.selectModel(item) }
                    }
                }
            }
        }
    }
}

@Composable private fun ModelOption(name: String, description: String, selected: Boolean, enabled: Boolean, choose: () -> Unit) {
    Card(onClick = choose, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable private fun size(bytes: Long): String = Formatter.formatFileSize(LocalContext.current, bytes)
