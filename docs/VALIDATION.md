# Prüfung vom 18. Oktober 2026

Geprüfter App-Quellstand: `3198ad2`. Upstream: `v2.0.12`, Commit `84b03d96cd4c390ebe475a4b5db5735e5535e9a6`.

- GitHub-CI: vier Python-Tests zur Release-Auswahl, sechs JVM-Tests für den Schreibkern, Android Lint und Debug-APK erfolgreich. Dieselben Tests sowie Release-Lint und signierter Build im automatischen Upstream-Release erfolgreich.
- Upstream-Sync manuell ausgelöst: Android-Tag erstellt, wiederverwendbaren Release-Workflow aufgerufen, Release mit APK und Quellnachweisen veröffentlicht. Zweiter Lauf: erfolgreich, kein weiterer Build oder Release.
- Eigenen Tag `v0.1.0` gepusht: separater Tag-Workflow erfolgreich, GitHub-Release mit signierter APK veröffentlicht.
- Release-Anhänge separat heruntergeladen: alle drei SHA-256-Dateiprüfsummen stimmen, APK-Signatur mit `apksigner` gültig (RSA 4096).
- `v0.1.0` erneut heruntergeladen, Prüfsummen und identische Signatur bestätigt, erfolgreich als Update über die erste APK installiert und gestartet.
- Signierte Release-APK auf Android 15 / API 35, x86_64, installiert und gestartet; keine Einträge im Crash-Log.
- Offizieller OS-Katalog über die echte HTTPS-Verbindung geöffnet und Raspberry Pi OS gewählt.
- Eine lokale 1-MiB-Testdatei über Androids Dokumentenauswahl importiert und erfolgreich als Image vorbereitet. Schreiben ohne Zielgerät bleibt deaktiviert.
- Deutsches Layout auf Handy und Tablet geprüft. `scripts/emulator_smoke.py` wiederholt Start, Erreichbarkeit des Schreibbereichs und gesperrten Schreibbutton ohne Ziel bei 320, 360, 411, 600 und 840 dp. Screenshots liegen in `docs/screenshots/`.

Nicht bestätigt: Schreiben auf physische USB-Medien, Gerätespezifika, tatsächlicher Stromausfall, 16-KB-Geräte und TalkBack. Vor produktivem Einsatz mit entbehrlicher SD-Karte und repräsentativen Kartenlesern testen. Die App ist eine erste native Android-Version mit den in der README genannten Funktionsgrenzen.
