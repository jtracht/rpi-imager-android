package de.jtracht.piimager.domain

import java.io.IOException

/** A problem the user is told about. [english] is the log and test message; "%s" takes the argument. */
interface ProblemCode { val english: String }

/** An exception the screen and the notifications translate through its [code]. */
interface ReportedProblem {
    val code: ProblemCode
    val argument: String?
}

/** Problems while loading, customizing, selecting USB storage and writing. */
enum class OperationProblem(override val english: String) : ProblemCode {
    CHOOSE_IMAGE("Choose an image"),
    MODEL_MISMATCH("Image does not match the selected model"),
    NO_SUPPORTED_IMAGE("This catalog entry contains no supported image"),
    CATALOG_INVALID("The OS catalog could not be read"),
    HTTPS_ONLY("Only HTTPS URLs are supported"),
    HTTP_STATUS("The server answered with HTTP %s"),
    REDIRECT("The download was redirected too often or without a target"),
    DECLARED_SIZE("The image does not have the size given in the catalog"),
    NO_CHECKSUM("Catalog image has no valid SHA-256 checksum"),
    CHECKSUM_MISMATCH("SHA-256 mismatch"),
    STORAGE_FULL("Not enough free app storage"),
    CANNOT_OPEN_IMAGE("Cannot open image"),
    EMPTY_IMAGE("Empty image"),
    ZIP_NO_IMAGE("ZIP contains no IMG or ISO file"),
    SOURCE_STALLED("Image source made no progress"),
    STORAGE_FAILED("The image could not be stored or removed in app storage"),
    PREPARED_IMAGE_CHANGED("Prepared image changed"),
    PREPARATION_STATE("The preparation is in an unexpected state"),
    ANOTHER_OPERATION("Another image operation is running"),
    BOOT_NOT_SUPPORTED("Customization requires a Raspberry Pi image with a FAT32 boot partition"),
    BOOT_FILE_MISSING("Missing boot file: %s"),
    BOOT_DAMAGED("The boot partition of this image cannot be edited safely"),
    BOOT_VERIFY_FAILED("Boot customization verification failed"),
    USB_DISCONNECTED("USB device disconnected"),
    USB_CHANGED("USB device changed"),
    USB_PERMISSION("USB permission required"),
    USB_UNSUPPORTED("Unsupported USB storage device"),
    USB_OPEN_FAILED("Cannot open USB device"),
    USB_CLAIM_FAILED("Cannot claim USB interface; eject mounted volumes first"),
    USB_TRANSFER_FAILED("USB transfer failed or timed out; reconnect the device and retry"),
    USB_NO_MEDIA("No media in USB reader"),
    USB_CAPACITY_CHANGED("USB capacity changed; select the device again"),
    IMAGE_TOO_LARGE_FOR_DEVICE("Image is larger than the USB device"),
    IMAGE_ENDED("Image ended unexpectedly"),
    VERIFY_FAILED("Verification failed at byte %s")
}

class OperationArgumentException(override val code: OperationProblem, override val argument: String? = null) :
    IllegalArgumentException(code.english.format(argument ?: "")), ReportedProblem

class OperationStateException(override val code: OperationProblem, override val argument: String? = null) :
    IllegalStateException(code.english.format(argument ?: "")), ReportedProblem

/** Keeps transport failures [IOException]s, as callers treat them as I/O errors. */
class UsbIoException(override val code: OperationProblem) : IOException(code.english), ReportedProblem {
    override val argument: String? get() = null
}

/** Like [require], with a translatable problem. */
fun requireOperation(condition: Boolean, problem: OperationProblem, argument: () -> String? = { null }) {
    if (!condition) throw OperationArgumentException(problem, argument())
}

/** Like [check], with a translatable problem. */
fun checkOperation(condition: Boolean, problem: OperationProblem, argument: () -> String? = { null }) {
    if (!condition) throw OperationStateException(problem, argument())
}

/** Like [error], with a translatable problem. */
fun operationFailure(problem: OperationProblem, argument: String? = null): Nothing = throw OperationStateException(problem, argument)
