# Modellauswahl und Katalogsuche: Prüfung am 18. Oktober 2026

Release: `v0.4.0`, App-Quellstand `495bca5`. Upstream unverändert: `v2.0.12`, Commit `84b03d96cd4c390ebe475a4b5db5735e5535e9a6`.

## Umfang und Vertrag

Native Modellauswahl aus `imager.devices`, Filterung nach Hardware-Tags und `matching_type`, Suche nach Namen/Beschreibungen sowie erneute Modellprüfung vor der Image-Vorbereitung. Die Filterregeln entsprechen `filterOsListWithHWTags` im festgehaltenen `upstream/src/imagewriter.cpp`: beliebige Tag-Überschneidung genügt, fehlende Tags hängen vom inklusiven/exklusiven Modus ab, ausdrücklich leere Tags passen zu keinem gewählten Modell. Inline-Kategorien werden anhand ihrer Kinder gefiltert. Die Android-App lädt entfernte Kategorien erst beim Öffnen; bis dahin bleiben unmarkierte Remote-Kategorien erreichbar. Lokale Images und die Option „Alle Modelle“ erhalten keine Modellprüfung.

Suche durchsucht die aktuelle Liste samt enthaltenen Unterkategorien; Treffer behalten die Navigation. Bei Eintritt in eine Kategorie wird wieder deren vollständiger modellkompatibler Inhalt angezeigt. Unbesuchte Remote-Kataloge werden nicht automatisch durchsucht. Die Funktion prüft Angaben des Herausgebers, keine tatsächliche Bootfähigkeit.

## Automatische Prüfungen

- 40 reguläre JVM-Tests bestanden, darunter neun neue Filter-/Suchtests: beliebige Tag-Überschneidung, inclusive/exclusive, fehlende gegenüber leeren Geräteangaben, ungefilterte Ansicht, rekursive Kategorien, Unveränderlichkeit der Quelldaten, Remote-Kategorien, Suche über mehrere Wörter, Groß-/Kleinschreibung, Navigation und Zusammenspiel von Filter/Suche.
- Die beiden optionalen Tests auf vollständigen offiziellen Image-Dateien wurden in diesem Lauf mangels gesetzter Testvariablen übersprungen. Sie waren in Version 0.3.0 erfolgreich; Boot-Dateisystem und Konfigurationsgenerator wurden hier nicht verändert.
- Vier Python-Sync-Tests, unabhängige YAML-/TOML-/Shell-Syntaxprüfung, Debug-Build mit Lint und signierter R8-Release-Build mit Lint erfolgreich.

## Android-Emulator

Signierte Release-Vorschau als Update auf Android 15 / API 35 installiert, kein Crash im Crash-Log.

- Aktuelle Modelle einschließlich Pi 5, 4, 3 und 2 aus dem echten offiziellen Katalog geladen.
- Pi 5 gewählt, Suche ohne Treffer geprüft und geleert. „Lite“ findet eine passende verschachtelte Kategorie; Modell und Suchtext bleiben beim Wechsel von Handy- auf Tablet-Geometrie erhalten.
- Die gefundene Kategorie geöffnet: Suchtext geleert und vollständigen kompatiblen Inhalt einschließlich „Full“-Images angezeigt. Dies bestätigt, dass Suchergebnisse die Originalkategorie nicht dauerhaft beschneiden.
- Ein 64-Bit-Image gewählt und Sitzungseinstellungen mit synthetischem Benutzerpasswort gespeichert. Wechsel auf Pi 2 setzt Image-Auswahl und Konfiguration zurück. Im Pi-2-Katalog fehlt anschließend das 64-Bit-Hauptimage, während das 32-Bit-Hauptimage angeboten wird.
- Suche nach „Ubuntu“ bei 320 dp mit geöffneter Android-Tastatur: passende Kategorie und obere Navigation erreichbar.
- Bestehender Emulator-Smoke-Test bei 320, 360, 411, 600 und 840 dp bestanden: App-Start, erreichbare Schreibsteuerung und deaktiviertes Schreiben ohne Image/Zieldatenträger.

Screenshots: [Modellauswahl 360 dp](screenshots/model-picker-360.png), [Pi-2-Filter 320 dp](screenshots/catalog-filter-320.png), [Suche mit Tastatur 320 dp](screenshots/catalog-keyboard-320.png), [Suche 840 dp](screenshots/catalog-search-840.png). Die allgemeinen Breiten-Screenshots wurden ebenfalls aktualisiert. Es wurde kein USB-Datenträger beschrieben.

## Veröffentlichungsprüfung am 19. Oktober 2026

[GitHub-CI](https://github.com/jtracht/rpi-imager-android/actions/runs/39163455408) und [Tag-Release-Pipeline](https://github.com/jtracht/rpi-imager-android/actions/runs/39163457507) erfolgreich. [Release v0.4.0](https://github.com/jtracht/rpi-imager-android/releases/tag/v0.4.0) mit signierter APK, Upstream-Archiv, Metadaten und Prüfsummen veröffentlicht. Alle Anhänge erneut heruntergeladen und gegen `SHA256SUMS` geprüft. APK-Signatur identisch mit den Vorgängerversionen; veröffentlichte APK als Update installiert und kalt gestartet, `versionName=v0.4.0`, kein Eintrag im Crash-Log.

## Grenzen der Prüfung

Keine neuen Hardware- oder Pi-Boot-Tests. Physische USB-/Mehrfach-LUN-Kompatibilität, TalkBack und tatsächliche Connect-Anmeldung bleiben separat zu prüfen. Secure Boot, USB-Gadget/Fastboot und Organisations-Geräteregistrierung sind weiterhin nicht portiert. Downloads sind weiterhin nur innerhalb des laufenden App-Prozesses möglich und nicht fortsetzbar.
