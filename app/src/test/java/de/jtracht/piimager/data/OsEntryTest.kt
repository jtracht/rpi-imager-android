package de.jtracht.piimager.data

import de.jtracht.piimager.domain.Customization
import org.junit.Assert.assertThrows
import org.junit.Test

class OsEntryTest {
    @Test fun connectRequiresExplicitCatalogCapability() {
        val settings = Customization(enabled = true, connect = true)
        OsEntry("compatible", capabilities = setOf("rpi_connect")).validateCustomization(settings)
        assertThrows(IllegalArgumentException::class.java) { OsEntry("unknown").validateCustomization(settings) }
        assertThrows(IllegalArgumentException::class.java) { OsEntry("other", capabilities = setOf("rpi_connect_device_identity")).validateCustomization(settings) }
    }

    @Test fun rawFlashingAndOtherSettingsNeedNoConnectCapability() {
        OsEntry("any").validateCustomization(Customization(enabled = false, connect = true))
        OsEntry("any").validateCustomization(Customization(enabled = true, connect = false))
    }
}
