package de.jtracht.piimager.data

import de.jtracht.piimager.domain.BootPayload
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import me.jahnen.libaums.core.fs.UsbFile
import me.jahnen.libaums.core.fs.fat32.Fat32FileSystem
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** Edits only the FAT32 boot partition of the temporary image, never a USB device. */
object BootImageEditor {
    private data class Region(val start: Long, val length: Long)

    fun apply(image: File, payload: BootPayload, checkpoint: () -> Unit = {}) {
        if (payload.files.isEmpty()) return
        RandomAccessFile(image, "rw").use { disk ->
            val region = partitions(disk).firstOrNull { candidate -> isFat32(disk, candidate) }
                ?: error("Customization requires an MBR/GPT image with a FAT32 boot partition")
            val driver = RegionDriver(disk, region, checkpoint)
            validateRootChains(disk, region, checkpoint)
            val root = requireNotNull(Fat32FileSystem.read(driver)).rootDirectory
            check(find(root, "config.txt") != null && find(root, "cmdline.txt") != null) { "The FAT32 partition is not a supported Raspberry Pi boot partition" }
            val cmdline = read(root, "cmdline.txt").toString(Charsets.UTF_8).trim()
            require(cmdline.isNotBlank() && !cmdline.contains('\u0000')) { "Invalid kernel command line" }
            val retained = cmdline.split(Regex("\\s+")).filterNot { token ->
                token.startsWith("systemd.run=") || token.startsWith("systemd.run_success_action=") ||
                    token == "systemd.unit=kernel-command-line.target" || token.startsWith("ds=nocloud") ||
                    token.startsWith("cfg80211.ieee80211_regdom=")
            }
            val expected = payload.files.toMutableMap()
            expected["cmdline.txt"] = ((retained + payload.cmdline).joinToString(" ") + "\n").toByteArray()
            if (payload.config.isNotEmpty()) {
                val config = read(root, "config.txt").toString(Charsets.UTF_8)
                val lines = config.lineSequence().filterNot { line ->
                    payload.config.any { item ->
                        if (item.startsWith("dtparam=")) line.trim().startsWith(item.substringBeforeLast('=') + "=")
                        else line.trim() == item
                    }
                }.toList()
                expected["config.txt"] = (lines.joinToString("\n").trimEnd() + "\n\n[all]\n" + payload.config.joinToString("\n") + "\n").toByteArray()
            }
            val generatedNames = setOf("firstrun.sh", "user-data", "meta-data", "network-config", "rpi-preseed.toml")
            // Remove stale provisioning mechanisms from previously customized local images.
            generatedNames.filter { it !in payload.files && it != "network-config" }.forEach { find(root, it)?.delete() }
            expected.forEach { (name, bytes) ->
                checkpoint()
                require(bytes.size <= 1024 * 1024 && '/' !in name && '\\' !in name) { "Invalid boot payload" }
                val file = find(root, name) ?: root.createFile(name)
                file.use {
                    it.length = bytes.size.toLong()
                    it.write(0, ByteBuffer.wrap(bytes))
                }
            }
            synchronizeMirrors(disk, region, checkpoint)
            disk.fd.sync()
            // A fresh filesystem instance reads back data and directory entries from disk.
            val checked = requireNotNull(Fat32FileSystem.read(driver)).rootDirectory
            expected.forEach { (name, bytes) ->
                check(read(checked, name).contentEquals(bytes)) { "Boot customization verification failed: $name" }
            }
        }
    }

    private fun find(root: UsbFile, name: String): UsbFile? = root.listFiles().firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun read(root: UsbFile, name: String): ByteArray {
        val file = find(root, name) ?: error("Missing boot file: $name")
        require(!file.isDirectory && file.length in 0..1024 * 1024) { "Boot file is too large: $name" }
        return ByteArray(file.length.toInt()).also { if (it.isNotEmpty()) file.read(0, ByteBuffer.wrap(it)) }
    }

    private fun bytes(disk: RandomAccessFile, offset: Long, size: Int): ByteArray {
        require(offset >= 0 && size >= 0 && offset <= disk.length() - size) { "Partition extends beyond the image" }
        return ByteArray(size).also { disk.seek(offset); disk.readFully(it) }
    }
    private fun uint(buffer: ByteBuffer, offset: Int) = buffer.getInt(offset).toLong() and 0xffffffffL

