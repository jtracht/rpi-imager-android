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

## Ende-zu-Ende-Test mit echten Images und virtuellen USB-Sticks

Am 3. November 2026 nach dem Release im Emulator (API 35, 1,5 GB RAM) mit zwei virtuellen 4-GiB-USB-Sticks: QEMU-`usb-storage`-Geräte an einem `qemu-xhci`-Controller, Rohdateien auf dem Host. Das Emulator-Image meldet `android.hardware.usb.host` nicht; für den Test wurde die Standard-Berechtigungsdatei per Bind-Mount ergänzt und das Framework neu gestartet (nur Test-AVD, SELinux dabei permissive). Danach listet Android beide Sticks als „QEMU USB HARDDRIVE“ und die App zeigt sie im USB-Schritt.

| Lauf | Image | Vorkonfiguration | Ziel | Ergebnis |
| --- | --- | --- | --- | --- |
| A | Raspberry Pi OS Lite (64-bit) 2026-10-06 aus dem Katalog, echter Download, SHA-256 gegen Katalog geprüft | cloud-init (`cloudinit-rpi`): Hostname, Passwort, SSH mit Passwortanmeldung; bestätigt während des Downloads, danach ins geladene Image geschrieben | Stick 0 | App: „Geprüft. Dein Datenträger ist bereit.“ |
| B | Raspberry Pi OS (Legacy, 64-bit) Lite 2026-10-06 als lokale `.img.xz` über die Dokumentauswahl | systemd-Firstrun | Stick 1 | App: „Geprüft. Dein Datenträger ist bereit.“ |

Unabhängige Gegenprüfung auf dem Host: Die ersten 3 078 619 136 bzw. 2 743 074 816 Bytes der Sticks haben exakt den SHA-256-Wert des vorbereiteten Images aus dem App-Speicher (`322bf1a5…ca8f53` bzw. `8473fb2e…e1ac2f9`). MBR mit FAT32-Boot- und Linux-Root-Partition ist intakt. Auf der Bootpartition stehen in Lauf A die cloud-init-Konfiguration mit Hostname, `ssh_pwauth: true` und der Instanzkennung der App, in Lauf B `firstrun.sh` mit Hostname und der Hook `systemd.run=/boot/firstrun.sh` in `cmdline.txt`. Das Passwort steht in keinem Fall im Klartext auf dem Datenträger. Stick 0 blieb beim Schreiben von Stick 1 unverändert. Schreiben und Rücklesen über das virtuelle USB dauerten je rund 1–2 Minuten pro 3 GB.

Beim Test gefunden und behoben: Die Statuskarte zeigte nach dem Bestätigen der Einstellungen bereits während des Downloads „Einstellungen werden ins Image geschrieben…“; sie unterscheidet jetzt Laden und Vorkonfigurieren. Abgelehnte Einstellungen standen nur am Ende des Formulars und blieben bei weggescrolltem Formular unsichtbar; die Meldung steht jetzt zusätzlich rot neben „Weiter“.

## Veröffentlichungsprüfung

[CI mit Android-Emulator](https://github.com/jtracht/rpi-imager-android/actions/runs/41607231520) und [Tag-Release-Pipeline](https://github.com/jtracht/rpi-imager-android/actions/runs/41607726409) erfolgreich. [Release v0.6.0](https://github.com/jtracht/rpi-imager-android/releases/tag/v0.6.0) mit signierter APK und allen Quell-/Prüfsummenanhängen veröffentlicht. Sämtliche Anhänge erneut heruntergeladen und gegen `SHA256SUMS` geprüft. Die APK meldet `versionName=v0.6.0`, besitzt denselben Signaturschlüssel wie die Vorgänger und enthält keine Test-Provider oder Instrumentierungs-Komponenten. Die mit R8 optimierte APK auf API 35 installiert und kalt gestartet; Modellliste angezeigt, Modellwahl führte automatisch zu Schritt 2 mit Katalog; kein Crash-Log.

## Verbleibende Grenzen

Schreiben und Prüfen sind nur mit emulierten USB-Massenspeichern getestet, nicht mit physischen Kartenlesern oder Sticks; ein virtueller Stick bildet keine langsamen, fehlerhaften oder herausgezogenen Medien nach. Die Prüfmeldungen der Vorkonfiguration sind weiterhin nur auf Englisch. Katalog-Images werden beim Antippen sofort geladen, auch über mobile Daten. Abbruch während des Schreibens der Einstellungen verwirft das geladene Image. Übernimmt eine neue Activity ein Image aus einer laufenden Vorbereitung, kennt sie dessen Quelle nicht; Einstellungen lassen sich dann nicht neu laden und bei lokalen Images kein Format wählen. Dunkles Design, große Schrift sowie die Reiter WLAN, SSH, Schnittstellen und Connect im neuen Formular wurden nicht erneut visuell geprüft. Physische USB-Kompatibilität, Pi-Erststart und TalkBack bleiben Hardwaretests.
