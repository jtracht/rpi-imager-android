package de.jtracht.piimager.data

import android.content.Context
import android.hardware.usb.*
import de.jtracht.piimager.domain.BlockTarget
import me.jahnen.libaums.core.driver.BlockDeviceDriverFactory
import java.nio.ByteBuffer

data class Storage(val id: String, val name: String, val vendor: Int, val product: Int, val capacity: Long = 0, val lun: Int = 0)

class UsbStorage(context: Context) {
    val manager = context.getSystemService(UsbManager::class.java)

    private fun massInterface(device: UsbDevice): UsbInterface? =
        (0 until device.interfaceCount).map(device::getInterface).firstOrNull {
            it.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE &&
                it.interfaceSubclass == 6 && it.interfaceProtocol == 80
        }

    fun devices(): List<Storage> = manager.deviceList.values.filter { massInterface(it) != null }.map {
        Storage(it.deviceName, it.productName ?: "USB ${it.vendorId}:${it.productId}", it.vendorId, it.productId)
    }

    fun device(storage: Storage): UsbDevice {
        val device = manager.deviceList[storage.id] ?: error("USB device disconnected")
        check(device.vendorId == storage.vendor && device.productId == storage.product) { "USB device changed" }
        return device
    }

    private fun transport(storage: Storage): AndroidBulkTransport {
        val device = device(storage)
        check(manager.hasPermission(device)) { "USB permission required" }
        val iface = massInterface(device) ?: error("Unsupported USB interface")
        val endpoints = (0 until iface.endpointCount).map(iface::getEndpoint)
            .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
        val input = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN } ?: error("Missing USB input")
        val output = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT } ?: error("Missing USB output")
        return AndroidBulkTransport(manager, device, iface, output, input)
    }

    fun logicalUnits(storage: Storage): List<Storage> {
        transport(storage).use { communication ->
            val max = ByteArray(1)
            val response = communication.controlTransfer(0xa1, 0xfe, 0, communication.usbInterface.id, max, 1)
            // Per Bulk-Only Transport, a stalled GET_MAX_LUN means LUN 0 only.
            val last = if (response == 1) max[0].toInt() and 255 else 0
            require(last in 0..15) { "Invalid USB logical-unit count" }
            return (0..last).map { storage.copy(capacity = 0, lun = it) }
        }
    }

    fun open(storage: Storage): BlockTarget {
        require(storage.lun in 0..15) { "Invalid USB logical unit" }
        val communication = transport(storage)
        try {
            // Do not parse existing partitions: blank and ext4 media must work too.
            val driver = BlockDeviceDriverFactory.createBlockDevice(communication, storage.lun.toByte())
            driver.init()
            return object : BlockTarget {
                override val blockSize get() = driver.blockSize
                override val blocks get() = driver.blocks
                override fun read(lba: Long, data: ByteBuffer) = driver.read(lba, data)
                override fun write(lba: Long, data: ByteBuffer) = driver.write(lba, data)
                override fun close() = communication.close()
            }
        } catch (error: Throwable) {
            communication.close()
            throw error
        }
    }
}
