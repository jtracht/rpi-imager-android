package de.jtracht.piimager.domain

/**
 * A first-boot setting the user has to correct. The domain stays free of Android resources: the screen maps
 * each problem to a translated text, while [english] remains the exception message for logs and tests.
 */
enum class SettingsProblem(override val english: String) : ProblemCode {
    FORMAT("Choose a supported first-boot format for this image"),
    HOSTNAME("Hostname must be a single DNS label (1–63 characters)"),
    USERNAME("Choose a regular Linux username, not a system account"),
    PASSWORD_INVALID("Invalid account password"),
    PASSWORD_OR_KEY("Set an account password or enable SSH with a public key"),
    PASSWORD_LENGTH("Account password must have at least 8 characters"),
    TIMEZONE("Unknown timezone; use a name such as Europe/Berlin"),
    KEYBOARD("Keyboard layout must be a Linux layout code such as de or us"),
    WIFI_COUNTRY("Choose a valid two-letter Wi-Fi country"),
    WIFI_NAME("Wi-Fi name must contain 1–32 UTF-8 bytes without control characters"),
    WIFI_PASSPHRASE("WPA passphrase must contain 8–63 ASCII characters or a 64-digit hexadecimal PSK"),
    SSH_PASSWORD_NEEDS_ACCOUNT_PASSWORD("SSH password login requires an account password"),
    SSH_KEY_REQUIRED("Add at least one SSH public key"),
    SUDO_NEEDS_LOGIN("Passwordless sudo needs an account with a password or an SSH key"),
    SSH_KEY_NOT_PUBLIC("Paste an OpenSSH public key, never a private key"),
    SSH_KEY_ENCODING("Invalid SSH public key encoding"),
    SSH_KEY_LENGTH("Invalid SSH public key length"),
    SSH_KEY_TYPE_MISMATCH("SSH public key type does not match its contents"),
    SSH_KEY_MULTILINE("SSH keys must be single-line public keys"),
    CONNECT_KEY("Paste a personal or organisation Connect auth key (rpuak_ / rpoak_ followed by 24 Base58 characters)"),
    CONNECT_UNSUPPORTED("This catalog image does not advertise Raspberry Pi Connect support")
}

/** Still an [IllegalArgumentException], so existing callers keep working; [problem] selects the translated text. */
class InvalidSettingsException(val problem: SettingsProblem) : IllegalArgumentException(problem.english), ReportedProblem {
    override val code: ProblemCode get() = problem
    override val argument: String? get() = null
}

/** Like [require], but reports a [SettingsProblem] the screen can translate. */
fun requireSetting(condition: Boolean, problem: SettingsProblem) {
    if (!condition) throw InvalidSettingsException(problem)
}
