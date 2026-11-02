# Screenshots

Übersicht aller Aufnahmen in diesem Ordner. Sie stammen aus Android-Emulatoren und zeigen die deutsche Oberfläche. Die Zahl im Dateinamen ist die Breite in dp. Eingaben sind synthetische Testwerte; Passwörter und Schlüssel erscheinen maskiert.

Eine Aufnahme zeigt den Stand ihrer Version, nicht zwingend die aktuelle Oberfläche.

## Startseite

Automatisch von `scripts/emulator_smoke.py` erzeugt, zuletzt aktualisiert für Version 0.4.0. Noch ohne ausgewähltes Image, daher sind die abhängigen Schaltflächen gesperrt.

| Datei | Pixel | Sichtbar |
| --- | --- | --- |
| [width-320.png](width-320.png) | 640 × 1600 | Kleinstes Handy-Layout: Modellkarte und Anfang von „1 · Betriebssystem“. „Pi-Modell wählen“ erscheint hervorgehoben. |
| [width-360.png](width-360.png) | 720 × 1600 | Handy-Layout, einspaltig. |
| [width-411.png](width-411.png) | 822 × 1644 | Großes Handy-Layout, einspaltig. |
| [width-600.png](width-600.png) | 1200 × 2400 | Weiterhin einspaltig; Schritt 1 und der Anfang von „2 · USB-Speicher“ sind sichtbar. |
| [width-840.png](width-840.png) | 1680 × 2400 | Tablet-Layout: Schritte 1 und 2 nebeneinander, darunter Schritt 3 mit gesperrtem „Image schreiben“. |

## Modellauswahl und Katalog

Version 0.4.0, siehe [Prüfbericht](../VALIDATION-0.4.0.md).

| Datei | Pixel | Sichtbar |
| --- | --- | --- |
| [model-picker-360.png](model-picker-360.png) | 720 × 1600 | Dialog „Pi-Modell wählen“ mit „Alle Modelle“ als aktiver Auswahl und dem Hinweis, dass ein Modellwechsel Image-Auswahl und Einstellungen zurücksetzt. |
| [catalog-filter-320.png](catalog-filter-320.png) | 640 × 1600 | Katalog, gefiltert auf Raspberry Pi 2, mit leerem Suchfeld. |
| [catalog-keyboard-320.png](catalog-keyboard-320.png) | 640 × 1600 | Suche nach „Ubuntu“ im Pi-2-Katalog bei geöffneter Android-Tastatur; Treffer ist die Kategorie „Other general-purpose OS“. |
| [catalog-search-840.png](catalog-search-840.png) | 1680 × 1600 | Suche nach „Lite“ im Pi-5-Katalog im Tablet-Layout, mit zwei Kategorien als Treffer. |

## Einstellungen für den ersten Start

| Datei | Version | Pixel | Sichtbar |
| --- | --- | --- | --- |
| [settings-system-320.png](settings-system-320.png) | [0.2.0](../VALIDATION-0.2.0.md) | 640 × 1600 | Reiter „System“ mit Hostname, Benutzername und Passwort. Noch ohne Reiter „Connect“. |
| [settings-wifi-840.png](settings-wifi-840.png) | [0.2.0](../VALIDATION-0.2.0.md) | 840 × 1200 | Reiter „WLAN“ mit Land, SSID, Passwort und verstecktem Netzwerk. Noch ohne Reiter „Connect“. Als einzige Tablet-Aufnahme in einfacher Pixeldichte. |
| [settings-connect-320.png](settings-connect-320.png) | [0.3.0](../VALIDATION-0.3.0.md) | 640 × 1600 | Reiter „Connect“ auf dem Handy, nach unten gescrollt, mit maskiertem Auth-Schlüssel. |
| [settings-connect-840.png](settings-connect-840.png) | [0.3.0](../VALIDATION-0.3.0.md) | 1680 × 2000 | Derselbe Reiter im Tablet-Layout, mit allen fünf Reitern. |

## Vorbereitung im Hintergrund

Version 0.5.0, signierte Release-Vorschau auf API 35, siehe [Prüfbericht](../VALIDATION-0.5.0.md).

| Datei | Pixel | Sichtbar |
| --- | --- | --- |
| [preparation-timeout.png](preparation-timeout.png) | 1680 × 2200 | Fehlerdialog, nachdem Android die Vorbereitung wegen seines Hintergrund-Zeitlimits beendet hat. Das Limit war für den Test auf acht Sekunden verkürzt. |
| [preparation-background.png](preparation-background.png) | 1680 × 2200 | Android-Benachrichtigung „Image vorbereitet und geprüft“. Der Eintrag „Serial console enabled“ und „T-Mobile“ stammen vom Emulator. |
| [preparation-real-image-ready.png](preparation-real-image-ready.png) | 1680 × 2200 | Startseite mit „Image bereit · 3,08 GB“ nach dem Import des offiziellen Raspberry-Pi-OS-Images. |

## Aktualisieren

Die `width-*.png`-Dateien erzeugt und überschreibt dieser Aufruf; die APK muss vorher auf dem Emulator installiert sein:

```
python scripts/emulator_smoke.py /pfad/zu/adb emulator-5554
```

Für alle anderen Aufnahmen gibt es kein Skript, sie entstehen von Hand. Dabei nur synthetische Testwerte verwenden, Geheimnisse maskiert lassen und diese Übersicht ergänzen.
