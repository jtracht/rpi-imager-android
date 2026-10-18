Native Android adaptation of Raspberry Pi Imager. Requires Android 8+ and a USB-OTG mass-storage reader (SCSI/Bulk-Only, LUN 0). Independent project, not an official Raspberry Pi product.

- Adaptive phone/tablet interface; German and English; light/dark theme.
- Official OS catalog, local images, XZ/GZip/ZIP decompression.
- Catalog SHA-256 validation before writing; complete USB read-back verification.
- Foreground writing with notification, cancellation and USB permission handling.
- Native first-boot settings: user/password, hostname, Wi-Fi/country/hidden networks, SSH password/public keys, timezone, keyboard, I²C/SPI/1-Wire and optional passwordless sudo.
- Rootless customization of FAT32 boot images using cloud-init, legacy Raspberry Pi OS systemd first-run or rpi-preseed. The source checksum is validated before customization; the customized image is hashed and completely verified after writing.
- Multiple USB logical units (LUNs), explicit target selection and removal of cached images/settings.

Install `pi-imager-android.apk`. Check `SHA256SUMS` and `upstream.properties` for checksums and the precise upstream source. The upstream source archive is attached for traceability; this is a native Android implementation, not the Qt desktop binary.

Customization requires a supported FAT32 boot partition with `config.txt` and `cmdline.txt`; local-image formats must be selected explicitly. Account passwords are hashed, while Wi-Fi credentials may remain in cleartext on the boot partition and in the prepared private image until cleared. First-boot scripts and configuration files have been checked; actual first boot on a Raspberry Pi still needs hardware validation.

Raspberry Pi Connect sign-in, secure boot, USB gadget/fastboot provisioning, internal SD slots and UAS-only readers are not implemented. Physical USB readers, multiple LUNs and TalkBack still need device validation. Eject mounted volumes in Android before opening the device. Interrupted media must be written again.
