# Connect-Vorkonfiguration: Prüfung am 18. Oktober 2026

Release: `v0.3.0`, App-Quellstand `8fb4bbf`. Upstream bleibt `v2.0.12`, Commit `84b03d96cd4c390ebe475a4b5db5735e5535e9a6`.

## Umfang

Persönliche und Organisations-Auth-Schlüssel aus dem offiziellen Connect-Browserkonto werden für den ersten Pi-Start vorbereitet. Die App prüft Format und Katalogfähigkeit; sie kontaktiert keine Connect-API zur Prüfung oder Einlösung des Schlüssels. Kein eingebetteter Device-Code-Login und keine Organisations-Geräteregistrierung. Die Auth-Schlüssel- und Service-Verträge wurden gegen den festgehaltenen Upstream (`imagewriter.cpp`, `customization_generator.cpp`) und die [offizielle Connect-Dokumentation](https://www.raspberrypi.com/documentation/services/connect.html) geprüft.

## Automatische Prüfungen

- 31 reguläre JVM-Tests bestanden, darunter vier neue Connect-Tests und zwei Katalogfähigkeits-Tests. Sie prüfen persönliche/Organisations-Schlüssel, falsche Präfixe, Länge, Base58, Zeilenumbrüche, deaktivierte Einstellungen, Kontenanlage vor Schlüsselablage, private Dateimodi, Service-Verknüpfungen, Linger und Preseed-Vertrag.
- Zwei zusätzliche Integrationstests mit verifizierten offiziellen Trixie-/Bookworm-Lite-Images (2026-10-06) bestanden: Connect-Payload auf temporären Kopien eingebaut, Bootdateien wieder gelesen, gesamte Linux-Rootpartition per SHA-256 unverändert. Insgesamt lokal 33 Tests ohne Fehler oder übersprungene Tests. Ohne bereitgestellte offizielle Images überspringt CI diese beiden optionalen Tests.
- Die modifizierten echten Images zusätzlich mit `pyfatfs` unabhängig gelesen: Connect-Schlüssel vorhanden, Cloud-init mit PyYAML geparst, Legacy-Skript per POSIX-Shell auf Syntax geprüft. Es wurden ausschließlich synthetische, nicht gültige Testschlüssel verwendet.
- Unabhängige CI-Prüfung parst beide Cloud-init-Varianten samt eingebettetem Connect-Skript und Preseed-TOML. Sowohl extrahierte Connect-Skripte als auch gesamter Legacy-First-Run bestehen `sh -n`; die Skripte werden nicht ausgeführt.
- Vier bestehende Python-Sync-Tests bestanden. Debug-Build/Lint und signierter, mit R8 optimierter Release-Build/Lint bestanden.
- [GitHub-CI](https://github.com/jtracht/rpi-imager-android/actions/runs/39162063066) und [Tag-Release-Pipeline](https://github.com/jtracht/rpi-imager-android/actions/runs/39162065738) erfolgreich. [Release v0.3.0](https://github.com/jtracht/rpi-imager-android/releases/tag/v0.3.0) veröffentlicht, APK und alle weiteren Anhänge erneut heruntergeladen und gegen `SHA256SUMS` geprüft. APK-Signatur identisch mit den Vorgängerversionen; veröffentlichte APK als Update installiert und kalt gestartet, `versionName=v0.3.0`, kein Crash-Log.

## Android-Emulator

Signierte Release-Vorschau auf Android 15 / API 35 als Update installiert und gestartet. Offizieller Katalog erkannt, Connect für das kompatible Desktop-Image angeboten. Browserknopf öffnet die echte Connect-Anmeldeseite; es wurde kein Konto angemeldet. Browserrückkehr und Layoutwechsel behalten den offenen Entwurf, einschließlich des synthetischen Schlüssels, im Sitzungsspeicher.

Lokales FAT32-Testimage über Androids Dokumentenauswahl importiert, `cloudinit-rpi` explizit ausgewählt, Benutzerpasswort und synthetischen Connect-Schlüssel konfiguriert. Vorbereitung direkt in der App unter normaler Android-Benutzerkennung erfolgreich; Hinweis auf einmalige Schlüsselverwendung am vorbereiteten Image sichtbar. Kein Crash im Android-Crash-Log.

Die von Android erzeugte Image-Datei anschließend separat aus dem Emulator gelesen und unabhängig mit `pyfatfs`/PyYAML geprüft: richtiger Benutzer, unveränderter synthetischer Connect-Schlüssel, Schlüsselpfad und Modus-0600-Anweisung vorhanden. Root wurde ausschließlich für diese anschließende Inspektion des privaten App-Speichers im Testemulator verwendet, nicht für die Image-Vorbereitung.

Screenshots mit maskiertem synthetischem Schlüssel: [320 dp](screenshots/settings-connect-320.png), [840 dp](screenshots/settings-connect-840.png). Kleine Ansichten sind scrollbar; die Übernehmen-/Abbrechen-Knöpfe bleiben erreichbar. Bei automatisierter Eingabe muss die Android-Tastatur fertig geöffnet sein, bevor Text injiziert wird.

## Grenzen

Kein echter erster Start oder Einlösen eines gültigen Schlüssels auf einem Raspberry Pi; Service-Start, Kontoverknüpfung und Ablaufverhalten brauchen weiterhin Hardware-/Kontotests. Physische USB-/Mehrfach-LUN-Geräte und TalkBack bleiben ebenfalls ungeprüft. Eine konfigurierte Lite-Image-Kopie bestätigt das Dateiformat, nicht das Vorhandensein oder die Funktionsfähigkeit der Connect-Pakete im Ausgangsimage. Lokale Images müssen diese Pakete bereits enthalten; Katalog-Images werden ausschließlich anhand ihrer ausdrücklichen `rpi_connect`-Fähigkeit freigeschaltet.
