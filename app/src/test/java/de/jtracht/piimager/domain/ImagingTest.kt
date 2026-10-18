package de.jtracht.piimager.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class ImagingTest {
    private class MemoryTarget(override val blockSize: Int = 512, override val blocks: Long = 8,
                               val corrupt: Boolean = false) : BlockTarget {
        val bytes = ByteArray((blocks * blockSize).toInt()) { 0x7f }
        var writes = 0
        override fun write(lba: Long, data: ByteBuffer) {
            writes++
            data.get(bytes, (lba * blockSize).toInt(), data.remaining())
        }
        override fun read(lba: Long, data: ByteBuffer) {
            if (corrupt) bytes[0] = (bytes[0].toInt() xor 1).toByte()
            data.put(bytes, (lba * blockSize).toInt(), data.remaining())
        }
        override fun close() = Unit
    }

    private fun image(size: Int, test: (File) -> Unit) {
        val file = File.createTempFile("imager-test", ".img")
        try { file.writeBytes(ByteArray(size) { (it % 251).toByte() }); test(file) }
        finally { file.delete() }
    }

    @Test fun padsFinalSectorAndVerifies() = image(777) { file ->
        val target = MemoryTarget()
        val phases = mutableSetOf<Boolean>()
        Imaging.flash(file, target) { verify, _ -> phases += verify }
        assertArrayEquals(file.readBytes(), target.bytes.copyOf(777))
        assertTrue(target.bytes.copyOfRange(777, 1024).all { it == 0.toByte() })
        assertEquals(0x7f.toByte(), target.bytes[1024])
        assertEquals(setOf(false, true), phases)
    }

    @Test fun rejectsTooSmallDeviceBeforeFirstWrite() = image(513) { file ->
        val target = MemoryTarget(blocks = 1)
        assertThrows(IllegalArgumentException::class.java) { Imaging.flash(file, target) { _, _ -> } }
        assertEquals(0, target.writes)
    }

    @Test fun detectsReadBackCorruption() = image(512) { file ->
        assertThrows(IllegalStateException::class.java) { Imaging.flash(file, MemoryTarget(corrupt = true)) { _, _ -> } }
    }

    @Test fun cancellationBeforeWriteDoesNotTouchTarget() = image(512) { file ->
        val target = MemoryTarget()
        assertThrows(InterruptedException::class.java) { Imaging.flash(file, target) { _, _ -> throw InterruptedException() } }
        assertEquals(0, target.writes)
    }

    @Test fun usesLogicalBlockAddressesAcrossChunks() = image(1024 * 1024 + 513) { file ->
        val target = MemoryTarget(blocks = 4096)
        Imaging.flash(file, target) { _, _ -> }
        assertArrayEquals(file.readBytes(), target.bytes.copyOf(file.length().toInt()))
        assertEquals(2, target.writes)
    }

    @Test fun sha256KnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Imaging.digest("abc".byteInputStream()))
    }
}
