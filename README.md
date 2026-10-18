# Pi Imager für Android

Unabhängige native Android-Portierung auf Basis des [Raspberry Pi Imager](https://github.com/raspberrypi/rpi-imager). Kotlin und Jetpack Compose ersetzen die Qt-Desktopoberfläche; USB Host und libaums ersetzen den Desktop-Laufwerkszugriff. Der vollständige Upstream ist als Git-Submodul auf einen konkreten Release-Commit festgelegt. Die App übernimmt die Katalog-URL aus dessen `src/config.h`. **Upstream-C++-Änderungen werden dadurch nicht automatisch zu Android-Funktionen**: Der Android-Adapter muss bei Funktions- und Protokolländerungen angepasst werden.

## Installation und Verwendung

Unter [Releases](https://github.com/jtracht/rpi-imager-android/releases) die signierte `pi-imager-android.apk` laden. Android 8 oder neuer, USB-Host/OTG und ein USB-SD-Kartenleser oder USB-Stick mit SCSI/Bulk-Only-Transport sind erforderlich. Kein Root erforderlich. Interne SD-Steckplätze können über Androids Dateiauswahl nicht als Rohdatenträger beschrieben werden.

1. Betriebssystem aus dem offiziellen Katalog oder lokale IMG-/ISO-Datei auswählen. XZ, GZ und ZIP werden anhand ihrer Dateisignatur erkannt. Bei ZIP wird die erste IMG-/ISO-Datei verwendet.
2. Optional „Ersten Start konfigurieren“ öffnen: Benutzerkonto, Hostname, Zeitzone, Tastaturlayout, WLAN, SSH, Connect und Hardware-Schnittstellen einstellen. Bei lokalen Images das tatsächlich unterstützte Einrichtungsformat auswählen; bei Katalog-Images wird `init_format` übernommen.
3. Image vorbereiten. Das entpackte Image liegt im privaten App-Speicher; entsprechend viel freier Speicher ist erforderlich. Katalog-Images brauchen einen gültigen SHA-256-Wert. Die App prüft zuerst das Original, passt danach die FAT32-Bootpartition an und berechnet einen neuen SHA-256-Wert für das konfigurierte Image. Lokale Images erhalten einen eigenen SHA-256-Wert, jedoch keinen externen Herkunftsnachweis.
4. Datenträger in den Android-Speichereinstellungen auswerfen, angeschlossen lassen und in der App USB-Zugriff erlauben. Bei Kartenlesern mit mehreren Speichereinheiten werden die bestückten LUNs einzeln mit Kapazität angezeigt.
5. Ziel einschließlich LUN im Löschdialog kontrollieren und Schreiben bestätigen. Danach prüft die App jeden geschriebenen Sektor einschließlich der Auffüllbytes. Erst nach erfolgreicher Prüfung abziehen.

Der Schreibvorgang läuft als Vordergrunddienst mit Benachrichtigung und Wake Lock weiter. Ein Abbruch, Prozessende, Neustart oder Abziehen hinterlässt eventuell einen unvollständigen Datenträger; es gibt keinen automatischen Neustart des Schreibvorgangs. Downloads laufen nur im App-Prozess und sind nicht fortsetzbar. Physische USB-Kompatibilität und TalkBack sind separat auf Geräten zu testen; CI allein bestätigt diese nicht.

## Vorkonfiguration ohne Root

Die App bearbeitet eine temporäre Image-Datei, keine auf Android eingehängte Partition. Partitionstabelle und FAT32-Geometrie werden geprüft; sämtliche Dateisystemzugriffe bleiben innerhalb der Bootpartition. Bei GPT werden Header- und Tabellen-Prüfsummen kontrolliert. Die erzeugten Dateien werden nach dem Schreiben erneut gelesen. Beide FAT-Kopien und die FSInfo-Sicherung werden synchronisiert. Die Linux-Rootpartition wird nicht verändert.

| Format des Images | Erzeugte Dateien / Verfahren |
| --- | --- |
| `cloudinit`, `cloudinit-rpi` | `user-data`, `network-config` bei WLAN, `meta-data` mit neuer Instanz-ID und passender NoCloud-Kerneloption |
| `systemd` | `firstrun.sh`, Einmal-Start über Kerneloptionen, Benutzer-/SSH-Einrichtung über Raspberry-Pi-OS-Helfer; WLAN über NetworkManager-Profil oder Legacy-Helfer |
| `rpi-preseed` | `rpi-preseed.toml` mit System-, Benutzer-, SSH-, WLAN-, Locale- und Schnittstellen-Einstellungen |

Unterstützt sind Benutzername und Passwort, Hostname, Zeitzone, Tastaturlayout, WPA-Personal-/offene und versteckte WLANs, WLAN-Land, SSH mit Passwort oder öffentlichen OpenSSH-Schlüsseln, optionales Sudo ohne Passwort sowie I²C, SPI und 1-Wire. Benutzerpasswörter stehen als gesalzene Linux-SHA-512-crypt-Hashes im Image. WLAN-Passwörter müssen je nach Einrichtungsverfahren im Klartext auf der Bootpartition stehen. Die Einstellungen bleiben nur im Arbeitsspeicher der App-Sitzung; auch ungespeicherte Eingaben überstehen einen Bildschirmwechsel durch Rotation. Sie werden nicht in Androids gespeicherten Zustand oder Einstellungen geschrieben.

Das vorbereitete Image kann Zugangsdaten enthalten und bleibt bis zur nächsten Vorbereitung, „Image und Einstellungen löschen“, App-Daten-Löschung oder Deinstallation im privaten App-Speicher. Der Löschknopf ist auch nach einem App-Neustart verfügbar. Löschen entfernt die Datei; es ist kein garantiertes physisches Überschreiben von Flash-Speicher.

Bei lokalen Images lässt eine ausgeschaltete WLAN-Einrichtung vorhandene Netzwerkeinstellungen des Ausgangsimages unverändert. Die Android-App verändert oder löscht die ausgewählte Originaldatei nicht.

Grenzen: Vorkonfiguration benötigt eine FAT32-Bootpartition mit `config.txt` und `cmdline.txt` in einem MBR-/GPT- oder eigenständigen FAT32-Image. FAT16-/ext4-only-/beliebige ISO-Bootdateisysteme lassen sich unverändert schreiben, aber nicht vorkonfigurieren. Ein als `systemd` markiertes Image braucht die Raspberry-Pi-OS-Helfer `imager_custom` und gegebenenfalls `userconf`. Unbekannte Katalogformate werden nicht automatisch umgedeutet. Secure Boot, USB-Gadget-/Fastboot-Provisionierung und UAS-only-Geräte sind weiterhin nicht portiert. Die App behauptet daher keine vollständige Funktionsgleichheit mit der Desktop-Anwendung.

## Raspberry Pi Connect

Im Bereich „Connect“ lässt sich ein persönlicher oder Organisations-Auth-Schlüssel für den ersten Start hinterlegen. Der Knopf öffnet [Connect im Browser](https://connect.raspberrypi.com/settings); dort anmelden, einen neuen Auth-Schlüssel erstellen und in die App einfügen. Zugangsdaten des Raspberry-Pi-Kontos werden ausschließlich auf der Connect-Webseite eingegeben. Die Android-App verwendet keine fremde OAuth-Client-ID, keinen eingebetteten Login und keinen ungeprüften Deep-Link zur Schlüsselübernahme.

Die App prüft das im festgehaltenen Upstream verwendete Format (`rpuak_` / `rpoak_`, 24 Base58-Zeichen), nicht Gültigkeit oder Ablauf beim Server. Katalog-Images müssen die Fähigkeit `rpi_connect` ausdrücklich ausweisen. Bei lokalen Images muss Connect mit dem Anmeldedienst bereits installiert sein; die App installiert keine Pakete. Cloud-init und Legacy-Systemd legen den Schlüssel nach der Benutzeranlage in dessen tatsächlichem Home als `.config/com.raspberrypi.connect/auth.key` mit Modus 0600 ab, aktivieren die Benutzerdienste und Linger. WayVNC wird nur aktiviert, wenn vorhanden. Preseed verwendet den Upstream-Vertrag `[connect]`, `enabled = true`, `mode = "token"` und `token`.

Für **jeden Pi einen neuen Schlüssel** verwenden und das Image erneut vorbereiten. Persönliche Schlüssel gelten sechs Stunden, Organisationsschlüssel bis zum bei Erstellung gewählten Ablauf. Der Pi braucht beim ersten Start Internetzugang und muss den Schlüssel vor Ablauf einlösen. Die App weist auch am vorbereiteten Image auf die einmalige Verwendung hin. Schlüssel bleiben nur in der Sitzungseinstellung und im vorbereiteten Image; „Image und Einstellungen löschen“ entfernt beides. Auf der Bootpartition ist der Schlüssel im First-Run-Skript, Cloud-init-Dokument oder Preseed bis zur Verarbeitung lesbar. Die tatsächliche Konto-Verknüpfung findet auf dem Pi statt und wurde hier noch nicht auf Hardware getestet. Siehe [offizielle Connect-Dokumentation](https://www.raspberrypi.com/documentation/services/connect.html#turn-on-the-connect-service-and-link-your-device).

## Automatische Releases

- `sync-upstream.yml` prüft stündlich um Minute 17 sowie manuell veröffentlichte stabile Upstream-Releases. GitHub kann geplante Ausführungen verzögern und bei inaktiven öffentlichen Repositories deaktivieren. Drafts und Vorabversionen sind ausgenommen.
- Neue Releases werden in Veröffentlichungsreihenfolge verarbeitet: Submodul aktualisieren, Commit und Konfiguration erfassen, Android-Tag `upstream/<upstream-tag>-android.1` erstellen, APK bauen und Release veröffentlichen. Zwischen zwei Prüfungen erschienene Releases werden nicht übersprungen; je Lauf wird eines verarbeitet. Ein fehlgeschlagener Build wird beim nächsten Lauf zuerst erneut versucht.
- Eigene Tags **jedes Namens** lösen `release.yml` aus und erhalten ebenfalls einen Release mit APK. Tags sind unveränderliche Quellstände. Nach einer Fehlerkorrektur einen neuen Tag erstellen oder beim Wiederholen den ursprünglichen Workflow korrigieren; ein Retry desselben Tags baut weiterhin dessen ursprünglichen Code.
- Der Sync ruft den Release-Workflow ausdrücklich auf: Tags, die mit `GITHUB_TOKEN` erstellt werden, lösen keinen weiteren Push-Workflow aus. Es wird nicht für jeden Release ein neues GitHub-Fork-Repository erstellt; stattdessen aktualisiert dieses Android-Derivat seinen festgehaltenen Upstream-Quellstand.
- Release-Anhänge: signierte Universal-APK, vollständiger Upstream-Quellcode als Archiv, `upstream.properties` und `SHA256SUMS`. Der USB-Transport verwendet ausschließlich Android USB Host; alte JNI-Hilfsbibliotheken von libaums werden ausgeschlossen, damit sie die Unterstützung für Geräte mit 16-KB-Speicherseiten nicht einschränken.
- `ci.yml` prüft Pull Requests und `main`: Upstream-Vertrag, Sync-Tests, Schreibkern-Tests, Android Lint und Debug-APK. Eine inkompatible Upstream-Konfiguration stoppt den Build statt stillschweigend weiterzumachen.
- Sowohl CI als auch Release prüfen generierte Vorkonfigurationsdateien mit unabhängigen YAML-/TOML-Parsern und POSIX-Shell-Syntaxprüfung. Die Skripte werden dabei nicht ausgeführt.

### GitHub-Einrichtung

Actions müssen aktiviert sein, der Workflow braucht Schreibzugriff auf Repository-Inhalte und darf auf `main` pushen. Branch-Regeln können den Sync blockieren. Keine zusätzlichen PATs erforderlich.

Folgende Actions-Secrets werden für echte Release-Signaturen benötigt; ohne sie bricht der Release-Build ab:

| Secret | Inhalt |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 des privaten JKS-Keystores |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore-Passwort |
| `ANDROID_KEY_ALIAS` | Alias des Signaturschlüssels |
| `ANDROID_KEY_PASSWORD` | Schlüsselpasswort |

Keystore und Passwörter unabhängig von GitHub sicher sichern. Derselbe Schlüssel ist für Updates installierter APKs notwendig. Keine Schlüssel im Git-Repository ablegen. GitHub Secrets lassen sich nach dem Setzen nicht wieder auslesen.

## Lokal bauen

JDK 17, Android SDK 36 und Build Tools 35.0.0 installieren. `ANDROID_HOME` setzen oder `sdk.dir` in der ignorierten `local.properties` eintragen.

```sh
git clone --recurse-submodules https://github.com/jtracht/rpi-imager-android.git
cd rpi-imager-android
python scripts/prepare_upstream.py
python -m unittest discover -s scripts -p 'test_*.py'
./gradlew testDebugUnitTest lintDebug assembleDebug
python -m pip install PyYAML==6.0.2
python scripts/validate_customization.py
```

Unter Windows `gradlew.bat` verwenden. Für einen signierten Release die Umgebungsvariablen `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` setzen und `assembleRelease` ausführen. Versionierung erfolgt über `ANDROID_VERSION_NAME` und `ANDROID_VERSION_CODE`; CI verwendet Sekunden seit dem 1. Januar 2024 UTC, gemeinsam für Tag- und Sync-Builds. Der unveränderte Gradle Wrapper ist mit Prüfsumme abgesichert.

## Prüfung und Screenshots

Connect-Nachweise für Version 0.3.0: [Prüfbericht](docs/VALIDATION-0.3.0.md), [Handy-Dialog](docs/screenshots/settings-connect-320.png), [Tablet-Dialog](docs/screenshots/settings-connect-840.png).

Build-, Release- und Emulator-Nachweise der ersten Version stehen in [docs/VALIDATION.md](docs/VALIDATION.md), die Prüfungen der Vorkonfiguration in [docs/VALIDATION-0.2.0.md](docs/VALIDATION-0.2.0.md). Die Oberfläche wurde bei 320, 360, 411, 600 und 840 dp geprüft: [Handy](docs/screenshots/width-360.png), [Tablet](docs/screenshots/width-840.png), [System-Einstellungen](docs/screenshots/settings-system-320.png), [WLAN-Einstellungen](docs/screenshots/settings-wifi-840.png). Für eine Wiederholung die APK auf einem laufenden Emulator installieren und `python scripts/emulator_smoke.py /pfad/zu/adb emulator-5554` ausführen. Der Test schreibt keine USB-Datenträger.

## Nachweise und Lizenz

`upstream.properties` und der Submodul-Zeiger dokumentieren den genauen Stand. Die generierten App-Assets enthalten Katalog-URL und Upstream-Lizenz. Apache-2.0 für den Android-Code; siehe `LICENSE`, `NOTICE` und die separaten Lizenzhinweise im Upstream. Diese App ist kein offizielles Produkt der Raspberry Pi Ltd und verwendet kein Raspberry-Pi-Markenlogo. Keine Telemetrie oder Analyse-SDKs. Netzwerkzugriffe erfolgen für den OS-Katalog und die vom Benutzer ausgewählten Images.
