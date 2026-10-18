Native Android adaptation of Raspberry Pi Imager. Requires Android 8+ and a USB-OTG mass-storage reader (SCSI/Bulk-Only, LUN 0). Independent project, not an official Raspberry Pi product.

- Adaptive phone/tablet interface; German and English; light/dark theme.
- Official OS catalog, local images, XZ/GZip/ZIP decompression.
- Catalog SHA-256 validation before writing; complete USB read-back verification.
- Foreground writing with notification, cancellation and USB permission handling.

Install `pi-imager-android.apk`. Check `SHA256SUMS` and `upstream.properties` for checksums and the precise upstream source. The upstream source archive is attached for traceability; this is a native Android implementation, not the Qt desktop binary.

Wi-Fi/SSH customization, Raspberry Pi Connect, internal SD slots, UAS-only readers and multiple LUN selection are not implemented. Writing still needs physical-device validation; automated tests cannot establish compatibility with your reader. Eject mounted volumes in Android before opening the device. Interrupted media must be written again.
