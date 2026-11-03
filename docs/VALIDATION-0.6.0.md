# Schritt-für-Schritt-Oberfläche: Prüfung am 3. November 2026

Release: `v0.6.0`. Upstream bleibt `v2.0.12`, Commit `84b03d96cd4c390ebe475a4b5db5735e5535e9a6`.

## Umfang

Die Oberfläche führt in fünf Schritten durch den Vorgang: Pi-Modell → Betriebssystem → Einstellungen → USB-Speicher → Schreiben. Modell und Image werden direkt in ihren Schritten gewählt; die Auswahl führt automatisch weiter. Jedes gewählte Image – lokal oder aus dem Katalog – wird sofort im bestehenden `dataSync`-Vordergrunddienst geladen und geprüft. Die Einstellungen für den ersten Start werden währenddessen ausgefüllt und anschließend in das bereits geladene, unveränderte Image geschrieben (`ImageRepository.customize`). Dabei wird das Image unter seinem temporären Namen bearbeitet und vorher gegen seine SHA-256-Prüfsumme geprüft; ein unterbrochener Schritt hinterlässt kein scheinbar fertiges Image. Bereits geschriebene Einstellungen zu ändern lädt das Image neu.

Ab 840 dp zeigt eine Schrittliste links den Stand jedes Schritts; darunter steht ein Stepper oben. Inhalte sind auf 720 dp Zeilenbreite begrenzt.

## Automatische Prüfungen

- Debug-Build, Test-APK, Android Lint und alle JVM-Tests bestanden, darunter neue Tests für die Freigabe der Schritte und die Kurznamen der Modelle.
- `scripts/emulator_smoke.py` auf API 35 bei 320, 360, 411, 600 und 840 dp bestanden: Start ohne „Weiter“ im Modellschritt, automatischer Wechsel nach Modellwahl, Katalog im Schritt mit „Lokales Image auswählen“ darunter, gesperrter Folgeschritt ohne Image.
- Bestehender Emulator-Test `scripts/emulator_preparation.py` bestanden: Hintergrundvorbereitung, SHA-256, neue Activity übernimmt das Ergebnis, Abbruch und Aufräumen. Der Test deckte zunächst auf, dass eine neue Activity mit fertigem Image im Modellschritt begann; sie öffnet nun den Einstellungsschritt.

## Manuelle Prüfungen im Emulator

- Lokales 68-MB-Test-Image mit FAT32-Bootpartition über Androids Dokumentauswahl gewählt: Laden startete sofort, Einstellungsschritt öffnete sich automatisch. Vorkonfiguration mit cloud-init und synthetischem Passwort ausgefüllt; nach „Weiter“ wurde sie ins geladene Image geschrieben und der USB-Schritt öffnete sich automatisch (360 und 1280 dp). Im Ergebnis haben sich 240 Bytes geändert; die cloud-init-Konfiguration enthält Hostname, Zeitzone, Tastatur und Benutzer, das Passwort nur als `$6$`-Hash.
- Katalogpfad (360 und 840 dp): Unterkategorie öffnen, mit der Zurück-Taste verlassen, Raspberry Pi OS (64-bit) gewählt – Download startete automatisch; „Weiter“ während des Downloads zeigt den Wartehinweis; Abbrechen und „Image erneut laden“ funktionieren.
- Beim Prüfen behoben: ganze Schalterzeilen schalten um, festhängender Wartezustand nach Abbruch, Unterkategorien beginnen oben.
- Screenshots: [docs/screenshots/README.md](screenshots/README.md).

## Verbleibende Grenzen

Der Schreibschritt wurde im Emulator nicht bedient (kein USB-Speicher). Die nachträgliche Vorkonfiguration wurde nur mit dem 68-MB-Test-Image geprüft, nicht mit einem mehrere GB großen offiziellen Image. Katalog-Images werden beim Antippen sofort geladen, auch über mobile Daten. Abbruch während des Schreibens der Einstellungen verwirft das geladene Image. Übernimmt eine neue Activity ein Image aus einer laufenden Vorbereitung, kennt sie dessen Quelle nicht; Einstellungen lassen sich dann nicht neu laden und bei lokalen Images kein Format wählen. Dunkles Design, große Schrift sowie die Reiter WLAN, SSH, Schnittstellen und Connect im neuen Formular wurden nicht erneut visuell geprüft. Physische USB-Kompatibilität, Pi-Erststart und TalkBack bleiben Hardwaretests.
