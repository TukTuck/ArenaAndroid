# Arena für Android

Schlanke, schnelle App, die **arena.ai** auf Android-Geräten stabil und zugänglich
bereitstellt – speziell für **Geräte bis Android 7**, die mit der normalen Website
überfordert sind. Ganz ohne Werbung, ohne Tracking, ohne unnötige Berechtigungen.

## Warum?

Auf alten Handys kommt arena.ai im Browser oft nur mühsam zustande: ewiges Laden,
weiße Seiten, Abstürze, Neuladen von Hand. Diese App packt die Seite in einen
robusten eigenen Rahmen:

* **Stabil:** Rendert die Seite neu, wenn der Web-Inhalt abbricht – inklusive
  automatischer Wiederherstellung nach einem App-Absturz. Klare Offline- und
  Fehlerseiten mit großer „Erneut versuchen“-Taste statt weißem Bildschirm.
* **Zugänglich:** Große Schaltflächen (min. 48 dp), **Seiten-Zoom** A−/A+ von
  25 % bis 300 % (100 % = Normalmaß, bleibt gespeichert), TalkBack-Beschriftungen,
  dunkler Modus, Desktop-Umschalter.
* **Praktisch:** Login bleibt erhalten, Datei-Upload funktioniert, Downloads
  laufen über den Download-Manager, Links teilen, im Browser öffnen, Zurück-Taste
  navigiert in der Seitengeschichte. Popups (z. B. Anmeldung) öffnen im selben Fenster.
* **Sicher:** Ungültige TLS-Zertifikate werden strikt abgelehnt (fail-closed),
  nur HTTPS, kein Standortzugriff, kein Dateizugriff aus dem Web.

## Kompatibilität

| | |
|---|---|
| Läuft ab | **Android 4.4** (API 19) |
| Optimiert für | **bis Android 7** (API 24/25) – Referenzgerät: Galaxy S8 mit Android 7 |
| läuft auch | auf neueren Geräten (bewusst **kein** `maxSdkVersion`) – Referenzgerät: Galaxy XCover 5 mit Android 14; ab Android 8 sorgt der integrierte Renderer-Crash-Schutz für extra Stabilität |
| Technik | reine WebView-App, keine externen Bibliotheken, APK ≈ 160 KB |
| Dex-Format | 035 (kompatibel bis runter zu Android 4.x) |
| Signatur | v1 + v2 + v3 (auf Android 7 **und** Android 14 installierbar) |

> Tipp für Android 5–7: Die WebView-Komponente lässt sich über den Play Store
> („Android-System-WebView“) aktualisieren – das bringt oft einen spürbaren
> Geschwindigkeits- und Stabilitätsgewinn.

## Installation

1. Die APK laden: direkt aus diesem Repo (`release/arena-0.2.8.apk`)
   oder aus dem Chat-Anhang. Schild in der Leiste muss `0.2.8` zeigen.
   **0.2.7 und älter:** auf Android 14 (XCover 5) „nicht kompatibel“, weil
   `uses-sdk` im APK fehlte. 0.2.8 hat minSdk 19 / targetSdk 28.
   Startet auf **https://arena.ai/code**. Zoom-Leiste Standard **aus** (Aa blass) —
   Anzeige wie Chrome, ohne Größen-Vorgabe. Aa schaltet A−/A+ ein.
2. Auf dem Gerät öffnen – beim ersten Mal „Installation aus unbekannten Quellen“
   für den Browser/die Dateiverwaltung erlauben.
3. Fertig. Wer möchte, kann beim ersten Start den Launcher-Dialog nutzen, um
   arena.ai-Links standardmäßig in der App zu öffnen.

**Wichtig für Updates:** Jede neue Version muss mit demselben Schlüssel signiert
sein. Der Debug-Schlüssel liegt nach dem ersten Build in `keystore/` – bitte
diesen Ordner aufbewahren (sonst ist ein Update nur mit Deinstallation möglich).
Der Ordner ist aus gutem Grund **nicht** in Git enthalten.

## Bauen – zwei Wege

### A) Android Studio (empfohlen für normale Entwicklung)
Projekt in Android Studio öffnen (Gradle, AGP 8.x) → *Run* / *Build APK*.
Benötigt ein installiertes Android-SDK (compileSdk 34).

### B) `build.sh` – ganz ohne Android Studio
Das Skript baut das APK mit einer minimalistischen Werkzeugkette:

```bash
JAVA=<pfad-zu-java> \
JAVAC_JAR=<pfad-zu-tools.jar> \
AAPT2=<pfad-zu-aapt2> \
ANDROID_JAR=<pfad-zu-android.jar> \
SOOT_DIR=<ordner-mit-soot-jars> \
APKSIGN_LIB=<pfad-zu-apk_sign_ts/dist/index.js> \
./build.sh
```

| Werkzeug | Zweck | Quelle |
|---|---|---|
| Java-Laufzeit | alles ausführen | z. B. PyPI-Paket `jdk4py` (Temurin) |
| `tools.jar` (OpenJDK 8) | `com.sun.tools.javac.Main` – Java-Kompilierung | npm-Paket `dataslope-tools-jar` |
| `aapt2` | Ressourcen/Manifest | PyPI-Paket `aapt2` (Linux-Binary) |
| `android.jar` (API 28) | Android-Plattform-Stubs | GitHub `Sable/android-platforms` |
| Soot 4.3.0 + ASM/Guava u. a. | Klassen → `classes.dex` | Maven-Artefakte (z. B. Git-Bäume mit `maven-repository`) |
| `apk_sign_ts` (Node) | Signierung v1+v2+v3 | npm |
| `openssl` | Debug-Schlüssel | System |

Ablauf in `build.sh`: Manifest → `aapt2 compile/link` (inkl. `R.java`) →
`javac` → `soot -f dex` → `classes.dex` einpacken → signieren. Danach liegt das
Ergebnis in `build/arena-<version>.apk`.

## Projektstruktur

```
app/src/main/
├── AndroidManifest.xml          # INTERNET + ACCESS_NETWORK_STATE, arena.ai-Links
├── java/de/tuktuck/arena/
│   ├── MainActivity.java        # Oberfläche, Menü, Schriftgröße, Crash-Recovery
│   ├── ArenaWebViewClient.java  # Ladeverhalten, Fehlerseiten, SSL fail-closed
│   └── ArenaChromeClient.java   # Upload, Vollbild, Popups
├── res/                         # Werkzeugleiste, Menü, Icons, Farben
└── assets/html/                 # Offline- & Fehlerseite (mit „Erneut versuchen“)
scripts/sign-apk.mjs             # Signierung (v1+v2+v3) via apk_sign_ts
build.sh                         # APK-Build ohne Android Studio
release/arena-<version>.apk      # fertige, signierte APK zum Sideloaden
```

## Hinweise

* Beim ersten Build erzeugt `build.sh` unter `keystore/` ein Debug-Zertifikat
  (10 000 Tage gültig). Für Folgeversionen bitte dieselben Dateien verwenden.
* Die App speichert nur lokale Einstellungen (Schriftgröße, Modi) und die
  normalen Web-Login-Daten von arena.ai – es gibt kein eigenes Tracking.
* „Desktop-Website“ schaltet den User-Agent um, falls arena.ai auf dem Handy
  eine zu eingeschränkte Ansicht liefert.