    private fun partitions(disk: RandomAccessFile): List<Region> {
        val mbr = bytes(disk, 0, 512)
        require(mbr[510] == 0x55.toByte() && mbr[511] == 0xaa.toByte()) { "Invalid image partition signature" }
        val buffer = ByteBuffer.wrap(mbr).order(ByteOrder.LITTLE_ENDIAN)
        val result = mutableListOf<Region>()
        var gpt = false
        for (index in 0..3) {
            val offset = 446 + 16 * index
            val type = mbr[offset + 4].toInt() and 255
            if (type == 0xee) gpt = true
            if (type in setOf(0x0b, 0x0c)) {
                val start = uint(buffer, offset + 8) * 512
                val length = uint(buffer, offset + 12) * 512
                require(start >= 512 && length > 0 && start <= disk.length() - length) { "Invalid FAT partition bounds" }
                result += Region(start, length)
            }
        }
        if (gpt) {
            val header = bytes(disk, 512, 512)
            val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(String(header, 0, 8, Charsets.US_ASCII) == "EFI PART") { "Invalid GPT header" }
            val size = h.getInt(12)
            require(size in 92..512) { "Invalid GPT header length" }
            val expectedHeaderCrc = uint(h, 16)
            h.putInt(16, 0)
            require(CRC32().apply { update(header, 0, size) }.value == expectedHeaderCrc) { "GPT header checksum mismatch" }
            val entriesLba = h.getLong(72)
            val count = h.getInt(80)
            val entrySize = h.getInt(84)
            require(entriesLba in 2..(disk.length() / 512) && count in 1..4096 && entrySize in 128..1024 && count.toLong() * entrySize <= 1024 * 1024) { "Unsupported GPT table" }
            val table = bytes(disk, entriesLba * 512, count * entrySize)
            require(CRC32().apply { update(table) }.value == uint(h, 88)) { "GPT partition table checksum mismatch" }
            val tableBuffer = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
            for (index in 0 until count) {
                val offset = index * entrySize
                if ((0..15).all { table[offset + it] == 0.toByte() }) continue
                val first = tableBuffer.getLong(offset + 32)
                val last = tableBuffer.getLong(offset + 40)
                require(first >= 2 && last >= first && last < disk.length() / 512) { "Invalid GPT partition bounds" }
                result += Region(first * 512, (last - first + 1) * 512)
            }
        }
        // Also accept a standalone FAT32 boot filesystem image.
        if (result.isEmpty() && !gpt) result += Region(0, disk.length())
        return result
    }

    private fun isFat32(disk: RandomAccessFile, region: Region): Boolean {
        if (region.length < 512) return false
        val boot = ByteBuffer.wrap(bytes(disk, region.start, 512)).order(ByteOrder.LITTLE_ENDIAN)
        val b = boot.array()
        if (String(b, 82, 8, Charsets.US_ASCII) != "FAT32   ") return false
        val sector = boot.getShort(11).toInt() and 65535
        val cluster = b[13].toInt() and 255
        val reserved = boot.getShort(14).toInt() and 65535
        val fats = b[16].toInt() and 255
        val sectors = uint(boot, 32)
        val fatSectors = uint(boot, 36)
        val dataSectors = sectors - reserved - fats * fatSectors
        val root = uint(boot, 44)
        val infoSector = boot.getShort(48).toInt() and 65535
        val flags = boot.getShort(40).toInt() and 65535
        require(sector == 512 && cluster in 1..128 && cluster.countOneBits() == 1 && reserved in 1..32767 && fats in 1..2 &&
            sectors > 0 && sectors * sector <= region.length && fatSectors > 0 && dataSectors > 0 &&
            dataSectors / cluster + 2 <= fatSectors * sector / 4 && root in 2..(dataSectors / cluster + 1) &&
            infoSector in 1 until reserved && (flags and 0x80 == 0 || flags and 0x0f < fats)) { "Invalid FAT32 boot geometry" }
        return true
    }

