# Screenshots

Übersicht aller Aufnahmen in diesem Ordner. Sie stammen aus Android-Emulatoren und zeigen die deutsche Oberfläche. Die Zahl im Dateinamen ist die Breite in dp. Eingaben sind synthetische Testwerte; Passwörter und Schlüssel erscheinen maskiert.

Eine Aufnahme zeigt den Stand ihrer Version, nicht zwingend die aktuelle Oberfläche.

## Schritt-für-Schritt-Ablauf

Die App führt in fünf Schritten durch den Vorgang: Pi-Modell → Betriebssystem → Einstellungen → USB-Speicher → Schreiben.

- **Pi-Modell:** Die Modelle stehen direkt als Liste. Ein Tipp wählt das Modell und wechselt automatisch weiter; einen „Weiter“-Knopf gibt es dort nicht. Das Modell steht danach im Stepper bei Schritt 1.
- **Betriebssystem:** Der Katalog wird direkt im Schritt angezeigt, mit Suche und Unterkategorien; darunter steht „Lokales Image auswählen“. Sobald ein Image gewählt ist, startet das Laden im Hintergrund und die App wechselt zu den Einstellungen.
- **Einstellungen:** Die Vorkonfiguration für den ersten Start wird ausgefüllt, während das Image lädt. „Weiter“ schreibt sie in das geladene Image, ohne erneuten Download, und öffnet danach automatisch den USB-Schritt.
- **USB-Speicher** und **Schreiben** wie zuvor; „Weiter“ ist erst aktiv, wenn der Schritt erledigt ist.

Erledigte Schritte erhalten einen Haken und lassen sich direkt antippen. Schritte wechseln mit einer Schiebe- und Überblendanimation. Unter 840 dp steht der Stepper oben (unter 560 dp nur Schritt 1 mit Namen), ab 840 dp als Schrittliste mit dem jeweiligen Stand links.

Die `width-*.png` erzeugt `scripts/emulator_smoke.py` automatisch. Sie zeigen den ersten Schritt nach dem Start.

| Datei | Pixel | Sichtbar |
| --- | --- | --- |
| [width-320.png](width-320.png) | 640 × 1600 | Kleinstes Handy-Layout: Stepper, Einleitung und Anfang der Modellliste. |
| [width-360.png](width-360.png) | 720 × 1600 | Handy-Layout. |
| [width-411.png](width-411.png) | 822 × 1644 | Großes Handy-Layout. |
| [width-600.png](width-600.png) | 1200 × 2400 | Kleines Tablet: Stepper mit allen Schrittnamen. |
| [width-840.png](width-840.png) | 1680 × 2400 | Tablet hochkant: Schrittliste links, Modellliste zweispaltig rechts. |
| [stepper-model-chosen-360.png](stepper-model-chosen-360.png) | 720 × 1600 | Handy direkt nach Tipp auf „Raspberry Pi 5“: automatisch in Schritt 2 mit dem Katalog, „Pi 5“ unter Schritt 1. |
| [stepper-catalog-search-360.png](stepper-catalog-search-360.png) | 720 × 1600 | Katalogsuche nach „Lite“ im Pi-5-Katalog bei geöffneter Android-Tastatur. |
| [stepper-subcategory-360.png](stepper-subcategory-360.png) | 720 × 1600 | Geöffnete Unterkategorie „Raspberry Pi OS (other)“ mit „‹ Übergeordnete Kategorie“; die Liste beginnt oben. |
| [stepper-local-option-360.png](stepper-local-option-360.png) | 720 × 1600 | Ende der Katalogliste mit Kategorien (›) und darunter „Lokales Image auswählen“. |
| [stepper-settings-loading-840.png](stepper-settings-loading-840.png) | 1680 × 2400 | Tablet hochkant, Schritt 3 während des automatisch gestarteten Downloads von Raspberry Pi OS (64-bit); „Weiter“ wurde bereits gedrückt, die App wartet auf das Image. |
| [stepper-settings-360.png](stepper-settings-360.png) | 720 × 1600 | Handy, Schritt 3 mit ausgefüllter Vorkonfiguration, Reiter „System“ (synthetisches Passwort, maskiert). |
| [stepper-settings-wifi-360.png](stepper-settings-wifi-360.png) | 720 × 1600 | Reiter „WLAN“ mit Test-SSID und maskiertem synthetischem Passwort. |
| [stepper-settings-connect-840.png](stepper-settings-connect-840.png) | 1680 × 2400 | Tablet hochkant, Reiter „Connect“ mit maskiertem synthetischem Schlüssel; die Schrittliste zeigt das geladene lokale Image. |
| [stepper-storage-360.png](stepper-storage-360.png) | 720 × 1600 | Handy, Schritt 4 nach Laden und Vorkonfigurieren eines 68-MB-Test-Images: Schritte 1 bis 3 mit Haken, „Weiter“ ohne USB-Speicher gesperrt. |
| [tablet-landscape-1280.png](tablet-landscape-1280.png) | 2560 × 1600 | Tablet quer, Schritt 4: Schrittliste mit „Raspberry Pi 5“, „Lokales Image · Image bereit · 68,16 MB“ und „Konfiguriert: raspberrypi · Benutzer pi“. |

| [stepper-dark-360.png](stepper-dark-360.png) | 720 × 1600 | Schritt 1 im dunklen Design. Der Rahmen um Schritt 1 ist Androids Fokusanzeige, weil der Emulator seit dem Neustart nicht berührt wurde. |

Alle `stepper-*` und `tablet-landscape-1280.png` sind von Hand im Emulator mit Version 0.6.0 aufgenommen.

## Modellauswahl und Katalog (historisch)

Version 0.4.0, siehe [Prüfbericht](../VALIDATION-0.4.0.md). Modellauswahl und Katalog waren damals eigene Dialoge; heute sind sie Schritt 1 und 2 des Ablaufs oben.

| Datei | Pixel | Sichtbar |
| --- | --- | --- |
| [model-picker-360.png](model-picker-360.png) | 720 × 1600 | Dialog „Pi-Modell wählen“ mit „Alle Modelle“ als aktiver Auswahl und dem Hinweis, dass ein Modellwechsel Image-Auswahl und Einstellungen zurücksetzt. Stand 0.4.0; der Dialog ist inzwischen durch die Modellliste in Schritt 1 ersetzt. |
| [catalog-filter-320.png](catalog-filter-320.png) | 640 × 1600 | Katalog, gefiltert auf Raspberry Pi 2, mit leerem Suchfeld. |
| [catalog-keyboard-320.png](catalog-keyboard-320.png) | 640 × 1600 | Suche nach „Ubuntu“ im Pi-2-Katalog bei geöffneter Android-Tastatur; Treffer ist die Kategorie „Other general-purpose OS“. |
| [catalog-search-840.png](catalog-search-840.png) | 1680 × 1600 | Suche nach „Lite“ im Pi-5-Katalog im Tablet-Layout, mit zwei Kategorien als Treffer. |

## Einstellungen für den ersten Start (historisch)

Damals ein eigener Dialog; heute Schritt 3 des Ablaufs oben.

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
