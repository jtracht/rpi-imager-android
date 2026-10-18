# Pi Imager für Android

Unabhängige native Android-Portierung auf Basis des [Raspberry Pi Imager](https://github.com/raspberrypi/rpi-imager). Kotlin und Jetpack Compose ersetzen die Qt-Desktopoberfläche; USB Host und libaums ersetzen den Desktop-Laufwerkszugriff. Der vollständige Upstream ist als Git-Submodul auf einen konkreten Release-Commit festgelegt. Die App übernimmt die Katalog-URL aus dessen `src/config.h`. **Upstream-C++-Änderungen werden dadurch nicht automatisch zu Android-Funktionen**: Der Android-Adapter muss bei Funktions- und Protokolländerungen angepasst werden.

## Installation und Verwendung

Unter [Releases](https://github.com/jtracht/rpi-imager-android/releases) die signierte `pi-imager-android.apk` laden. Android 8 oder neuer, USB-Host/OTG und ein USB-SD-Kartenleser oder USB-Stick mit SCSI/Bulk-Only-Transport sind erforderlich. Kein Root erforderlich. Interne SD-Steckplätze können über Androids Dateiauswahl nicht als Rohdatenträger beschrieben werden.

1. Betriebssystem aus dem offiziellen Katalog oder lokale IMG-/ISO-Datei auswählen. XZ, GZ und ZIP werden anhand ihrer Dateisignatur erkannt. Bei ZIP wird die erste IMG-/ISO-Datei verwendet.
2. Image vorbereiten. Das entpackte Image liegt im privaten App-Speicher; entsprechend viel freier Speicher ist erforderlich. Katalog-Images brauchen einen gültigen SHA-256-Wert und werden vor dem Schreiben geprüft. Lokale Images erhalten einen eigenen SHA-256-Wert, jedoch keinen externen Herkunftsnachweis.
3. Datenträger in den Android-Speichereinstellungen auswerfen, angeschlossen lassen und in der App USB-Zugriff erlauben. Das Gerät und seine Kapazität werden angezeigt.
4. Ziel im Löschdialog kontrollieren und Schreiben bestätigen. Danach prüft die App jeden geschriebenen Sektor einschließlich der Auffüllbytes. Erst nach erfolgreicher Prüfung abziehen.

Der Schreibvorgang läuft als Vordergrunddienst mit Benachrichtigung und Wake Lock weiter. Ein Abbruch, Prozessende, Neustart oder Abziehen hinterlässt eventuell einen unvollständigen Datenträger; es gibt keinen automatischen Neustart des Schreibvorgangs. Downloads laufen nur im App-Prozess und sind nicht fortsetzbar. Noch nicht umgesetzt: WLAN-/SSH-/Benutzer-Vorkonfiguration, Raspberry Pi Connect, UAS-only-Geräte, mehrere LUNs. Physische USB-Kompatibilität und TalkBack sind separat auf Geräten zu testen; CI allein bestätigt diese nicht.

## Automatische Releases

- `sync-upstream.yml` prüft stündlich um Minute 17 sowie manuell veröffentlichte stabile Upstream-Releases. GitHub kann geplante Ausführungen verzögern und bei inaktiven öffentlichen Repositories deaktivieren. Drafts und Vorabversionen sind ausgenommen.
- Neue Releases werden in Veröffentlichungsreihenfolge verarbeitet: Submodul aktualisieren, Commit und Konfiguration erfassen, Android-Tag `upstream/<upstream-tag>-android.1` erstellen, APK bauen und Release veröffentlichen. Zwischen zwei Prüfungen erschienene Releases werden nicht übersprungen; je Lauf wird eines verarbeitet. Ein fehlgeschlagener Build wird beim nächsten Lauf zuerst erneut versucht.
- Eigene Tags **jedes Namens** lösen `release.yml` aus und erhalten ebenfalls einen Release mit APK. Tags sind unveränderliche Quellstände. Nach einer Fehlerkorrektur einen neuen Tag erstellen oder beim Wiederholen den ursprünglichen Workflow korrigieren; ein Retry desselben Tags baut weiterhin dessen ursprünglichen Code.
- Der Sync ruft den Release-Workflow ausdrücklich auf: Tags, die mit `GITHUB_TOKEN` erstellt werden, lösen keinen weiteren Push-Workflow aus. Es wird nicht für jeden Release ein neues GitHub-Fork-Repository erstellt; stattdessen aktualisiert dieses Android-Derivat seinen festgehaltenen Upstream-Quellstand.
- Release-Anhänge: signierte Universal-APK, vollständiger Upstream-Quellcode als Archiv, `upstream.properties` und `SHA256SUMS`. Der USB-Transport verwendet ausschließlich Android USB Host; alte JNI-Hilfsbibliotheken von libaums werden ausgeschlossen, damit sie die Unterstützung für Geräte mit 16-KB-Speicherseiten nicht einschränken.
- `ci.yml` prüft Pull Requests und `main`: Upstream-Vertrag, Sync-Tests, Schreibkern-Tests, Android Lint und Debug-APK. Eine inkompatible Upstream-Konfiguration stoppt den Build statt stillschweigend weiterzumachen.

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
```

Unter Windows `gradlew.bat` verwenden. Für einen signierten Release die Umgebungsvariablen `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` setzen und `assembleRelease` ausführen. Versionierung erfolgt über `ANDROID_VERSION_NAME` und `ANDROID_VERSION_CODE`; CI verwendet Sekunden seit dem 1. Januar 2024 UTC, gemeinsam für Tag- und Sync-Builds. Der unveränderte Gradle Wrapper ist mit Prüfsumme abgesichert.

## Nachweise und Lizenz

`upstream.properties` und der Submodul-Zeiger dokumentieren den genauen Stand. Die generierten App-Assets enthalten Katalog-URL und Upstream-Lizenz. Apache-2.0 für den Android-Code; siehe `LICENSE`, `NOTICE` und die separaten Lizenzhinweise im Upstream. Diese App ist kein offizielles Produkt der Raspberry Pi Ltd und verwendet kein Raspberry-Pi-Markenlogo. Keine Telemetrie oder Analyse-SDKs. Netzwerkzugriffe erfolgen für den OS-Katalog und die vom Benutzer ausgewählten Images.
