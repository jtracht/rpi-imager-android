package de.jtracht.piimager.domain

import org.junit.Assert.*
import org.junit.Test
import org.tukaani.xz.MemoryLimitException
import org.tukaani.xz.XZInputStream
import java.io.*
import java.util.Base64
import java.util.zip.*

class ImageInputTest {
    @Test fun officialImageDictionarySizeFitsWithDecoderOverhead() {
        val packed = Base64.getDecoder().decode("/Td6WFoAAATm1rRGAgAhARwAAAAQz1jM4AFfAB9dACCbiIdV+BJaWlDqihmi/YCy9BkQftGmoNOPSi9mAAAAAI8e8FuJXBFiAAE74AIAAADnLDUvscRn+wIAAAAABFla")
        assertThrows(MemoryLimitException::class.java) { XZInputStream(packed.inputStream(), 65536).use { it.readBytes() } }
        val decoded = ImageInput.unpack(packed.inputStream().buffered()).use { it.readBytes() }
        assertEquals("Android XZ regression\n".repeat(16), decoded.toString(Charsets.UTF_8))
    }
    @Test fun magicDetectionHandlesOneByteReadsFromANetworkLikeSource() {
        val encoded = ByteArrayOutputStream().also { GZIPOutputStream(it).use { stream -> stream.write("test image".toByteArray()) } }.toByteArray()
        val shortReads = object : ByteArrayInputStream(encoded) {
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 1))
            override fun available() = 0
        }
        assertEquals("test image", ImageInput.unpack(shortReads.buffered()).use { it.readBytes().toString(Charsets.UTF_8) })
    }
    @Test fun shortRawImageLosesNoBytesToSignatureDetection() {
        val bytes = byteArrayOf(1, 2, 3)
        assertArrayEquals(bytes, ImageInput.unpack(bytes.inputStream().buffered()).use { it.readBytes() })
    }
    @Test fun zipSelectsTheFirstImageAndSkipsDirectoriesAndMetadata() {
        val bytes = ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { zip ->
                for ((name, value) in listOf("folder/" to "", "readme.txt" to "skip", "folder/BOOT.IMG" to "first image", "other.iso" to "second image")) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry()
                }
            }
        }.toByteArray()
        assertEquals("first image", ImageInput.unpack(bytes.inputStream().buffered()).use { it.readBytes().toString(Charsets.UTF_8) })
    }
    @Test fun zipWithoutAnImageIsRejected() {
        val bytes = ByteArrayOutputStream().also { buffer -> ZipOutputStream(buffer).use { it.putNextEntry(ZipEntry("readme.txt")); it.write(1); it.closeEntry() } }.toByteArray()
        assertThrows(IllegalStateException::class.java) { ImageInput.unpack(bytes.inputStream().buffered()) }
    }
}