    private fun validateRootChains(disk: RandomAccessFile, region: Region, checkpoint: () -> Unit) {
        val b = ByteBuffer.wrap(bytes(disk, region.start, 512)).order(ByteOrder.LITTLE_ENDIAN)
        val reserved = b.getShort(14).toInt() and 65535
        val fatCount = b.get(16).toInt() and 255
        val fatSectors = uint(b, 36)
        val flags = b.getShort(40).toInt() and 65535
        val active = if (flags and 0x80 == 0) 0 else flags and 0x0f
        val fatStart = region.start + (reserved + active * fatSectors) * 512
        val dataStart = region.start + (reserved + fatCount * fatSectors) * 512
        val clusterSize = (b.get(13).toInt() and 255) * 512
        val maxCluster = (uint(b, 32) - reserved - fatCount * fatSectors) * 512 / clusterSize + 1
        fun chain(start: Long, limit: Long): List<Long> {
            val seen = linkedSetOf<Long>()
            var current = start
            while (current < 0x0ffffff8L) {
                checkpoint()
                require(current in 2..maxCluster && seen.size < limit && seen.add(current)) { "Invalid or cyclic boot filesystem cluster chain" }
                current = uint(ByteBuffer.wrap(bytes(disk, fatStart + current * 4, 4)).order(ByteOrder.LITTLE_ENDIAN), 0) and 0x0fffffff
            }
            return seen.toList()
        }
        val root = chain(uint(b, 44), 8L * 1024 * 1024 / clusterSize)
        for (cluster in root) {
            val directory = ByteBuffer.wrap(bytes(disk, dataStart + (cluster - 2) * clusterSize, clusterSize)).order(ByteOrder.LITTLE_ENDIAN)
            for (offset in 0 until clusterSize step 32) {
                val marker = directory.get(offset).toInt() and 255
                if (marker == 0) return
                val attributes = directory.get(offset + 11).toInt() and 255
                if (marker == 0xe5 || attributes and 0x18 != 0 || attributes == 0x0f) continue
                val length = uint(directory, offset + 28)
                if (length == 0L) continue
                require(length <= region.length) { "Boot file exceeds partition" }
                val start = ((directory.getShort(offset + 20).toInt() and 65535).toLong() shl 16) or (directory.getShort(offset + 26).toInt() and 65535).toLong()
                val minimum = (length + clusterSize - 1) / clusterSize
                require(chain(start, minimum + 64).size >= minimum) { "Truncated boot file cluster chain" }
            }
        }
    }

    private fun synchronizeMirrors(disk: RandomAccessFile, region: Region, checkpoint: () -> Unit) {
        val b = ByteBuffer.wrap(bytes(disk, region.start, 512)).order(ByteOrder.LITTLE_ENDIAN)
        val reserved = b.getShort(14).toInt() and 65535
        val count = b.get(16).toInt() and 255
        val fatBytes = uint(b, 36) * 512
        val mirrored = b.getShort(40).toInt() and 0x80 == 0
        // libaums 0.10 updates only its active FAT. Mirror every changed table on disk.
        if (mirrored && count > 1) {
            var offset = 0L
            while (offset < fatBytes) {
                checkpoint()
                val chunk = bytes(disk, region.start + reserved * 512 + offset, minOf(65536L, fatBytes - offset).toInt())
                for (index in 1 until count) {
                    disk.seek(region.start + reserved * 512 + index * fatBytes + offset)
                    disk.write(chunk)
                }
                offset += chunk.size
            }
        }
        val info = b.getShort(48).toInt() and 65535
        val backup = b.getShort(50).toInt() and 65535
        if (backup in 1 until reserved && backup + info < reserved) {
            val primary = bytes(disk, region.start + info * 512, 512)
            disk.seek(region.start + (backup + info) * 512)
            disk.write(primary)
        }
    }

    /** Fat32FileSystem's interface uses byte offsets, matching libaums ByteBlockDevice. */
    private class RegionDriver(val disk: RandomAccessFile, val region: Region, val checkpoint: () -> Unit) : BlockDeviceDriver {
        override val blockSize = 512
        override val blocks get() = region.length / blockSize
        override fun init() = Unit
        private fun seek(offset: Long, count: Int) {
            checkpoint()
            require(offset >= 0 && count >= 0 && offset <= region.length - count) { "FAT operation exceeds boot partition" }
            disk.seek(region.start + offset)
        }
        override fun read(deviceOffset: Long, buffer: ByteBuffer) {
            seek(deviceOffset, buffer.remaining())
            disk.readFully(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
            buffer.position(buffer.limit())
        }
        override fun write(deviceOffset: Long, buffer: ByteBuffer) {
            seek(deviceOffset, buffer.remaining())
            disk.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
            buffer.position(buffer.limit())
        }
    }
}
