# Android-Vorkonfiguration: Prüfung am 18. Oktober 2026

App-Quellstand und Release-Tag: `0c93047`, `v0.2.0`. Upstream unverändert: `v2.0.12`, `84b03d96cd4c390ebe475a4b5db5735e5535e9a6`.

## Automatische Prüfungen

- 25 JVM-Tests erfolgreich: acht Boot-Dateisystemtests, elf Konfigurations-/Validierungstests und sechs bestehende Schreibkern-Tests.
- Vier Python-Tests der Release-Auswahl erfolgreich.
- Unabhängige YAML- und TOML-Parser prüfen die generierten Cloud-init-/Preseed-Dateien, einschließlich Anführungszeichen, Backslashes und Sonderzeichen in Zugangsdaten. Der Systemd-First-Run wird mit `sh -n` syntaktisch geprüft und nicht ausgeführt. Diese Prüfung läuft in CI und vor jedem APK-Release.
- Android Lint für Debug und Release, Debug-Build und signierter, mit R8 optimierter Release-Build erfolgreich. GitHub-CI für den App-Quellstand erfolgreich.
- Tag-Pipeline für `v0.2.0` erfolgreich: APK, Upstream-Archiv, Metadaten und Prüfsummen am veröffentlichten GitHub-Release. Die veröffentlichte APK wurde erneut heruntergeladen, alle Dateiprüfsummen und dieselbe Signatur wie bei `v0.1.0` bestätigt; Installation als Update und Start im Emulator erfolgreich.
- Dateisystemtests prüfen unter anderem MBR und GPT, GPT-Prüfsummen, obere/untere Partitionsgrenzen, zyklische FAT-Ketten, FAT-Spiegel, lange Dateinamen, Vergrößerung des Root-Verzeichnisses, Abbruch und Wechsel zwischen Einrichtungsverfahren.

## Offizielle Images

Zwei zusätzliche Integrationstests wurden lokal mit heruntergeladenen, anhand des offiziellen Katalogs per SHA-256 geprüften Images ausgeführt:

| Image | Einrichtungsformat | Ergebnis |
| --- | --- | --- |
| Raspberry Pi OS Lite (64-bit), Trixie, 2026-10-06 | `cloudinit-rpi` | Bootdateien erstellt und erneut gelesen; Linux-Rootpartition byteweise über SHA-256 unverändert |
| Raspberry Pi OS (Legacy, 64-bit) Lite, Bookworm, 2026-10-06 | `systemd` | First-Run und Bootoptionen erstellt und erneut gelesen; Linux-Rootpartition byteweise über SHA-256 unverändert |

Die geänderten Bootpartitionen wurden zusätzlich mit dem unabhängigen Python-Paket `pyfatfs` gelesen. Cloud-init-Dateien wurden mit PyYAML geparst, das echte generierte Legacy-Skript mit einer POSIX-Shell syntaktisch geprüft. Die Tests booten keinen Raspberry Pi.

Die optionalen Tests stehen in `RealImageIntegrationTest`. Zum Wiederholen `PI_IMAGER_CLOUD_IMAGE` und `PI_IMAGER_SYSTEMD_IMAGE` auf die entpackten, geprüften Images setzen und `testDebugUnitTest --tests de.jtracht.piimager.data.RealImageIntegrationTest --rerun` ausführen. Sie arbeiten auf temporären Kopien. In normaler CI ohne diese Images werden genau diese zwei Tests übersprungen.

## Android-Emulator

- Signierte Release-Vorschau auf Android 15 / API 35 als Update installiert und gestartet; App-Code entspricht dem Release-Quellstand. Kein Crash im Crash-Log.
- Katalog geöffnet und korrektes `cloudinit-rpi` automatisch aus dem gewählten OS übernommen.
- Ungespeichertes Benutzerpasswort blieb beim Wechsel zwischen Hoch- und Querformat im offenen Dialog erhalten. Es wird nicht in Saved State oder Einstellungen abgelegt.
- Lokales FAT32-Testimage über Androids Dokumentenauswahl geöffnet; Format ausdrücklich ausgewählt; Benutzerpasswort, verstecktes WLAN, SSH-Passwortauthentifizierung sowie I²C, SPI und 1-Wire konfiguriert; Image erfolgreich in der Android-App vorbereitet.
- Die von der App erzeugte Image-Datei wurde anschließend im Testemulator separat untersucht: Dateisystem mit `pyfatfs`, Konfiguration mit PyYAML, Passwort-Hash gegen OpenSSL-SHA-512-crypt. WLAN-Daten und alle gewählten Hardware-Optionen waren korrekt enthalten. Die App selbst bereitete das Image unter ihrer normalen Android-Benutzerkennung vor; Root wurde ausschließlich danach zur Testinspektion des privaten App-Speichers im Emulator verwendet.
- „Image und Einstellungen löschen“ entfernt die vorbereitete Datei aus dem privaten App-Speicher.
- Layout und fehlender-Zieldatenträger-Sperre bei 320, 360, 411, 600 und 840 dp geprüft; System- und WLAN-Dialog zusätzlich visuell geprüft. Screenshots unter `docs/screenshots/` enthalten nur synthetische Testwerte.

## Noch nicht durch Hardware bestätigt

Echter erster Start auf einem Raspberry Pi, WLAN-Verbindungsaufbau und SSH-Anmeldung auf dem Pi, physische USB-Kartenleser einschließlich mehrerer LUNs, 16-KB-Android-Geräte und TalkBack. Die oben genannten Datei-, Syntax- und Emulatorprüfungen ersetzen diese Tests nicht. Grenzen und noch nicht portierte Desktop-Funktionen stehen in der README.
