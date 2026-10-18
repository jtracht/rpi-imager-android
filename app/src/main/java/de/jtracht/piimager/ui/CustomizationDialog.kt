package de.jtracht.piimager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.jtracht.piimager.R
import de.jtracht.piimager.domain.Customization
import de.jtracht.piimager.domain.InitFormat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizationDialog(settings: Customization, format: InitFormat, section: Int, local: Boolean,
                        change: (Customization, InitFormat) -> Unit, changeSection: (Int) -> Unit,
                        dismiss: () -> Unit, save: (Customization, InitFormat) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxHeight()) {
                    Text(stringResource(R.string.custom_title), style = MaterialTheme.typography.headlineSmall)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                        item { Text(stringResource(R.string.custom_hint), style = MaterialTheme.typography.bodySmall) }
                        item { SettingSwitch(R.string.custom_enable, settings.enabled) { change(settings.copy(enabled = it), format) } }
                        if (settings.enabled) {
                            item {
                                if (local) FormatPicker(format) { change(settings, it) }
                                else Text(stringResource(R.string.custom_format, format.id.ifEmpty { "—" }))
                            }
                            item {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(R.string.custom_system, R.string.custom_wifi, R.string.custom_ssh, R.string.custom_interfaces).forEachIndexed { index, title ->
                                        FilterChip(selected = section == index, onClick = { changeSection(index) }, label = { Text(stringResource(title)) })
                                    }
                                }
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    when (section) {
                                        0 -> SystemSettings(settings) { change(it, format) }
                                        1 -> WifiSettings(settings) { change(it, format) }
                                        2 -> SshSettings(settings) { change(it, format) }
                                        3 -> InterfaceSettings(settings) { change(it, format) }
                                    }
                                }
                            }
                        }
                        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = {
                            try { settings.validate(format); save(settings, format); dismiss() }
                            catch (e: IllegalArgumentException) { error = e.message }
                        }) { Text(stringResource(R.string.custom_save)) }
                    }
                }
            }
        }
    }
}

@Composable private fun FormatPicker(format: InitFormat, select: (InitFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.custom_local_format), style = MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(format.id.ifEmpty { stringResource(R.string.custom_choose_format) }) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                InitFormat.entries.filter { it != InitFormat.NONE }.forEach { item ->
                    DropdownMenuItem(text = { Text(item.id) }, onClick = { select(item); expanded = false })
                }
            }
        }
    }
}

@Composable private fun SystemSettings(s: Customization, update: (Customization) -> Unit) {
    SettingField(R.string.custom_hostname, s.hostname) { update(s.copy(hostname = it)) }
    SettingField(R.string.custom_username, s.username) { update(s.copy(username = it)) }
    SettingField(R.string.custom_password, s.password, secret = true) { update(s.copy(password = it)) }
    SettingField(R.string.custom_timezone, s.timezone) { update(s.copy(timezone = it)) }
    SettingField(R.string.custom_keyboard, s.keyboard) { update(s.copy(keyboard = it)) }
    SettingSwitch(R.string.custom_sudo, s.passwordlessSudo) { update(s.copy(passwordlessSudo = it)) }
}

@Composable private fun WifiSettings(s: Customization, update: (Customization) -> Unit) {
    SettingSwitch(R.string.custom_wifi_enable, s.wifi) { update(s.copy(wifi = it)) }
    SettingField(R.string.custom_country, s.country) { update(s.copy(country = it.uppercase(java.util.Locale.ROOT))) }
    if (s.wifi) {
        SettingField(R.string.custom_ssid, s.ssid) { update(s.copy(ssid = it)) }
        SettingSwitch(R.string.custom_wifi_open, s.openNetwork) { update(s.copy(openNetwork = it)) }
        if (!s.openNetwork) SettingField(R.string.custom_wifi_password, s.wifiPassword, secret = true) { update(s.copy(wifiPassword = it)) }
        SettingSwitch(R.string.custom_wifi_hidden, s.hidden) { update(s.copy(hidden = it)) }
    }
}

@Composable private fun SshSettings(s: Customization, update: (Customization) -> Unit) {
    SettingSwitch(R.string.custom_ssh_enable, s.ssh) { update(s.copy(ssh = it)) }
    if (s.ssh) {
        SettingSwitch(R.string.custom_ssh_password, s.sshPasswordAuth) { update(s.copy(sshPasswordAuth = it)) }
        SettingField(R.string.custom_ssh_keys, s.publicKeys, multiline = true) { update(s.copy(publicKeys = it)) }
        Text(stringResource(R.string.custom_ssh_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun InterfaceSettings(s: Customization, update: (Customization) -> Unit) {
    Text(stringResource(R.string.custom_interfaces_hint))
    SettingSwitch(R.string.custom_i2c, s.i2c) { update(s.copy(i2c = it)) }
    SettingSwitch(R.string.custom_spi, s.spi) { update(s.copy(spi = it)) }
    SettingSwitch(R.string.custom_onewire, s.oneWire) { update(s.copy(oneWire = it)) }
}

@Composable private fun SettingSwitch(label: Int, checked: Boolean, change: (Boolean) -> Unit) {
    val description = stringResource(label)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = change, modifier = Modifier.semantics { contentDescription = description }, thumbContent = null)
    }
}

@Composable private fun SettingField(label: Int, value: String, secret: Boolean = false, multiline: Boolean = false, change: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = change, label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth(),
        singleLine = !multiline, minLines = if (multiline) 4 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) ({ TextButton(onClick = { visible = !visible }) { Text(stringResource(if (visible) R.string.custom_hide else R.string.custom_show)) } }) else null)
}
