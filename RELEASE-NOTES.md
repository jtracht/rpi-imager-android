Native Android adaptation of Raspberry Pi Imager. Requires Android 8+ and a USB-OTG mass-storage reader (SCSI/Bulk-Only). Independent project, not an official Raspberry Pi product.

New in v0.4.0: native Raspberry Pi model selection and OS search. Models and compatibility tags come from the official catalog. Inclusive/exclusive board matching filters nested categories; search matches names and descriptions while preserving navigation. Changing the model clears the previous image choice and first-boot settings. Local-image compatibility still needs to be checked by the user. Model selection and search survive rotation within the running app session.

- Adaptive phone/tablet interface; German and English; light/dark theme.
- Official OS catalog, local images, XZ/GZip/ZIP decompression.
- Catalog SHA-256 validation before writing; complete USB read-back verification.
- Foreground writing with notification, cancellation and USB permission handling.
- Native first-boot settings: user/password, hostname, Wi-Fi/country/hidden networks, SSH password/public keys, timezone, keyboard, I²C/SPI/1-Wire and optional passwordless sudo.
- Rootless customization of FAT32 boot images using cloud-init, legacy Raspberry Pi OS systemd first-run or rpi-preseed. The source checksum is validated before customization; the customized image is hashed and completely verified after writing.
- Multiple USB logical units (LUNs), explicit target selection and removal of cached images/settings.
- Raspberry Pi Connect first-boot provisioning with personal/organisation auth keys from the browser account. Use a new key and prepare a new image for every Pi; personal keys expire after six hours.

Install `pi-imager-android.apk`. Check `SHA256SUMS` and `upstream.properties` for checksums and the precise upstream source. The upstream source archive is attached for traceability; this is a native Android implementation, not the Qt desktop binary.

Customization requires a supported FAT32 boot partition with `config.txt` and `cmdline.txt`; local-image formats must be selected explicitly. Account passwords are hashed, while Wi-Fi credentials and Connect keys may remain in cleartext on the boot partition and in the prepared private image until cleared. First-boot scripts and configuration files have been checked; actual first boot on a Raspberry Pi still needs hardware validation.

Embedded Connect device-code login, organisation device-identity registration, secure boot, USB gadget/fastboot provisioning, internal SD slots and UAS-only readers are not implemented. Physical USB readers, multiple LUNs and TalkBack still need device validation. Eject mounted volumes in Android before opening the device. Interrupted media must be written again.
