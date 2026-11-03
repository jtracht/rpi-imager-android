package de.jtracht.piimager.presentation

import android.content.Context
import androidx.annotation.StringRes
import de.jtracht.piimager.R
import de.jtracht.piimager.domain.OperationProblem
import de.jtracht.piimager.domain.ProblemCode
import de.jtracht.piimager.domain.ReportedProblem
import de.jtracht.piimager.domain.SettingsProblem
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * The text shown for an error in dialogs, the settings step and notifications. Known problems are translated;
 * network failures get one translated text; anything else is shown as unexpected with its original message.
 */
fun Context.describe(error: Throwable): String = when (error) {
    is ReportedProblem -> getString(error.code.text(), error.argument ?: "")
    is UnknownHostException, is ConnectException, is NoRouteToHostException, is SocketTimeoutException, is SSLException ->
        getString(R.string.problem_network)
    else -> getString(R.string.problem_unexpected, error.message ?: error.javaClass.simpleName)
}

/** Exhaustive, so a new problem cannot be added without a translated text. */
@StringRes fun ProblemCode.text(): Int = when (this) {
    is SettingsProblem -> text()
    is OperationProblem -> text()
    else -> R.string.problem_unexpected
}

@StringRes fun SettingsProblem.text(): Int = when (this) {
    SettingsProblem.FORMAT -> R.string.settings_problem_format
    SettingsProblem.HOSTNAME -> R.string.settings_problem_hostname
    SettingsProblem.USERNAME -> R.string.settings_problem_username
    SettingsProblem.PASSWORD_INVALID -> R.string.settings_problem_password_invalid
    SettingsProblem.PASSWORD_OR_KEY -> R.string.settings_problem_password_or_key
    SettingsProblem.PASSWORD_LENGTH -> R.string.settings_problem_password_length
    SettingsProblem.TIMEZONE -> R.string.settings_problem_timezone
    SettingsProblem.KEYBOARD -> R.string.settings_problem_keyboard
    SettingsProblem.WIFI_COUNTRY -> R.string.settings_problem_wifi_country
    SettingsProblem.WIFI_NAME -> R.string.settings_problem_wifi_name
    SettingsProblem.WIFI_PASSPHRASE -> R.string.settings_problem_wifi_passphrase
    SettingsProblem.SSH_PASSWORD_NEEDS_ACCOUNT_PASSWORD -> R.string.settings_problem_ssh_password_needs_account_password
    SettingsProblem.SSH_KEY_REQUIRED -> R.string.settings_problem_ssh_key_required
    SettingsProblem.SUDO_NEEDS_LOGIN -> R.string.settings_problem_sudo_needs_login
    SettingsProblem.SSH_KEY_NOT_PUBLIC -> R.string.settings_problem_ssh_key_not_public
    SettingsProblem.SSH_KEY_ENCODING -> R.string.settings_problem_ssh_key_encoding
    SettingsProblem.SSH_KEY_LENGTH -> R.string.settings_problem_ssh_key_length
    SettingsProblem.SSH_KEY_TYPE_MISMATCH -> R.string.settings_problem_ssh_key_type_mismatch
    SettingsProblem.SSH_KEY_MULTILINE -> R.string.settings_problem_ssh_key_multiline
    SettingsProblem.CONNECT_KEY -> R.string.settings_problem_connect_key
    SettingsProblem.CONNECT_UNSUPPORTED -> R.string.settings_problem_connect_unsupported
}

@StringRes fun OperationProblem.text(): Int = when (this) {
    OperationProblem.CHOOSE_IMAGE -> R.string.problem_choose_image
    OperationProblem.MODEL_MISMATCH -> R.string.problem_model_mismatch
    OperationProblem.NO_SUPPORTED_IMAGE -> R.string.problem_no_supported_image
    OperationProblem.CATALOG_INVALID -> R.string.problem_catalog_invalid
    OperationProblem.HTTPS_ONLY -> R.string.problem_https_only
    OperationProblem.HTTP_STATUS -> R.string.problem_http_status
    OperationProblem.REDIRECT -> R.string.problem_redirect
    OperationProblem.DECLARED_SIZE -> R.string.problem_declared_size
    OperationProblem.NO_CHECKSUM -> R.string.problem_no_checksum
    OperationProblem.CHECKSUM_MISMATCH -> R.string.problem_checksum_mismatch
    OperationProblem.STORAGE_FULL -> R.string.problem_storage_full
    OperationProblem.CANNOT_OPEN_IMAGE -> R.string.problem_cannot_open_image
    OperationProblem.EMPTY_IMAGE -> R.string.problem_empty_image
    OperationProblem.ZIP_NO_IMAGE -> R.string.problem_zip_no_image
    OperationProblem.SOURCE_STALLED -> R.string.problem_source_stalled
    OperationProblem.STORAGE_FAILED -> R.string.problem_storage_failed
    OperationProblem.PREPARED_IMAGE_CHANGED -> R.string.problem_prepared_image_changed
    OperationProblem.PREPARATION_STATE -> R.string.problem_preparation_state
    OperationProblem.ANOTHER_OPERATION -> R.string.problem_another_operation
    OperationProblem.BOOT_NOT_SUPPORTED -> R.string.problem_boot_not_supported
    OperationProblem.BOOT_FILE_MISSING -> R.string.problem_boot_file_missing
    OperationProblem.BOOT_DAMAGED -> R.string.problem_boot_damaged
    OperationProblem.BOOT_VERIFY_FAILED -> R.string.problem_boot_verify_failed
    OperationProblem.USB_DISCONNECTED -> R.string.problem_usb_disconnected
    OperationProblem.USB_CHANGED -> R.string.problem_usb_changed
    OperationProblem.USB_PERMISSION -> R.string.problem_usb_permission
    OperationProblem.USB_UNSUPPORTED -> R.string.problem_usb_unsupported
    OperationProblem.USB_OPEN_FAILED -> R.string.problem_usb_open_failed
    OperationProblem.USB_CLAIM_FAILED -> R.string.problem_usb_claim_failed
    OperationProblem.USB_TRANSFER_FAILED -> R.string.problem_usb_transfer_failed
    OperationProblem.USB_NO_MEDIA -> R.string.problem_usb_no_media
    OperationProblem.USB_CAPACITY_CHANGED -> R.string.problem_usb_capacity_changed
    OperationProblem.IMAGE_TOO_LARGE_FOR_DEVICE -> R.string.problem_image_too_large_for_device
    OperationProblem.IMAGE_ENDED -> R.string.problem_image_ended
    OperationProblem.VERIFY_FAILED -> R.string.problem_verify_failed
}
