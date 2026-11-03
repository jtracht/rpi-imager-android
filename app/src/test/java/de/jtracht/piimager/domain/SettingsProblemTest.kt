package de.jtracht.piimager.domain

import de.jtracht.piimager.data.OsEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsProblemTest {
    private val valid = Customization(enabled = true, password = "Synthetic-Test-1")

    private fun problem(settings: Customization, format: InitFormat = InitFormat.CLOUD_INIT_RPI): SettingsProblem =
        assertThrows(InvalidSettingsException::class.java) { settings.validate(format) }.problem

    @Test fun validSettingsPass() {
        valid.validate(InitFormat.CLOUD_INIT_RPI)
    }

    @Test fun eachRuleReportsItsOwnProblem() {
        assertEquals(SettingsProblem.FORMAT, problem(valid, InitFormat.NONE))
        assertEquals(SettingsProblem.HOSTNAME, problem(valid.copy(hostname = "-bad-")))
        assertEquals(SettingsProblem.USERNAME, problem(valid.copy(username = "root")))
        assertEquals(SettingsProblem.PASSWORD_OR_KEY, problem(valid.copy(password = "")))
        assertEquals(SettingsProblem.PASSWORD_LENGTH, problem(valid.copy(password = "short")))
        assertEquals(SettingsProblem.TIMEZONE, problem(valid.copy(timezone = "Mars/Base")))
        assertEquals(SettingsProblem.KEYBOARD, problem(valid.copy(keyboard = "DE")))
        assertEquals(SettingsProblem.WIFI_COUNTRY, problem(valid.copy(country = "XX")))
        assertEquals(SettingsProblem.WIFI_NAME, problem(valid.copy(wifi = true, ssid = "")))
        assertEquals(SettingsProblem.WIFI_PASSPHRASE, problem(valid.copy(wifi = true, ssid = "Test", wifiPassword = "short")))
        assertEquals(SettingsProblem.SSH_KEY_REQUIRED, problem(valid.copy(ssh = true)))
        assertEquals(SettingsProblem.SSH_KEY_NOT_PUBLIC, problem(valid.copy(ssh = true, publicKeys = "-----BEGIN OPENSSH PRIVATE KEY-----")))
        assertEquals(SettingsProblem.CONNECT_KEY, problem(valid.copy(connect = true, connectKey = "rpuak_short")))
    }

    @Test fun catalogWithoutConnectSupportReportsItsProblem() {
        val settings = valid.copy(connect = true, connectKey = "rpuak_" + "A".repeat(24))
        val error = assertThrows(InvalidSettingsException::class.java) { OsEntry("image").validateCustomization(settings) }
        assertEquals(SettingsProblem.CONNECT_UNSUPPORTED, error.problem)
    }

    @Test fun problemsStayIllegalArgumentsWithEnglishMessages() {
        val error: IllegalArgumentException = InvalidSettingsException(SettingsProblem.SSH_KEY_REQUIRED)
        assertEquals("Add at least one SSH public key", error.message)
        assertEquals(SettingsProblem.entries.size, SettingsProblem.entries.map { it.english }.toSet().size)
    }
}
