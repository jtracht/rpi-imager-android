package de.jtracht.piimager.domain

import org.junit.Assert.*
import org.junit.Test

class ConnectProvisioningTest {
    private val key = "rpuak_" + "A".repeat(24)
    private val settings = Customization(enabled = true, password = "test-only-password", username = "testuser", connect = true, connectKey = key)

    @Test fun validatesPersonalAndOrganisationKeysButRejectsOtherSecrets() {
        ConnectProvisioning.validateKey(key)
        ConnectProvisioning.validateKey(key.replace("rpuak_", "rpoak_"))
        for (invalid in listOf("", "rpdev_" + "A".repeat(24), "rpuak_" + "A".repeat(23), "rpuak_" + "A".repeat(25), "rpuak_" + "0".repeat(24), key + "\n", "rpuak_$(reboot)", "my-account-password")) {
            assertThrows(IllegalArgumentException::class.java) { settings.copy(connectKey = invalid).validate(InitFormat.SYSTEMD) }
        }
    }

    @Test fun disabledConnectNeverIncludesRememberedKey() {
        for (format in InitFormat.entries.filter { it != InitFormat.NONE }) {
            val payload = CustomizationGenerator.generate(settings.copy(connect = false), format)
            assertFalse(payload.files.values.any { it.toString(Charsets.UTF_8).contains(key) })
        }
    }

    @Test fun cloudAndLegacyInstallPrivateCredentialAfterUserCreation() {
        val script = ConnectProvisioning.script(settings)
        assertTrue(script.indexOf("umask 077") < script.indexOf("printf '%s\\n'"))
        assertTrue(script.contains("chmod 600 \"\u0024CONNECT_DIR/auth.key\""))
        assertTrue(script.contains("getent passwd"))
        assertTrue(script.contains("id -gn"))
        assertTrue(script.contains("default.target.wants/rpi-connect.service"))
        assertTrue(script.contains("rpi-connect.service.wants/rpi-connect-signin.path"))
        assertTrue(script.contains("/var/lib/systemd/linger/\u0024TARGET_USER"))
        for (format in listOf(InitFormat.CLOUD_INIT, InitFormat.CLOUD_INIT_RPI, InitFormat.SYSTEMD)) {
            val file = if (format == InitFormat.SYSTEMD) "firstrun.sh" else "user-data"
            val contents = CustomizationGenerator.generate(settings, format).files.getValue(file).toString(Charsets.UTF_8)
            assertTrue(contents.contains(key))
            assertTrue(contents.contains("rpi-connect-signin.path"))
            if (format == InitFormat.SYSTEMD) assertTrue(contents.indexOf("/usr/lib/userconf-pi/userconf") < contents.indexOf("CONNECT_DIR="))
            else assertTrue(contents.indexOf("  name:") < contents.indexOf("CONNECT_DIR="))
        }
    }

    @Test fun preseedUsesTokenContractAndDisablingCustomizationDropsAllSecrets() {
        val contents = CustomizationGenerator.generate(settings, InitFormat.PRESEED).files.getValue("rpi-preseed.toml").toString(Charsets.UTF_8)
        assertTrue(contents.contains("[connect]\nenabled = true\nmode = \"token\"\ntoken = \"$key\""))
        assertTrue(CustomizationGenerator.generate(settings.copy(enabled = false), InitFormat.NONE).files.isEmpty())
    }
}
