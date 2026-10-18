package de.jtracht.piimager.domain

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.min

/** A whole device, addressed in logical blocks, never a mounted filesystem. */
interface BlockTarget : AutoCloseable {
    val blockSize: Int
    val blocks: Long
    fun write(lba: Long, data: ByteBuffer)
    fun read(lba: Long, data: ByteBuffer)
}

data class PreparedImage(val file: File, val name: String, val sha256: String)

object Imaging {
    fun digest(input: InputStream): String = input.use {
        val hash = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(1024 * 1024)
        while (true) {
            val count = it.read(bytes)
            if (count < 0) break
            hash.update(bytes, 0, count)
        }
        hash.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    /** Progress also serves as a cancellation checkpoint before each USB operation. */
    fun flash(file: File, target: BlockTarget, progress: (verify: Boolean, fraction: Float) -> Unit) {
        require(file.length() > 0) { "Empty image" }
        require(target.blockSize in 512..65536 && target.blockSize.countOneBits() == 1) { "Unsupported sector size" }
        // libaums 0.10 uses SCSI READ/WRITE(10), whose LBA is 32 bits.
        require(target.blocks in 1..0xFFFFFFFFL) { "Device exceeds SCSI READ/WRITE(10) capacity" }
        val requiredBlocks = (file.length() - 1) / target.blockSize + 1
        require(requiredBlocks <= target.blocks) { "Image is larger than the USB device" }
        val chunk = (1024 * 1024 / target.blockSize) * target.blockSize
        for (verify in listOf(false, true)) {
            file.inputStream().buffered().use { source ->
                var offset = 0L
                while (offset < file.length()) {
                    progress(verify, offset.toFloat() / file.length())
                    val count = min(chunk.toLong(), file.length() - offset).toInt()
                    val padded = ((count + target.blockSize - 1) / target.blockSize) * target.blockSize
                    val expected = ByteArray(padded)
                    var received = 0
                    while (received < count) {
                        val n = source.read(expected, received, count - received)
                        check(n > 0) { "Image ended unexpectedly" }
                        received += n
                    }
                    val lba = offset / target.blockSize
                    if (verify) {
                        val actual = ByteArray(padded)
                        val buffer = ByteBuffer.wrap(actual)
                        target.read(lba, buffer)
                        check(!buffer.hasRemaining()) { "Incomplete USB read" }
                        check(expected.contentEquals(actual)) { "Verification failed at byte $offset" }
                    } else {
                        val buffer = ByteBuffer.wrap(expected)
                        target.write(lba, buffer)
                        check(!buffer.hasRemaining()) { "Incomplete USB write" }
                    }
                    offset += count
                }
            }
            progress(verify, 1f)
        }
    }
}
