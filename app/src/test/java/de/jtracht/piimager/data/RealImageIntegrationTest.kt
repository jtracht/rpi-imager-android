package de.jtracht.piimager.data

import de.jtracht.piimager.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Optional integration tests on verified official image copies, supplied by environment. */
class RealImageIntegrationTest {
    @Test fun officialTrixieCloudInit() = checkImage("PI_IMAGER_CLOUD_IMAGE", InitFormat.CLOUD_INIT_RPI)
    @Test fun officialBookwormSystemd() = checkImage("PI_IMAGER_SYSTEMD_IMAGE", InitFormat.SYSTEMD)

    private fun checkImage(variable: String, format: InitFormat) {
        val source = System.getenv(variable)?.let(::File)
        assumeTrue("Set $variable to a verified official image to run this integration test", source?.isFile == true)
        val original = requireNotNull(source)
        val output = File.createTempFile("pi-imager-real-${format.id}", ".img")
        try {
            original.copyTo(output, overwrite = true)
            val before = linuxHash(output)
            val payload = CustomizationGenerator.generate(Customization(enabled = true, hostname = "android-test-pi", username = "testuser", password = "test-only-password", wifi = true, ssid = "Test: \" Wi-Fi", wifiPassword = "test'\"\u0024`123456", ssh = true, sshPasswordAuth = true, i2c = true, spi = true, oneWire = true, connect = true, connectKey = "rpuak_" + "A".repeat(24)), format)
            BootImageEditor.apply(output, payload)
            assertEquals("Customization must never change the Linux root partition", before, linuxHash(output))
            System.getenv("PI_IMAGER_REAL_OUTPUT_DIR")?.let { folder ->
                val dir = File(folder).apply { mkdirs() }
                output.copyTo(File(dir, "customized-${format.id}.img"), overwrite = true)
            }
        } finally { output.delete() }
    }

    private fun linuxHash(file: File): String = RandomAccessFile(file, "r").use { disk ->
        val mbr = ByteArray(512).also(disk::readFully)
        val b = ByteBuffer.wrap(mbr).order(ByteOrder.LITTLE_ENDIAN)
        val offset = (0..3).map { 446 + it * 16 }.first { mbr[it + 4] == 0x83.toByte() }
        val start = (b.getInt(offset + 8).toLong() and 0xffffffffL) * 512
        var remaining = (b.getInt(offset + 12).toLong() and 0xffffffffL) * 512
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024)
        disk.seek(start)
        while (remaining > 0) {
            val count = minOf(remaining, buffer.size.toLong()).toInt()
            disk.readFully(buffer, 0, count)
            hash.update(buffer, 0, count)
            remaining -= count
        }
        hash.digest().joinToString("") { "%02x".format(it) }
    }
}
