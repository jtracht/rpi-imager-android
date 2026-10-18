package de.jtracht.piimager.data

import de.jtracht.piimager.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BootImageEditorTest {
    @Test fun provisionsCloudImageWithoutTouchingLinuxPartition() = fixture { file ->
        val payload = CustomizationGenerator.generate(Customization(enabled = true, password = "test-password", wifi = true, ssid = "test-wifi", wifiPassword = "test-passphrase", ssh = true, sshPasswordAuth = true, i2c = true), InitFormat.CLOUD_INIT_RPI)
        BootImageEditor.apply(file, payload)
        val files = readFiles(file)
        payload.files.forEach { (name, data) -> assertArrayEquals(name, data, files[name]) }
        assertTrue(requireNotNull(files["cmdline.txt"]).toString(Charsets.UTF_8).contains("ds=nocloud;i=pi-imager-android-"))
        assertTrue(requireNotNull(files["config.txt"]).toString(Charsets.UTF_8).contains("[all]\ndtparam=i2c_arm=on"))
        RandomAccessFile(file, "r").use { disk ->
            disk.seek(START + SIZE)
            assertEquals(0x5a, disk.read())
            val first = ByteArray(FAT_SECTORS * 512)
            val second = ByteArray(first.size)
            disk.seek(START + 32 * 512); disk.readFully(first)
            disk.readFully(second)
            assertArrayEquals("Both FAT copies must remain synchronized", first, second)
        }
        System.getenv("PI_IMAGER_TEST_OUTPUT")?.let { output -> file.copyTo(File(output), overwrite = true) }
    }
    @Test fun systemdAndPreseedReplaceOldMechanism() = fixture { file ->
        val s = Customization(enabled = true, password = "test-password")
        BootImageEditor.apply(file, CustomizationGenerator.generate(s, InitFormat.SYSTEMD))
        assertTrue(readFiles(file).containsKey("firstrun.sh"))
        BootImageEditor.apply(file, CustomizationGenerator.generate(s, InitFormat.PRESEED))
        val files = readFiles(file)
        assertFalse(files.containsKey("firstrun.sh"))
        assertTrue(files.containsKey("rpi-preseed.toml"))
        assertFalse(requireNotNull(files["cmdline.txt"]).toString(Charsets.UTF_8).contains("systemd.run="))
    }
    @Test fun expandsRootAndWritesLongNames() = fixture { file ->
        val files = (0..20).associate { "long-file-name-$it.txt" to "payload $it".toByteArray() }
        BootImageEditor.apply(file, BootPayload(files, emptyList(), emptyList()))
        val actual = readFiles(file)
        files.forEach { (name, bytes) -> assertArrayEquals(name, bytes, actual[name]) }
    }
    @Test fun cancellationDoesNotWriteLinuxPartition() = fixture { file ->
        val payload = BootPayload(mapOf("user-data" to "#cloud-config".toByteArray()), emptyList(), emptyList())
        assertThrows(InterruptedException::class.java) { BootImageEditor.apply(file, payload) { throw InterruptedException() } }
        RandomAccessFile(file, "r").use { it.seek(START + SIZE); assertEquals(0x5a, it.read()) }
    }
    @Test fun rejectsOutOfBoundsPartitionAndBadGeometry() = fixture { file ->
        RandomAccessFile(file, "rw").use { it.seek(446 + 12); it.write(byteArrayOf(-1, -1, -1, -1)) }
        assertThrows(IllegalArgumentException::class.java) { BootImageEditor.apply(file, BootPayload(mapOf("user-data" to byteArrayOf(1)), emptyList(), emptyList())) }
    }
    @Test fun rejectsRawNonBootImageWithoutModification() {
        val file = File.createTempFile("non-boot", ".img")
        try {
            file.writeBytes(ByteArray(1024))
            val before = file.readBytes()
            assertThrows(IllegalArgumentException::class.java) { BootImageEditor.apply(file, BootPayload(mapOf("user-data" to byteArrayOf(1)), emptyList(), emptyList())) }
            assertArrayEquals(before, file.readBytes())
        } finally { file.delete() }
    }
    @Test fun supportsGptAndRejectsCorruptHeader() = fixture { file ->
        RandomAccessFile(file, "rw").use { disk ->
            disk.setLength(disk.length() + 34 * 512)
            disk.seek(450); disk.write(0xee)
            val table = ByteBuffer.allocate(128 * 128).order(ByteOrder.LITTLE_ENDIAN)
            table.put(0, 1); table.putLong(32, START / 512); table.putLong(40, (START + SIZE) / 512 - 1)
            val header = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
            header.put("EFI PART".toByteArray()); header.putInt(8, 0x10000); header.putInt(12, 92)
            header.putLong(24, 1); header.putLong(32, disk.length() / 512 - 1)
            header.putLong(40, 34); header.putLong(48, disk.length() / 512 - 34)
            header.putLong(72, 2); header.putInt(80, 128); header.putInt(84, 128)
            header.putInt(88, java.util.zip.CRC32().apply { update(table.array()) }.value.toInt())
            header.putInt(16, java.util.zip.CRC32().apply { update(header.array(), 0, 92) }.value.toInt())
            disk.seek(512); disk.write(header.array()); disk.seek(1024); disk.write(table.array())
        }
        val payload = BootPayload(mapOf("user-data" to "#cloud-config\nhostname: gpt-pi\n".toByteArray()), listOf("ds=nocloud;i=test-gpt"), emptyList())
        BootImageEditor.apply(file, payload)
        assertArrayEquals(payload.files["user-data"], readFiles(file)["user-data"])
        RandomAccessFile(file, "rw").use { it.seek(512 + 16); it.write(0) }
        assertThrows(IllegalArgumentException::class.java) { BootImageEditor.apply(file, payload) }
    }
    @Test fun rejectsCyclicRootWithoutWriting() = fixture { file ->
        RandomAccessFile(file, "rw").use { disk ->
            disk.seek(START + 32 * 512 + 2 * 4)
            disk.write(byteArrayOf(2, 0, 0, 0))
        }
        val before = Imaging.digest(file.inputStream())
        assertThrows(IllegalArgumentException::class.java) { BootImageEditor.apply(file, BootPayload(mapOf("user-data" to byteArrayOf(1)), emptyList(), emptyList())) }
        assertEquals(before, Imaging.digest(file.inputStream()))
    }

    /** Independent minimal FAT reader, including LFN reconstruction and cluster chains. */
    private fun readFiles(file: File): Map<String, ByteArray> = RandomAccessFile(file, "r").use { disk ->
        fun next(cluster: Int): Int {
            disk.seek(START + 32 * 512 + cluster * 4)
            val data = ByteArray(4); disk.readFully(data)
            return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int and 0x0fffffff
        }
        fun chain(start: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var cluster = start
            val visited = mutableSetOf<Int>()
            while (cluster in 2 until 0x0ffffff8) {
                check(visited.add(cluster)) { "Cluster cycle" }
                disk.seek(START + DATA_SECTOR * 512 + (cluster - 2) * 512)
                val bytes = ByteArray(512); disk.readFully(bytes); out.write(bytes)
                cluster = next(cluster)
            }
            return out.toByteArray()
        }
        val root = chain(2)
        val names = mutableMapOf<Int, String>()
        val files = mutableMapOf<String, ByteArray>()
        for (offset in root.indices step 32) {
            val first = root[offset].toInt() and 255
            if (first == 0) break
            if (first == 0xe5) { names.clear(); continue }
            val entry = ByteBuffer.wrap(root, offset, 32).slice().order(ByteOrder.LITTLE_ENDIAN)
            if (root[offset + 11] == 0x0f.toByte()) {
                names[first and 31] = listOf(1,3,5,7,9,14,16,18,20,22,24,28,30).map { entry.getShort(it).toInt() and 65535 }.takeWhile { it != 0 && it != 65535 }.map(Int::toChar).joinToString("")
                continue
            }
            val name = if (names.isNotEmpty()) names.toSortedMap().values.joinToString("") else String(root, offset, 8).trim().lowercase() + String(root, offset + 8, 3).trim().let { if (it.isEmpty()) "" else ".$it" }.lowercase()
            names.clear()
            if (root[offset + 11].toInt() and 0x18 != 0) continue
            val cluster = ((entry.getShort(20).toInt() and 65535) shl 16) or (entry.getShort(26).toInt() and 65535)
            val size = entry.getInt(28)
            files[name] = if (size == 0) byteArrayOf() else chain(cluster).copyOf(size)
        }
        files
    }

    private fun fixture(test: (File) -> Unit) {
        val file = File.createTempFile("pi-boot-fixture", ".img")
        try {
            RandomAccessFile(file, "rw").use { disk ->
                disk.setLength(START + SIZE + 4096)
                val mbr = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
                mbr.put(450, 0x0c); mbr.putInt(454, (START / 512).toInt()); mbr.putInt(458, SECTORS)
                mbr.put(466, 0x83.toByte()); mbr.putInt(470, ((START + SIZE) / 512).toInt()); mbr.putInt(474, 8)
                mbr.putShort(510, 0xaa55.toShort()); disk.write(mbr.array())
                val boot = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
                boot.put(0, 0xeb.toByte()); boot.put(1, 0x58); boot.put(2, 0x90.toByte())
                boot.position(3); boot.put("MSWIN4.1".toByteArray()); boot.putShort(11, 512); boot.put(13, 1)
                boot.putShort(14, 32); boot.put(16, 2); boot.put(21, 0xf8.toByte()); boot.putInt(32, SECTORS)
                boot.putInt(36, FAT_SECTORS); boot.putInt(44, 2); boot.putShort(48, 1); boot.putShort(50, 6)
                boot.put(64, 0x80.toByte()); boot.put(66, 0x29); boot.putInt(67, 0x12345678)
                boot.position(71); boot.put("BOOT       ".toByteArray()); boot.position(82); boot.put("FAT32   ".toByteArray())
                boot.putShort(510, 0xaa55.toShort()); disk.seek(START); disk.write(boot.array()); disk.seek(START + 6 * 512); disk.write(boot.array())
                val info = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
                info.putInt(0, 0x41615252); info.putInt(484, 0x61417272); info.putInt(488, SECTORS - DATA_SECTOR - 3); info.putInt(492, 5); info.putInt(508, 0xaa550000.toInt())
                disk.seek(START + 512); disk.write(info.array())
                val fat = ByteBuffer.allocate(FAT_SECTORS * 512).order(ByteOrder.LITTLE_ENDIAN)
                fat.putInt(0, 0x0ffffff8); for (cluster in 1..4) fat.putInt(cluster * 4, 0x0fffffff)
                disk.seek(START + 32 * 512); disk.write(fat.array()); disk.write(fat.array())
                val config = "[all]\n# fixture\n".toByteArray()
                val cmdline = "console=serial0,115200 root=PARTUUID=12345678-02 rootwait\n".toByteArray()
                val root = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
                listOf(Triple("CONFIG  TXT", 3, config), Triple("CMDLINE TXT", 4, cmdline)).forEachIndexed { index, (name, cluster, contents) ->
                    val offset = index * 32
                    root.position(offset); root.put(name.toByteArray()); root.put(offset + 11, 0x20); root.putShort(offset + 26, cluster.toShort()); root.putInt(offset + 28, contents.size)
                    disk.seek(START + DATA_SECTOR * 512 + (cluster - 2) * 512); disk.write(contents)
                }
                disk.seek(START + DATA_SECTOR * 512); disk.write(root.array())
                disk.seek(START + SIZE); disk.write(ByteArray(4096) { 0x5a })
            }
            test(file)
        } finally { file.delete() }
    }

    companion object {
        private const val START = 1024L * 1024
        private const val SECTORS = 131072
        private const val SIZE = SECTORS * 512L
        private const val FAT_SECTORS = 1024
        private const val DATA_SECTOR = 32 + 2 * FAT_SECTORS
    }
}
