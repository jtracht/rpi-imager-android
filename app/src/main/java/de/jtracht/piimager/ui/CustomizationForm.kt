package de.jtracht.piimager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.jtracht.piimager.R
import de.jtracht.piimager.domain.Customization
import de.jtracht.piimager.domain.InitFormat
import de.jtracht.piimager.domain.ConnectProvisioning

/**
 * First-boot settings as an inline form for the settings step. Confirming happens through the step's
 * Next button, which validates the draft and reports problems through [error].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizationForm(settings: Customization, format: InitFormat, section: Int, local: Boolean, connectAvailable: Boolean,
                      error: String?, change: (Customization, InitFormat) -> Unit, changeSection: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.custom_hint), style = MaterialTheme.typography.bodySmall)
        SettingSwitch(R.string.custom_enable, settings.enabled) { change(settings.copy(enabled = it), format) }
        if (settings.enabled) {
            if (local) FormatPicker(format) { change(settings, it) }
            else Text(stringResource(R.string.custom_format, format.id.ifEmpty { "—" }))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.custom_system, R.string.custom_wifi, R.string.custom_ssh, R.string.custom_interfaces, R.string.custom_connect).forEachIndexed { index, title ->
                    FilterChip(selected = section == index, onClick = { changeSection(index) }, label = { Text(stringResource(title)) })
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (section) {
                    0 -> SystemSettings(settings) { change(it, format) }
                    1 -> WifiSettings(settings) { change(it, format) }
                    2 -> SshSettings(settings) { change(it, format) }
                    3 -> InterfaceSettings(settings) { change(it, format) }
                    4 -> ConnectSettings(settings, local, connectAvailable) { change(it, format) }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable private fun ConnectSettings(s: Customization, local: Boolean, available: Boolean, update: (Customization) -> Unit) {
    val uriHandler = LocalUriHandler.current
    var browserError by remember { mutableStateOf(false) }
    Text(stringResource(if (available) R.string.connect_hint else R.string.connect_unavailable))
    if (available) {
        if (local) Text(stringResource(R.string.connect_local_hint), style = MaterialTheme.typography.bodySmall)
        SettingSwitch(R.string.connect_enable, s.connect) { update(s.copy(connect = it, connectKey = if (it) s.connectKey else "")) }
        if (s.connect) {
            OutlinedButton(onClick = {
                try { uriHandler.openUri(ConnectProvisioning.SETTINGS_URL); browserError = false }
                catch (_: android.content.ActivityNotFoundException) { browserError = true }
                catch (_: IllegalArgumentException) { browserError = true }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connect_open)) }
            if (browserError) Text(stringResource(R.string.connect_browser_error), color = MaterialTheme.colorScheme.error)
            SettingField(R.string.connect_key, s.connectKey, secret = true) { update(s.copy(connectKey = it.trim())) }
            Text(stringResource(R.string.connect_key_hint), style = MaterialTheme.typography.bodySmall)
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

/** The whole row toggles, not only the small switch, and is announced as one switch. */
@Composable private fun SettingSwitch(label: Int, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, thumbContent = null)
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
