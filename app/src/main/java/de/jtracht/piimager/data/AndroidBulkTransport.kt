package de.jtracht.piimager.data

import de.jtracht.piimager.domain.*

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
    private val connection = manager.openDevice(device) ?: throw UsbIoException(OperationProblem.USB_OPEN_FAILED)
    private var closed = false

    init {
        if (!connection.claimInterface(usbInterface, true)) {
            connection.close()
            throw UsbIoException(OperationProblem.USB_CLAIM_FAILED)
        }
    }

    override fun bulkOutTransfer(src: ByteBuffer): Int = transfer(outEndpoint, src)
    override fun bulkInTransfer(dest: ByteBuffer): Int = transfer(inEndpoint, dest)

    private fun transfer(endpoint: UsbEndpoint, buffer: ByteBuffer): Int {
        checkOperation(!closed, OperationProblem.USB_TRANSFER_FAILED)
        // Android 8 limits each bulkTransfer to 16 KB; the SCSI layer loops.
        val count = minOf(buffer.remaining(), 16384)
        val result = connection.bulkTransfer(endpoint, buffer.array(), buffer.arrayOffset() + buffer.position(), count, UsbCommunication.TRANSFER_TIMEOUT)
        if (result <= 0) throw UsbIoException(OperationProblem.USB_TRANSFER_FAILED)
        buffer.position(buffer.position() + result)
        return result
    }

    override fun controlTransfer(requestType: Int, request: Int, value: Int, index: Int, buffer: ByteArray, length: Int): Int {
        checkOperation(!closed, OperationProblem.USB_TRANSFER_FAILED)
        return connection.controlTransfer(requestType, request, value, index, buffer, length, UsbCommunication.TRANSFER_TIMEOUT)
    }

    override fun clearFeatureHalt(endpoint: UsbEndpoint) {
        // Standard USB CLEAR_FEATURE(ENDPOINT_HALT), directed to the endpoint.
        val result = controlTransfer(0x02, 0x01, 0, endpoint.address, byteArrayOf(), 0)
        if (result < 0) throw UsbIoException(OperationProblem.USB_TRANSFER_FAILED)
    }

    override fun resetDevice() {
        // USB Mass Storage Bulk-Only reset, scoped to this interface.
        val result = controlTransfer(0x21, 0xff, 0, usbInterface.id, byteArrayOf(), 0)
        if (result < 0) throw UsbIoException(OperationProblem.USB_TRANSFER_FAILED)
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
