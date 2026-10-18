package de.jtracht.piimager.domain

/** Auth-key provisioning follows the pinned upstream contract, without borrowing its OAuth client ID. */
object ConnectProvisioning {
    const val SETTINGS_URL = "https://connect.raspberrypi.com/settings"

    fun validateKey(key: String) {
        require(key.matches(Regex("rp[uo]ak_[1-9A-HJ-NP-Za-km-z]{24}"))) {
            "Paste a personal or organisation Connect auth key (rpuak_ / rpoak_ followed by 24 Base58 characters)"
        }
    }

    fun script(settings: Customization): String {
        validateKey(settings.connectKey)
        // Resolve the user's actual home and primary group after account creation. Never install packages.
        return buildString {
            appendLine("TARGET_USER=${CustomizationGenerator.shell(settings.username)}")
            appendLine("TARGET_HOME=\u0024(getent passwd \"\u0024TARGET_USER\" | cut -d: -f6)")
            appendLine("TARGET_GROUP=\u0024(id -gn \"\u0024TARGET_USER\")")
            appendLine("test -n \"\u0024TARGET_HOME\" && test -d \"\u0024TARGET_HOME\"")
            appendLine("CONNECT_DIR=\"\u0024TARGET_HOME/.config/com.raspberrypi.connect\"")
            appendLine("USER_UNITS=\"\u0024TARGET_HOME/.config/systemd/user\"")
            appendLine("umask 077")
            appendLine("install -o \"\u0024TARGET_USER\" -g \"\u0024TARGET_GROUP\" -m 700 -d \"\u0024TARGET_HOME/.config\" \"\u0024CONNECT_DIR\" \"\u0024TARGET_HOME/.config/systemd\" \"\u0024USER_UNITS\" \"\u0024USER_UNITS/default.target.wants\" \"\u0024USER_UNITS/rpi-connect.service.wants\"")
            appendLine("printf '%s\\n' ${CustomizationGenerator.shell(settings.connectKey)} > \"\u0024CONNECT_DIR/auth.key\"")
            appendLine("chown \"\u0024TARGET_USER:\u0024TARGET_GROUP\" \"\u0024CONNECT_DIR/auth.key\"")
            appendLine("chmod 600 \"\u0024CONNECT_DIR/auth.key\"")
            for (unit in listOf("rpi-connect.service", "rpi-connect-signin.path", "rpi-connect-wayvnc.service")) {
                appendLine("UNIT_SRC=/usr/lib/systemd/user/$unit")
                appendLine("[ -f \"\u0024UNIT_SRC\" ] || UNIT_SRC=/lib/systemd/user/$unit")
                // Lite has no WayVNC; remote shell still works without it.
                if (unit.endsWith("wayvnc.service")) appendLine("if [ -f \"\u0024UNIT_SRC\" ]; then")
                else appendLine("test -f \"\u0024UNIT_SRC\"")
                val wantedBy = if (unit == "rpi-connect.service") "default.target" else "rpi-connect.service"
                appendLine("ln -sf \"\u0024UNIT_SRC\" \"\u0024USER_UNITS/$wantedBy.wants/$unit\"")
                if (unit.endsWith("wayvnc.service")) appendLine("fi")
            }
            appendLine("install -d -m 0755 /var/lib/systemd/linger")
            appendLine("install -m 0644 /dev/null \"/var/lib/systemd/linger/\u0024TARGET_USER\"")
            appendLine("loginctl enable-linger \"\u0024TARGET_USER\" || true")
            appendLine("sleep 2")
            appendLine("systemctl --quiet --user --machine=\"\u0024TARGET_USER@.host\" daemon-reload || true")
            appendLine("systemctl --quiet --user --machine=\"\u0024TARGET_USER@.host\" start rpi-connect.service rpi-connect-signin.path || true")
            appendLine("if [ -f /usr/lib/systemd/user/rpi-connect-wayvnc.service ] || [ -f /lib/systemd/user/rpi-connect-wayvnc.service ]; then")
            appendLine("  systemctl --quiet --user --machine=\"\u0024TARGET_USER@.host\" start rpi-connect-wayvnc.service || true\nfi")
        }
    }
}
