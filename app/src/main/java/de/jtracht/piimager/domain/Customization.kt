package de.jtracht.piimager.domain

import org.apache.commons.codec.digest.Sha2Crypt
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

enum class InitFormat(val id: String) {
    NONE(""), SYSTEMD("systemd"), CLOUD_INIT("cloudinit"), CLOUD_INIT_RPI("cloudinit-rpi"), PRESEED("rpi-preseed");
    companion object { fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: NONE }
}

/** Session-only settings. Passwords are never saved in preferences or Android saved state. */
data class Customization(
    val enabled: Boolean = false,
    val hostname: String = "raspberrypi",
    val username: String = "pi",
    val password: String = "",
    val timezone: String = "Europe/Berlin",
    val keyboard: String = "de",
    val wifi: Boolean = false,
    val ssid: String = "",
    val wifiPassword: String = "",
    val country: String = "DE",
    val hidden: Boolean = false,
    val openNetwork: Boolean = false,
    val ssh: Boolean = false,
    val sshPasswordAuth: Boolean = false,
    val publicKeys: String = "",
    val passwordlessSudo: Boolean = false,
    val i2c: Boolean = false,
    val spi: Boolean = false,
    val oneWire: Boolean = false,
    val connect: Boolean = false,
    val connectKey: String = ""
) {
    fun keys() = publicKeys.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()

    fun validate(format: InitFormat) {
        if (!enabled) return
        require(format != InitFormat.NONE) { "Choose a supported first-boot format for this image" }
        require(hostname.matches(Regex("[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"))) { "Hostname must be a single DNS label (1–63 characters)" }
        require(username.matches(Regex("[a-z_][a-z0-9_-]{0,30}")) && username !in RESERVED_USERS) { "Choose a regular Linux username, not a system account" }
        require(password.length <= 256 && password.none { it == '\u0000' || it == '\n' || it == '\r' }) { "Invalid account password" }
        require(password.isNotEmpty() || (ssh && !sshPasswordAuth && keys().isNotEmpty())) { "Set an account password or enable SSH with a public key" }
        if (password.isNotEmpty()) require(password.length >= 8) { "Account password must have at least 8 characters" }
        require(timezone in ZoneId.getAvailableZoneIds()) { "Unknown timezone; use a name such as Europe/Berlin" }
        require(keyboard.matches(Regex("[a-z]{2,3}"))) { "Keyboard layout must be a Linux layout code such as de or us" }
        require(country in java.util.Locale.getISOCountries()) { "Choose a valid two-letter Wi-Fi country" }
        if (wifi) {
            require(ssid.toByteArray(Charsets.UTF_8).size in 1..32 && ssid.none { it.isISOControl() || it in "\u2028\u2029" }) { "Wi-Fi name must contain 1–32 UTF-8 bytes without control characters" }
            if (!openNetwork) require((wifiPassword.length in 8..63 && wifiPassword.all { it.code in 32..126 }) || wifiPassword.matches(Regex("[a-fA-F0-9]{64}"))) { "WPA passphrase must contain 8–63 ASCII characters or a 64-digit hexadecimal PSK" }
        }
        if (ssh) {
            if (sshPasswordAuth) require(password.isNotEmpty()) { "SSH password login requires an account password" }
            else require(keys().isNotEmpty()) { "Add at least one SSH public key" }
            keys().forEach(::validateKey)
        }
        require(!passwordlessSudo || password.isNotEmpty() || (ssh && keys().isNotEmpty())) { "An administrative account is required" }
        if (connect) ConnectProvisioning.validateKey(connectKey)
    }

    companion object {
        private val RESERVED_USERS = setOf("root", "daemon", "bin", "sys", "sync", "games", "man", "lp", "mail", "news", "uucp", "proxy", "www-data", "backup", "list", "irc", "gnats", "nobody", "systemd-network", "systemd-timesync", "messagebus", "sshd", "_apt")
        private fun validateKey(key: String) {
            val parts = key.split(Regex("\\s+"), limit = 3)
            require(parts.size >= 2 && parts[0] in setOf("ssh-ed25519", "ssh-rsa", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521", "sk-ssh-ed25519@openssh.com", "sk-ecdsa-sha2-nistp256@openssh.com")) { "Paste an OpenSSH public key, never a private key" }
            val bytes = try { Base64.getDecoder().decode(parts[1]) } catch (_: IllegalArgumentException) { throw IllegalArgumentException("Invalid SSH public key encoding") }
            require(bytes.size in 16..16384) { "Invalid SSH public key length" }
            val size = java.nio.ByteBuffer.wrap(bytes, 0, 4).int
            require(size == parts[0].length && size <= bytes.size - 4 && String(bytes, 4, size, Charsets.US_ASCII) == parts[0]) { "SSH public key type does not match its contents" }
            require(key.none { it.isISOControl() || it in "\u2028\u2029" }) { "SSH keys must be single-line public keys" }
        }
    }
}

data class BootPayload(val files: Map<String, ByteArray>, val cmdline: List<String>, val config: List<String>)

object CustomizationGenerator {
    /** Shared scalar encoding for YAML/TOML basic strings; controls never become new statements. */
    fun quoted(value: String): String = buildString {
        append('"')
        value.forEach { c -> when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.isISOControl() || c in "\u2028\u2029") append("\\u%04x".format(c.code)) else append(c)
        } }
        append('"')
    }
    fun shell(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"
    private fun keyfile(value: String) = buildString {
        value.forEachIndexed { index, c ->
            when {
                c == '\\' -> append("\\\\")
                c == ' ' && (index == 0 || index == value.lastIndex) -> append("\\s")
                c == '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    fun generate(s: Customization, format: InitFormat): BootPayload {
        if (!s.enabled) return BootPayload(emptyMap(), emptyList(), emptyList())
        s.validate(format)
        // SHA-512 crypt is accepted by both current and legacy Linux images.
        val passwordHash = if (s.password.isNotEmpty()) Sha2Crypt.sha512Crypt(s.password.toByteArray(Charsets.UTF_8)) else "!"
        val parameters = mutableListOf("cfg80211.ieee80211_regdom=${s.country}")
        val config = mutableListOf<String>()
        if (format != InitFormat.PRESEED) {
            if (s.i2c) config += "dtparam=i2c_arm=on"
            if (s.spi) config += "dtparam=spi=on"
            if (s.oneWire) config += "dtoverlay=w1-gpio"
        }
        val files: Map<String, String> = when (format) {
            InitFormat.SYSTEMD -> {
                parameters += listOf("systemd.run=/boot/firstrun.sh", "systemd.run_success_action=reboot", "systemd.unit=kernel-command-line.target")
                mapOf("firstrun.sh" to systemd(s, passwordHash))
            }
            InitFormat.PRESEED -> mapOf("rpi-preseed.toml" to preseed(s, passwordHash))
            InitFormat.CLOUD_INIT, InitFormat.CLOUD_INIT_RPI -> {
                val instance = "pi-imager-android-${UUID.randomUUID()}"
                parameters += "ds=nocloud;i=$instance"
                buildMap<String, String> {
                    put("meta-data", "instance-id: $instance\n")
                    put("user-data", cloud(s, passwordHash))
                    if (s.wifi) put("network-config", network(s))
                }
            }
            InitFormat.NONE -> error("Unsupported first-boot format")
        }
        return BootPayload(files.mapValues { it.value.toByteArray(Charsets.UTF_8) }, parameters, config)
    }

    private fun cloud(s: Customization, hash: String) = buildString {
        appendLine("#cloud-config")
        appendLine("hostname: ${quoted(s.hostname)}")
        appendLine("manage_etc_hosts: true")
        appendLine("timezone: ${quoted(s.timezone)}")
        appendLine("keyboard:\n  model: pc105\n  layout: ${quoted(s.keyboard)}")
        appendLine("user:\n  name: ${quoted(s.username)}\n  shell: /bin/bash")
        appendLine("  lock_passwd: ${s.password.isEmpty()}")
        if (s.password.isNotEmpty()) appendLine("  passwd: ${quoted(hash)}")
        appendLine("  sudo: ${if (s.passwordlessSudo) quoted("ALL=(ALL) NOPASSWD:ALL") else "null"}")
        if (s.ssh && s.keys().isNotEmpty()) {
            appendLine("  ssh_authorized_keys:")
            s.keys().forEach { appendLine("    - ${quoted(it)}") }
        }
        appendLine("ssh_pwauth: ${s.ssh && s.sshPasswordAuth}")
        appendLine("manage_resolv_conf: false")
        appendLine("runcmd:")
        appendLine("  - [systemctl, ${if (s.ssh) "enable" else "disable"}, --now, ssh]")
        if (s.i2c) {
            appendLine("  - [sh, -c, ${quoted("printf '%s\\n' i2c-dev > /etc/modules-load.d/pi-imager-android-i2c.conf")}]")
            appendLine("  - [modprobe, i2c-dev]")
        }
        if (s.passwordlessSudo) {
            val file = "/etc/sudoers.d/090-imager-${s.username}"
            val command = "printf '%s\\n' ${shell("${s.username} ALL=(ALL) NOPASSWD:ALL")} > ${shell(file)} && chmod 0440 ${shell(file)}"
            appendLine("  - [sh, -c, ${quoted(command)}]")
        }
        if (s.connect) appendLine("  - [sh, -eu, -c, ${quoted(ConnectProvisioning.script(s))}]")
    }

    private fun network(s: Customization) = buildString {
        appendLine("network:\n  version: 2\n  ethernets:\n    eth0:\n      dhcp4: true\n      dhcp6: true\n      optional: true")
        appendLine("  wifis:\n    wlan0:\n      dhcp4: true\n      dhcp6: true\n      optional: true")
        appendLine("      regulatory-domain: ${quoted(s.country)}\n      access-points:")
        appendLine("        ${quoted(s.ssid)}:")
        if (s.hidden) appendLine("          hidden: true")
        if (s.openNetwork) appendLine("          auth:\n            key-management: none")
        else appendLine("          password: ${quoted(s.wifiPassword)}")
    }

    private fun preseed(s: Customization, hash: String) = buildString {
        appendLine("[system]\nhostname = ${quoted(s.hostname)}")
        appendLine("\n[user]\nname = ${quoted(s.username)}\npassword = ${quoted(hash)}\npassword_encrypted = true\ngroups = [\"sudo\"]\npasswordless_sudo = ${s.passwordlessSudo}")
        appendLine("\n[ssh]\nenabled = ${s.ssh}\npassword_authentication = ${s.ssh && s.sshPasswordAuth}")
        if (s.ssh) appendLine("authorized_keys = [${s.keys().joinToString(", ") { quoted(it) }}]")
        if (s.wifi) {
            appendLine("\n[wlan]\nssid = ${quoted(s.ssid)}\nhidden = ${s.hidden}\ncountry = ${quoted(s.country)}")
            if (!s.openNetwork) appendLine("password = ${quoted(s.wifiPassword)}")
        }
        appendLine("\n[locale]\nkeymap = ${quoted(s.keyboard)}\ntimezone = ${quoted(s.timezone)}")
        if (s.i2c || s.spi || s.oneWire) appendLine("\n[interfaces]\ni2c = ${s.i2c}\nspi = ${s.spi}\nonewire = ${s.oneWire}")
        if (s.connect) appendLine("\n[connect]\nenabled = true\nmode = \"token\"\ntoken = ${quoted(s.connectKey)}")
    }

    private fun systemd(s: Customization, hash: String): String = buildString {
        // Legacy Raspberry Pi OS only. imager_custom handles both NetworkManager and dhcpcd.
        appendLine("#!/bin/sh\nset -eu\nCUSTOM=/usr/lib/raspberrypi-sys-mods/imager_custom")
        appendLine("test -x \"\u0024CUSTOM\"")
        appendLine("\"\u0024CUSTOM\" set_hostname ${shell(s.hostname)}")
        appendLine("if [ -x /usr/lib/userconf-pi/userconf ]; then")
        appendLine("  /usr/lib/userconf-pi/userconf ${shell(s.username)} ${shell(hash)}")
        appendLine("else\n  FIRSTUSER=\u0024(getent passwd 1000 | cut -d: -f1)\n  test -n \"\u0024FIRSTUSER\"")
        appendLine("  printf '%s:%s\\n' \"\u0024FIRSTUSER\" ${shell(hash)} | chpasswd -e")
        appendLine("  if [ \"\u0024FIRSTUSER\" != ${shell(s.username)} ]; then\n    usermod -l ${shell(s.username)} \"\u0024FIRSTUSER\"\n    usermod -m -d ${shell("/home/${s.username}")} ${shell(s.username)}\n    groupmod -n ${shell(s.username)} \"\u0024FIRSTUSER\"\n  fi\nfi")
        if (s.ssh) {
            if (s.sshPasswordAuth) appendLine("\"\u0024CUSTOM\" enable_ssh")
            else appendLine("\"\u0024CUSTOM\" enable_ssh -k ${s.keys().joinToString(" ") { shell(it) }}")
        } else appendLine("systemctl disable ssh || true")
        if (s.wifi) {
            val profile = buildString {
                appendLine("[connection]\nid=pi-imager-android\nuuid=${UUID.randomUUID()}\ntype=wifi\ninterface-name=wlan0\nautoconnect=true\nautoconnect-priority=100")
                // Decimal SSID bytes preserve UTF-8, backslashes and whitespace exactly.
                appendLine("\n[wifi]\nmode=infrastructure\nssid=${s.ssid.toByteArray(Charsets.UTF_8).joinToString(";", postfix = ";") { (it.toInt() and 255).toString() }}\nhidden=${s.hidden}")
                if (!s.openNetwork) appendLine("security=802-11-wireless-security\n\n[wifi-security]\nkey-mgmt=wpa-psk\npsk=${keyfile(s.wifiPassword)}")
                appendLine("\n[ipv4]\nmethod=auto\n\n[ipv6]\nmethod=auto")
            }
            appendLine("if [ -d /etc/NetworkManager/system-connections ]; then")
            appendLine("  umask 077\n  printf '%s' ${shell(profile)} > /etc/NetworkManager/system-connections/pi-imager-android.nmconnection")
            appendLine("  chmod 600 /etc/NetworkManager/system-connections/pi-imager-android.nmconnection\n  rfkill unblock wifi || true\nelse")
            // Some imager_custom versions write PASS directly to an NM keyfile.
            val pass = if (s.openNetwork) "" else s.wifiPassword
            val escaped = keyfile(pass)
            appendLine("WIFI_PASS=${shell(pass)}")
            appendLine("if grep -qF 'psk=\u0024{PASS}' \"\u0024CUSTOM\"; then WIFI_PASS=${shell(escaped)}; fi")
            val flag = if (pass.isNotEmpty() && !pass.matches(Regex("[a-fA-F0-9]{64}"))) "-p " else ""
            appendLine("\"\u0024CUSTOM\" set_wlan ${if (s.hidden) "-h " else ""}$flag${shell(s.ssid)} \"\u0024WIFI_PASS\" ${shell(s.country)}")
            appendLine("fi")
        }
        appendLine("\"\u0024CUSTOM\" set_keymap ${shell(s.keyboard)}")
        appendLine("\"\u0024CUSTOM\" set_timezone ${shell(s.timezone)}")
        if (s.i2c) appendLine("printf '%s\\n' i2c-dev > /etc/modules-load.d/pi-imager-android-i2c.conf\nmodprobe i2c-dev")
        if (s.passwordlessSudo) {
            val path = "/etc/sudoers.d/090-imager-${s.username}"
            appendLine("printf '%s\\n' ${shell("${s.username} ALL=(ALL) NOPASSWD:ALL")} > ${shell(path)}\nchmod 0440 ${shell(path)}")
        } else {
            // Remove the legacy image's blanket NOPASSWD rule for its default user.
            appendLine("rm -f /etc/sudoers.d/010_pi-nopasswd")
        }
        if (s.connect) appendLine(ConnectProvisioning.script(s))
        appendLine("for BOOT in /boot /boot/firmware; do")
        appendLine("  if [ -f \"\u0024BOOT/firstrun.sh\" ]; then\n    sed -i 's| systemd.run=[^ ]*||g; s| systemd.run_success_action=[^ ]*||g; s| systemd.unit=kernel-command-line.target||g' \"\u0024BOOT/cmdline.txt\"\n    rm -f \"\u0024BOOT/firstrun.sh\"\n  fi\ndone\nsync")
    }
}
