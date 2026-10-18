package de.jtracht.piimager.domain

import org.apache.commons.codec.digest.Sha2Crypt
import org.junit.Assert.*
import org.junit.Test

class CustomizationTest {
    private fun settings() = Customization(enabled = true, password = "test-only-password", hostname = "test-pi", username = "testuser")
    private fun text(payload: BootPayload, name: String) = requireNotNull(payload.files[name]).toString(Charsets.UTF_8)

    @Test fun exportSamplesForIndependentSyntaxChecks() {
        val s = settings().copy(wifi = true, ssid = "Test: \" Wi-Fi", wifiPassword = "test'\"\u0024`123456", ssh = true, sshPasswordAuth = true, i2c = true, spi = true, oneWire = true)
        for (format in InitFormat.entries.filter { it != InitFormat.NONE }) {
            val folder = java.io.File("build/customization-samples/${format.id}").apply { mkdirs() }
            CustomizationGenerator.generate(s, format).files.forEach { (name, contents) -> java.io.File(folder, name).writeBytes(contents) }
        }
    }

    @Test fun disabledDoesNotCreatePayload() {
        assertTrue(CustomizationGenerator.generate(Customization(), InitFormat.NONE).files.isEmpty())
    }
    @Test fun cloudHashesPasswordAndUsesStableInstanceWithinPayload() {
        val payload = CustomizationGenerator.generate(settings().copy(ssh = true, sshPasswordAuth = true), InitFormat.CLOUD_INIT_RPI)
        val cloud = text(payload, "user-data")
        assertTrue(cloud.startsWith("#cloud-config\n"))
        assertFalse(cloud.contains("test-only-password"))
        val hash = Regex("passwd: \"([^\"]+)\"").find(cloud)?.groupValues?.get(1) ?: error("Missing hash")
        assertTrue(hash.startsWith("\u00246\u0024"))
        assertEquals(hash, Sha2Crypt.sha512Crypt("test-only-password".toByteArray(), hash))
        val instance = text(payload, "meta-data").trim().substringAfter("instance-id: ")
        assertTrue(payload.cmdline.contains("ds=nocloud;i=$instance"))
        val second = CustomizationGenerator.generate(settings(), InitFormat.CLOUD_INIT_RPI)
        assertNotEquals(text(payload, "meta-data"), text(second, "meta-data"))
        assertTrue(cloud.contains("[systemctl, enable, --now, ssh]"))
    }
    @Test fun wifiQuotesUntrustedScalarsAndRetainsEthernet() {
        val s = settings().copy(wifi = true, ssid = "Cafe: \"\\ Wi-Fi", wifiPassword = "abc'\"\u0024`123456", hidden = true)
        val network = text(CustomizationGenerator.generate(s, InitFormat.CLOUD_INIT), "network-config")
        assertTrue(network.contains("ethernets:\n    eth0:"))
        assertTrue(network.contains("hidden: true"))
        assertTrue(network.contains(CustomizationGenerator.quoted(s.ssid)))
        assertTrue(network.contains(CustomizationGenerator.quoted(s.wifiPassword)))
    }
    @Test fun openWifiDoesNotRetainPassword() {
        val s = settings().copy(wifi = true, ssid = "Public", openNetwork = true, wifiPassword = "old-password")
        val network = text(CustomizationGenerator.generate(s, InitFormat.CLOUD_INIT), "network-config")
        assertTrue(network.contains("key-management: none"))
        assertFalse(network.contains("old-password"))
        val toml = text(CustomizationGenerator.generate(s, InitFormat.PRESEED), "rpi-preseed.toml")
        assertFalse(toml.contains("old-password"))
    }
    @Test fun allSupportedFormatsCarryIdentityAndHardwareSettings() {
        for (format in InitFormat.entries.filter { it != InitFormat.NONE }) {
            val payload = CustomizationGenerator.generate(settings().copy(i2c = true, spi = true, oneWire = true), format)
            val combined = payload.files.values.joinToString { it.toString(Charsets.UTF_8) }
            assertTrue(combined.contains("test-pi"))
            assertTrue(combined.contains("testuser"))
            assertFalse(combined.contains("test-only-password"))
            if (format != InitFormat.PRESEED) assertEquals(listOf("dtparam=i2c_arm=on", "dtparam=spi=on", "dtoverlay=w1-gpio"), payload.config)
            else assertTrue(combined.contains("onewire = true"))
        }
    }
    @Test fun rejectsCommandAndKernelArgumentInjection() {
        for (s in listOf(settings().copy(hostname = "pi\nreboot"), settings().copy(username = "pi`id`"), settings().copy(country = "DE init=/bin/sh"), settings().copy(keyboard = "de;id"), settings().copy(username = "root"))) {
            assertThrows(IllegalArgumentException::class.java) { CustomizationGenerator.generate(s, InitFormat.SYSTEMD) }
        }
    }
    @Test fun rejectsUnsupportedFormatAndInvalidWifi() {
        assertThrows(IllegalArgumentException::class.java) { settings().validate(InitFormat.NONE) }
        for (s in listOf(settings().copy(wifi = true, ssid = "x".repeat(33)), settings().copy(wifi = true, ssid = "ok", wifiPassword = "short"), settings().copy(wifi = true, ssid = "bad\nssid", openNetwork = true))) {
            assertThrows(IllegalArgumentException::class.java) { s.validate(InitFormat.CLOUD_INIT) }
        }
    }
    @Test fun rejectsPrivateAndMismatchedSshKeys() {
        for (key in listOf("-----BEGIN OPENSSH PRIVATE KEY-----", "ssh-ed25519 ZmFrZQ==", "ssh-rsa NOT_BASE64")) {
            assertThrows(IllegalArgumentException::class.java) { settings().copy(ssh = true, publicKeys = key).validate(InitFormat.CLOUD_INIT) }
        }
    }
    @Test fun keyOnlyAccountStaysLockedAndPasswordAuthDisabled() {
        val type = "ssh-ed25519".toByteArray()
        val raw = java.nio.ByteBuffer.allocate(4 + type.size + 4 + 32).putInt(type.size).put(type).putInt(32).put(ByteArray(32) { (it + 1).toByte() }).array()
        val key = "ssh-ed25519 " + java.util.Base64.getEncoder().encodeToString(raw) + " test-only"
        val cloud = text(CustomizationGenerator.generate(settings().copy(password = "", ssh = true, publicKeys = key), InitFormat.CLOUD_INIT_RPI), "user-data")
        assertTrue(cloud.contains("lock_passwd: true"))
        assertTrue(cloud.contains("ssh_pwauth: false"))
        assertFalse(cloud.contains("  passwd:"))
        assertTrue(cloud.contains(key))
    }
    @Test fun shellQuotingDoesNotExposeExpansion() {
        assertEquals("'a'\"'\"'\u0024`b'", CustomizationGenerator.shell("a'\u0024`b"))
        val script = text(CustomizationGenerator.generate(settings().copy(wifi = true, ssid = "test'\u0024net", wifiPassword = "safe'\u0024`123456"), InitFormat.SYSTEMD), "firstrun.sh")
        assertTrue(script.contains(CustomizationGenerator.shell("test'\u0024net")))
        assertTrue(script.contains("rm -f \"\u0024BOOT/firstrun.sh\""))
        assertTrue(script.contains("sync\n"))
    }
}
