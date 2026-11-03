package de.jtracht.piimager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class OperationProblemTest {
    @Test fun helpersKeepTheExceptionTypesCallersExpect() {
        val argument = assertThrows(IllegalArgumentException::class.java) { requireOperation(false, OperationProblem.EMPTY_IMAGE) }
        val state = assertThrows(IllegalStateException::class.java) { checkOperation(false, OperationProblem.STORAGE_FULL) }
        val failure = assertThrows(IllegalStateException::class.java) { operationFailure(OperationProblem.REDIRECT) }
        val io: IOException = UsbIoException(OperationProblem.USB_TRANSFER_FAILED)
        listOf(argument, state, failure, io).forEach { assertTrue(it is ReportedProblem) }
        assertEquals(OperationProblem.STORAGE_FULL, (state as ReportedProblem).code)
    }

    @Test fun argumentsReachTheMessageAndTheTranslation() {
        val error = assertThrows(OperationStateException::class.java) { checkOperation(false, OperationProblem.HTTP_STATUS) { "503" } }
        assertEquals("503", error.argument)
        assertEquals("The server answered with HTTP 503", error.message)
    }

    @Test fun flashingTooLargeAnImageReportsItsProblem() {
        val file = File.createTempFile("image", ".img").apply { writeBytes(ByteArray(4096)); deleteOnExit() }
        val small = object : BlockTarget {
            override val blockSize = 512
            override val blocks = 4L
            override fun write(lba: Long, data: java.nio.ByteBuffer) = Unit
            override fun read(lba: Long, data: java.nio.ByteBuffer) = Unit
            override fun close() = Unit
        }
        val error = assertThrows(OperationArgumentException::class.java) { Imaging.flash(file, small) { _, _ -> } }
        assertEquals(OperationProblem.IMAGE_TOO_LARGE_FOR_DEVICE, error.code)
    }

    @Test fun everyProblemHasItsOwnEnglishMessage() {
        val messages = OperationProblem.entries.map { it.english } + SettingsProblem.entries.map { it.english }
        assertEquals(messages.size, messages.toSet().size)
    }
}
