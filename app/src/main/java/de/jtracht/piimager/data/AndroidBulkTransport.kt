package de.jtracht.piimager.data

import android.hardware.usb.*
import me.jahnen.libaums.core.usb.UsbCommunication
import java.io.IOException
import java.nio.ByteBuffer

/** Android USB Host transport; avoids libaums' legacy, 4 KB-aligned JNI helpers. */
class AndroidBulkTransport(
    manager: UsbManager,
    device: UsbDevice,
    override val usbInterface: UsbInterface,
    override val outEndpoint: UsbEndpoint,
    override val inEndpoint: UsbEndpoint
) : UsbCommunication {
    private val connection = manager.openDevice(device) ?: throw IOException("Cannot open USB device")
    private var closed = false

    init {
        if (!connection.claimInterface(usbInterface, true)) {
            connection.close()
            throw IOException("Cannot claim USB interface; eject mounted volumes first")
        }
    }

    override fun bulkOutTransfer(src: ByteBuffer): Int = transfer(outEndpoint, src)
    override fun bulkInTransfer(dest: ByteBuffer): Int = transfer(inEndpoint, dest)

    private fun transfer(endpoint: UsbEndpoint, buffer: ByteBuffer): Int {
        check(!closed) { "USB connection closed" }
        // Android 8 limits each bulkTransfer to 16 KB; the SCSI layer loops.
        val count = minOf(buffer.remaining(), 16384)
        val result = connection.bulkTransfer(endpoint, buffer.array(), buffer.arrayOffset() + buffer.position(), count, UsbCommunication.TRANSFER_TIMEOUT)
        if (result <= 0) throw IOException("USB transfer failed or timed out; reconnect the device and retry")
        buffer.position(buffer.position() + result)
        return result
    }

    override fun controlTransfer(requestType: Int, request: Int, value: Int, index: Int, buffer: ByteArray, length: Int): Int {
        check(!closed) { "USB connection closed" }
        return connection.controlTransfer(requestType, request, value, index, buffer, length, UsbCommunication.TRANSFER_TIMEOUT)
    }

    override fun clearFeatureHalt(endpoint: UsbEndpoint) {
        // Standard USB CLEAR_FEATURE(ENDPOINT_HALT), directed to the endpoint.
        val result = controlTransfer(0x02, 0x01, 0, endpoint.address, byteArrayOf(), 0)
        if (result < 0) throw IOException("Cannot clear USB endpoint halt")
    }

    override fun resetDevice() {
        // USB Mass Storage Bulk-Only reset, scoped to this interface.
        val result = controlTransfer(0x21, 0xff, 0, usbInterface.id, byteArrayOf(), 0)
        if (result < 0) throw IOException("USB mass-storage reset failed")
        clearFeatureHalt(inEndpoint)
        clearFeatureHalt(outEndpoint)
    }

    override fun close() {
        if (!closed) {
            connection.releaseInterface(usbInterface)
            connection.close()
            closed = true
        }
    }
}
