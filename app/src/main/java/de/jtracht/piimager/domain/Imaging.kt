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

/** A verified image in private storage. [settings] are the first-boot settings already written into it (session memory only). */
data class PreparedImage(val file: File, val name: String, val sha256: String, val settings: Customization = Customization())

object Imaging {
    fun digest(input: InputStream, checkpoint: () -> Unit = {}): String = input.use {
        val hash = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(1024 * 1024)
        while (true) {
            checkpoint()
            val count = it.read(bytes)
            if (count < 0) break
            hash.update(bytes, 0, count)
        }
        hash.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    /** Progress also serves as a cancellation checkpoint before each USB operation. */
    fun flash(file: File, target: BlockTarget, progress: (verify: Boolean, fraction: Float) -> Unit) {
        requireOperation(file.length() > 0, OperationProblem.EMPTY_IMAGE)
        requireOperation(target.blockSize in 512..65536 && target.blockSize.countOneBits() == 1, OperationProblem.USB_UNSUPPORTED)
        // libaums 0.10 uses SCSI READ/WRITE(10), whose LBA is 32 bits.
        requireOperation(target.blocks in 1..0xFFFFFFFFL, OperationProblem.USB_UNSUPPORTED)
        val requiredBlocks = (file.length() - 1) / target.blockSize + 1
        requireOperation(requiredBlocks <= target.blocks, OperationProblem.IMAGE_TOO_LARGE_FOR_DEVICE)
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
                        checkOperation(n > 0, OperationProblem.IMAGE_ENDED)
                        received += n
                    }
                    val lba = offset / target.blockSize
                    if (verify) {
                        val actual = ByteArray(padded)
                        val buffer = ByteBuffer.wrap(actual)
                        target.read(lba, buffer)
                        checkOperation(!buffer.hasRemaining(), OperationProblem.USB_TRANSFER_FAILED)
                        checkOperation(expected.contentEquals(actual), OperationProblem.VERIFY_FAILED) { offset.toString() }
                    } else {
                        val buffer = ByteBuffer.wrap(expected)
                        target.write(lba, buffer)
                        checkOperation(!buffer.hasRemaining(), OperationProblem.USB_TRANSFER_FAILED)
                    }
                    offset += count
                }
            }
            progress(verify, 1f)
        }
    }
}
