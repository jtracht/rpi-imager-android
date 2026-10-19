package de.jtracht.piimager.domain

import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

object ImageInput {
    fun unpack(input: BufferedInputStream): InputStream {
        input.mark(8)
        val magic = ByteArray(6)
        var count = 0
        while (count < magic.size) {
            val n = input.read(magic, count, magic.size - count)
            if (n < 0) break
            check(n > 0) { "Image source made no progress" }
            count += n
        }
        input.reset()
        return when {
            // A 64-MiB LZMA2 dictionary needs additional decoder overhead (65640 KiB total).
            count >= 6 && magic.contentEquals(byteArrayOf(0xfd.toByte(), 0x37, 0x7a, 0x58, 0x5a, 0x00)) -> XZInputStream(input, 96 * 1024)
            count >= 2 && magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte() -> GZIPInputStream(input)
            count >= 2 && magic[0] == 0x50.toByte() && magic[1] == 0x4b.toByte() -> {
                val zip = ZipInputStream(input)
                var item = zip.nextEntry
                while (item != null && (item.isDirectory || !item.name.lowercase(Locale.ROOT).let { it.endsWith(".img") || it.endsWith(".iso") })) item = zip.nextEntry
                if (item == null) { zip.close(); error("ZIP contains no IMG or ISO file") }
                zip
            }
            else -> input
        }
    }
}
