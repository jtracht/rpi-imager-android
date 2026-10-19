package de.jtracht.piimager.data

import de.jtracht.piimager.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class PreparationRequestTest {
    private val model = PiModel("Pi 5", "", setOf("pi5-64bit"), false)
    private val image = OsEntry("Test", url = "https://example.invalid/test.img", initFormat = "cloudinit-rpi", devices = setOf("pi5-64bit"))
    private fun request(entry: OsEntry? = image, settings: Customization = Customization()) = PreparationRequest(entry, null, settings, InitFormat.NONE, model)

    @Test fun requiresASelectedSource() {
        assertThrows(IllegalArgumentException::class.java) { request(null).validate() }
    }
    @Test fun rejectsIncompatibleImageBeforeStartingAService() {
        request().validate()
        assertThrows(IllegalArgumentException::class.java) { request(image.copy(devices = setOf("pi1-32bit"))).validate() }
    }
    @Test fun validatesSessionCredentialsBeforeStarting() {
        val settings = Customization(enabled = true, password = "test-only-password")
        request(settings = settings).validate()
        assertThrows(IllegalArgumentException::class.java) { request(settings = settings.copy(username = "root")).validate() }
        assertThrows(IllegalArgumentException::class.java) { request(settings = settings.copy(connect = true, connectKey = "rpuak_" + "A".repeat(24))).validate() }
        request(image.copy(capabilities = setOf("rpi_connect")), settings.copy(connect = true, connectKey = "rpuak_" + "A".repeat(24))).validate()
    }
    @Test fun hashingCanBeCancelledBetweenChunksAndClosesTheInput() {
        var closed = false
        val input = object : ByteArrayInputStream(ByteArray(3 * 1024 * 1024)) {
            override fun close() { closed = true; super.close() }
        }
        var checkpoints = 0
        assertThrows(IllegalStateException::class.java) {
            Imaging.digest(input) { checkpoints++; check(checkpoints < 2) { "cancelled" } }
        }
        assertEquals(2, checkpoints)
        assertTrue(closed)
    }
}
