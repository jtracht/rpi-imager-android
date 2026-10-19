# Hintergrund-Vorbereitung: Prüfung am 19. Oktober 2026

Zielrelease: `v0.5.0`. Upstream bleibt `v2.0.12`, Commit `84b03d96cd4c390ebe475a4b5db5735e5535e9a6`.

## Umfang

Download, Entpacken, Prüfsumme und Vorkonfiguration laufen in einem separaten `dataSync`-Vordergrunddienst. Activity und ViewModel dürfen beendet werden, während der Dienst weiterarbeitet. Eine neue Activity im selben Prozess übernimmt Fortschritt bzw. das fertige Image. Start-Intents enthalten eine zufällige Auftragskennung und bei lokalen Dokumenten eine temporäre URI-Lesefreigabe; die eigentlichen Einstellungen und Zugangsdaten bleiben im Arbeitsspeicher. Kein automatischer Neustart nach Prozessende oder Geräte-Neustart und keine automatische Fortsetzung von Downloads oder USB-Schreiben.

Abbruch-Prüfpunkte bestehen auch während SHA-256-Berechnungen und vor der Übernahme des fertigen Images. Fortschrittsanzeigen sind auf zwei Aktualisierungen pro Sekunde begrenzt. Beim Android-15-Zeitlimit stoppt `onTimeout` den Dienst umgehend; blockierte I/O-Aufrufe können separat auslaufen. Grundlage ist die [offizielle Android-Dokumentation](https://developer.android.com/develop/background-work/services/fgs/timeout).

## Automatische Prüfungen

- 49 reguläre JVM-Tests bestanden, darunter vier neue Tests für Auftragsvalidierung und abbrechbares Hashing einschließlich Schließen des Eingabestreams sowie fünf neue Tests zur Entpackung. Die beiden optionalen Tests mit vollständigen offiziellen Images sind ohne gesetzte Testvariablen übersprungen.
- Debug-Build und Android Lint bestanden; Test-APK für den Emulator erfolgreich gebaut.
- Neuer Emulator-Test auf Android 15 / API 35 bestanden: ein separater Test-Provider liefert eine absichtlich gedrosselte synthetische 64-MiB-Datei. Nach Start wird die Activity beendet. Der Dienst beendet die Vorbereitung mit der erwarteten SHA-256-Prüfsumme. Eine neue Activity zeigt das Ergebnis. Ein zweiter Lauf wird abgebrochen; weder temporäres noch vorbereitetes Image bleibt zurück.
- Der Test verwendet keine Konten, keinen Netzwerkdownload und schreibt keinen USB-Datenträger. Provider und Instrumentierungs-Runner befinden sich ausschließlich im separaten Test-APK, nicht in der veröffentlichten App. Der Provider ist in Java implementiert, damit sein separater Prozess ohne Kotlin-Bibliotheken der Ziel-App läuft.

Zum Wiederholen auf einem ausdrücklich benannten Testemulator: `assembleDebug assembleDebugAndroidTest`, beide Debug-APKs installieren und `python scripts/emulator_preparation.py /pfad/zu/adb emulator-5554` starten. Der Runner prüft das rohe Android-Instrumentierungsergebnis und legt einen Bericht unter `app/build/reports/preparation-probe/` ab. CI führt ihn in einem eigenen API-35-Emulator-Job aus und lädt den Bericht als Artefakt hoch.

## XZ-Kompatibilität

Ein echter offizieller OS-Download zeigte, dass das bisherige XZ-Limit von 65536 KiB ein 64-MiB-Wörterbuch wegen zusätzlicher Decoder-Verwaltung ablehnt (benötigt: 65640 KiB). Der Decoder erhält nun ein begrenztes Budget von 96 MiB. Eine kleine, mit einem 64-MiB-LZMA2-Wörterbuch erzeugte Regressionstest-Datei bestätigt alte Ablehnung und neue erfolgreiche Dekodierung. Ein weiterer Test liefert Signaturen absichtlich byteweise; die Erkennung liest jetzt auch bei kurzen Netzwerk-Reads alle benötigten Signaturbytes, ohne sie im resultierenden Raw-Image zu verlieren. ZIP-Auswahl und Fehler bei fehlendem Image werden ebenfalls geprüft.

## Signierte Release-Vorschau auf Android

- Mit R8 optimierte, signierte APK auf API 35 installiert. Echter offizieller OS-Download gestartet und App verlassen. Der Dienst war als `isForeground=true`, Typ `dataSync` aktiv. Androids Zeitlimit per dokumentierter Testeinstellung auf acht Sekunden verkürzt; Dienst beendete sich ohne Crash und die App zeigte die vorgesehene Zeitlimit-Meldung. Die vorherige globale Testeinstellung (`null`) wurde danach wiederhergestellt. [Screenshot](screenshots/preparation-timeout.png).
- Vollständiges, bereits anhand des offiziellen Katalogs verifiziertes `2026-10-06-raspios-trixie-arm64-lite.img.xz` (550466056 Bytes) anschließend über Androids Dokumentauswahl importiert und im Hintergrund vorbereitet. Ergebnis: 3078619136 Bytes, SHA-256 `b8a393dc1c3701450b5fffbfe74368a98fb6eab7415ce456cc0cead886ea8780`, identisch zum Katalog. Androids Fertig-Benachrichtigung und „Image bereit · 3,08 GB“ in der App bestätigt. [Benachrichtigung](screenshots/preparation-background.png), [fertiges offizielles Image](screenshots/preparation-real-image-ready.png).
- Die App selbst arbeitete dabei unter ihrer normalen Android-Benutzerkennung. Root wurde ausschließlich nach Abschluss zur unabhängigen SHA-256-Prüfung der privaten Image-Datei im Testemulator verwendet. Kein USB-Datenträger wurde beschrieben.

## Grenzen der Prüfung

Kein Fortsetzen unterbrochener Downloads. Android darf Dienste beenden oder begrenzen; Geräte-Neustart, Force-Stop und Prozessverlust erfordern erneute Vorbereitung. Ein Ergebnis wird nach Prozess-Neustart nicht automatisch aus dem App-Speicher rekonstruiert. Bei blockierenden Netzwerkaufrufen kann Abbruch bis zum Verbindungs-Zeitlimit dauern. Physische USB-Kompatibilität, Pi-Erststart, echte Connect-Verknüpfung und TalkBack bleiben separate Hardware-/Kontotests. Secure Boot und Fastboot sind weiterhin nicht portiert.
