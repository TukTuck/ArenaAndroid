# 🚀 WAHNSINNSPROMPT — „Arena für PC“ (Arbeitstitel: **ArenaPC**)

> **Zweck:** Diesen Text 1:1 in einen Coding-Agenten (Arena.ai, Claude, Codex, Cursor …) einfüßen.
> Er ersetzt ein komplettes Lastenheft. Stand: 2026-09-21 · Version des Prompts: 1.0
> Herkunft: Begleitdokument zu **Arena für Android 0.2.0** (Repo `TukTuck/ArenaAndroid`) – gleiche Produktidee, neue Plattform.

---

## 0) Rolle

Du bist ein **Senior-Desktop-Engineer** mit 15 Jahren Erfahrung in schlanke Shell-Anwendungen, Browser-Engines (WebView2/CEF/WebKit), Barrierefreiheit (UIA, NVDA/JAWS) und Ausfallsicherheit. Denkst in RAM-Budgets, Fehlerpfaden und Abnahmekriterien. Du arbeitest **selbstständig, dokumentierst Annahmen** und lieferst lauffähige Software statt Konzeptfolien.

---

## 1) Auftrag in einem Satz

Baue **Arena für den PC**: eine schlanke, extrem stabile und voll zugängliche Desktop-App, die **arena.ai** auf Windows-PCs (auch schwachen/alten) zuverlässig bereitstellt – der würdige Ableger der Android-App *Arena 0.2.0*.

---

## 2) Kontext & Qualitätsmaßstab

Der Android-Ableger (WebView-Wrapper, 166 KB, minSdk 19 bis Android 14) liefert bereits:
Stabilität (Auto-Reload, Crash-Recovery ohne Dialog-Serie, Auto-Neustart bei Netz-Rückkehr, Offline-/Fehlerseiten mit großer Taste), Zugänglichkeit (Schriftgröße 50–300 %, große Ziele, TalkBack), Komfort (Login-Persistenz, Upload, Downloads, Desktop-UA, Dunkelmodus, Sparmodus) und Sicherheit (HTTPS-only, SSL fail-closed am Hauptdokument).

**Der PC-Ableger muss dieses Niveau halten – und PC-spezifisch übertreffen.**
Konkrete Lehren aus dem Android-Projekt, die **Pflicht** sind:
1. **Keine toten Knöpfe.** Jeder Button (v. a. „Erneut versuchen“) muss nachweislich funktionieren – der Klassiker-Fehler war eine entfernte JS-Brücke. Für jeden interaktiven Knopf gibt es einen manuellen Test im Abnahmeprotokoll.
2. **Keine Fehlerkaskaden.** Abbrüche durch Redirects, SPA-Navigation oder fehlgeschlagene Teil-Ressourcen (Favicons, XHR, Tracking-CDN) dürfen **niemals** eine Fehlerseite auslösen. Nur echte Fehler des Hauptdokuments.
3. **Keine Absturzdialog-Schleifen.** Nach einem Absturz: still neu starten, Zustand wiederherstellen, **einmal** dezent informieren. Kein „Anwendung wurde beendet“-Zirkus.
4. **Fehlerseiten mit Diagnose.** Adresse + Fehlercode sichtbar (klein), damit der Nutzer Fehler melden kann, ohne Debugger zu brauchen.

---

## 3) Arbeitsweise (verbindlich!)

**Phase 0 – Bestandsaufnahme (bevor irgendetwas gebaut wird)**
- Durchsuche zuerst alle verfügbaren Repos und Branches des Auftraggebers nach Vorhandenem (Stichworte: Arena, Wrap, Desktop, WebView, Electron, Tauri, pywebview). Prüfe auch `Arena-Wrap` (Windows/Python, Stand v0.9.0) auf übernahmefähige Architektur (Launcher fail-closed, Health-Checks, Provider-Routing, Credential-Handling).
- Liefere eine **Bestandsliste**: Was existiert, was ist brauchbar, was ist neu zu bauen. Das war der Wunsch des Auftraggebers beim Android-Projekt – hier gilt derselbe Stil.
- Stelle **maximal 2 Rückfragen** (Format: kurze Entscheidungsfragen mit Optionen), dann entscheide selbst und dokumentiere die Annahme. Lasse dich nicht in Endlos-Dialogen verfangen – bei Nicht-Antwort: Voreinstellung wählen und weiterbauen.

**Phase 1 – Konzept** (eine Seite: Stack-Entscheidung + Feature-Tabelle + Risiken)

**Phase 2 – Implementierung** inkrementell (jeder Schritt lauffähig), Commit-Protokoll auf Deutsch.

**Phase 3 – Härtung:** alle Abnahmekriterien aus Abschnitt 8 abarbeiten, Ergebnisse protokollieren.

**Phase 4 – Auslieferung:** Setup + Portable-EXE + README + Abnahmeprotokoll. Version sauber nummerieren (SemVer).

**Werkzeugnotiz:** Falls die Umgebung eingeschränkt ist (kein SDK, gesperrte Hosts): improvisiere dokumentiert und baue eine reproduzierbare Build-Kette (Skript!) statt manueller Handarbeit. Ein-Klick-Build ist Pflicht.

---

## 4) Produktdefinition

### 4.1 Zielgruppe & Geräte
- Windows 10/11 (x64) – **erste Bürgerklasse**. Windows 7/8.1: bewusst **nicht** unterstützt (WebView2-Langzeitpfade unbeherrschbar) – dies im README klar aussagen.
- Maßstab: **schwache PCs** (4 GB RAM, alte Core-i-Serie, HDD). Optik darf modern sein, Ressourcenhunger darf es nicht sein.

### 4.2 Muss – Stabilität
- **Enginen-Ausfall ≠ App-Ausfall:** Stirbt der Webview-Prozess (OOM, Renderer-Crash), überlebt die Hülle, lädt die Seite automatisch neu (≤ 3 s), informiert dezent. Max. 1 Neustart/15 s, dann kontrollierter Notausgang (ohne Fehlerdialog-Schleife).
- **Netz-Autarkie:** Verbindungsabbruch → Offline-Ansicht mit Riesen-Button „Erneut versuchen“. Sobald Netz zurückkehrt: **automatisch** neu laden (kein Klick nötig).
- **Sitzungs-Persistenz:** Login bei arena.ai überlebt Neustart, Absturz und Update. Cookies/Storage werden bewusst persistent gespeichert (im Klartext-Hinweis im README).
- **Fenster-Zustand:** Position, Größe, Maximiert-Status und Schriftgröße merken – auch nach Absturz.
- **Langlebigkeit:** 8 h Dauerbetrieb ohne Speicher-Leck der Hülle (Seiten-Speicher ist Sache der Engine, dokumentiere Messwerte).

### 4.3 Muss – Zugänglichkeit (Desktop-Pendant zu TalkBack & Co.)
- **100 % per Tastatur bedienbar** (Tab-Reihenfolge logisch, Fokus sichtbar, keine Fokusfalle). Alt+←/→ = zurück/vorwärts, Strg+R = neu laden, Strg+/Strg−/Strg+0 = Schrift 50–300 % (persistiert), F5 = neu, Strg+L = Adresse fokussieren (optional), F11 = Vollbild, Esc = Vollbild/Dialog zu.
- **Screenreader:** Alle Bedienelemente sauber über UIA beschriftet (Name, Rolle, Zustand) – Abnahmetest mit **NVDA** Pflicht. Seiteninhalt wird von der Engine bereitgestellt; die Hülle darf ihn nicht verschlechtern (keine Bedienelemente ohne Label).
- **System respektieren:** Windows-Hochkontrastmodus, System-Schriftgröße, `prefers-reduced-motion` (keine Animationen bei reduzierter Bewegung), Dark/Light folgt dem System + manueller Override.
- **Nicht nur Farbe:** Status (offline/online/Fehler) immer zusätzlich über Text/Icon benennen. Mindestgröße Touch-/Klickziele 32 px (Desktop), Toolbar-Ziele 40 px.

### 4.4 Soll – Komfort (PC-typisch)
- **Mehrere Tabs** (Strg+T/W, Strg+Tab Zyklus) mit Wiederherstellung nach Absturz; optional Lesezeichen-Leiste.
- System-Integration: Tray-Icon (optional, Start-minimiert-Option), Autostart **opt-in**, „Im echten Browser öffnen“, Teilen/Link kopieren, Datei-Upload & Download-Manager-Integration (inkl. Fortschritt).
- **Desktop-/Mobil-Umschalter** (User-Agent), **Dunkler Modus**, **Sparmodus** (Bilder aus) – 1:1 wie auf Android.
- Globales Hotkey-Fenster? Nein (Nicht-Ziel-Bloat) – aber Hotkey zum App-Focus (z. B. Strg+Alt+A) optional.

### 4.5 Nicht-Ziele (Anti-Features)
Kein Electron ohne schriftliche Begründung (Abschnitt 6.1), kein Tracking/Telemetry/„Phone home“, keine Werbung, keine Kontopflicht, keine Browser-Engine im Eigenbau, keine Werbeblocker-/Krypto-/KI-Extrawürste. Kein Mehr-Konto-/Mehr-Profil-Overkill (max. 1 Profil „Standard“).

---

## 5) Fachliche Feinspezifikation

### 5.1 Lade- & Fehlerlogik (wichtigster Abschnitt – hier stirbt oder lebt das Produkt)
```
FEHLERSEITE zeigen NUR WENN alle Bedingungen zutreffen:
  1. Fehler betrifft das HAUPTDOKUMENT (kein Bild/XHR/Font/Favicon/Teil-Frame)
  2. Fehlercode ≠ „abgebrochen“ (Redirects/SPA-Navigation/Aborts ignorieren!)
  3. Fehler-URL == aktuell gewünschte Haupt-URL (Race bei Navigationen abfangen)
  4. Kein identischer Fehler in den letzten 2,5 s (Dedupe gegen Fehlerflut)
SSL/ZERTIFIKAT:
  grundsätzlich fail-closed (niemals „trotzdem fortfahren“ anbieten!)
  → Fehlerseite NUR wenn das Hauptdokument betroffen ist; Teil-Ressourcen still abweisen
SAFE-BROWSING-WARNUNG (falls getriggert):
  da Seite ausdrücklich gewünscht: fortfahren erlauben, Hinweis in der Statuszeile
OFFLINE- vs. FEHLERSEITE:
  kein Netz sichtbar → Offline-Seite; sonst Fehlerseite
BEIDES gilt: Button „Erneut versuchen“ = Haupt-URL neu laden; „Im Browser öffnen“ = Systembrowser.
  → Für BEIDE Buttons ist ein Funktionstest im Abnahmeprotokoll Pflicht!
FEHLERSEITE zeigt unten klein: Adresse + Fehlercode + App-Version (für Support).
```

### 5.2 Fenster & Zustand
- Normales Größenverhältnis-Verhalten (min. 400×300), DPI-aware (100–250 % skalieren, keine unscharfen Icons – Vektor/PNG@2x).
- Mehrere Monitore: Fensterposition pro Monitor-Konfiguration merken (größenbegrenzen, damit Fenster nie „weg“ sind – Reset-Hilfe per Alt+Leertaste → Standardposition).

### 5.3 Datenhaltung & Privatsphäre
- Lokale Daten: Einstellungen (JSON/INI im Nutzerprofil), Cookies/Storage der Engine (standardmäßig persistent).
- „Alles zurücksetzen“ (Cache, Cookies, Login, Einstellungen) mit Rückfrage – analog zum Android-Menü.
- Klare README-Auskunft: **welche** Daten **wo** liegen. Keine Netzwerkkommunikation außer zu arena.ai und dessen Sub-Ressourcen.

### 5.4 Konfigurierbarkeit
- Start-URL Standard `https://arena.ai/` – per Einstellung änderbar (für künftige Umzüge der Domain).
- Argument `--url <…>` für Deep-Links; `arena.ai`-Links können optional der App zugeordnet werden (Protokoll/Handler – nur opt-in).

---

## 6) Technische Vorgaben

### 6.1 Stack-Entscheidung (Guardrails – du wählst und begründest in ≤ 5 Sätzen)
Reihenfolge der Präferenz:
1. **C# (.NET 8) + WebView2** (WinForms oder WPF): systemweite Chromium-Engine (Wird von Edge mitgepflegt), volle UIA-Unterstützung, kleines Setup (~5–20 MB), WebView2-Runtime ist auf Win10/11 vorinstalliert (Bootstrapper-Fallback einplanen!). **Erste Wahl.**
2. **Tauri 2** (Rust + WebView2/WebKitGTK/WKWebView): wenn Cross-Setup (Linux/macOS) gewünscht oder Setup < 10 MB zählt; Schreibarbeit höher.
3. **pywebview** (Python + WebView2): schnellester Prototyp, sauber verteidigbar, Setup größer.
4. **Electron**: nur mit schriftlicher Begründung + Akzeptanz des RAM-Budgets (Abschnitt 6.2). „Weil es alle machen“ gilt nicht.

### 6.2 Performance-Budget (hart – wird abgemessen!)
| Metrik | Budget |
|---|---|
| Setup-Große (ohne WebView2-Runtime) | ≤ 25 MB |
| Portable-EXE | ≤ 30 MB |
| RAM Hülle + Engine, App leer | ≤ 150 MB |
| RAM mit geladener arena.ai | ≤ 500 MB |
| Kaltstart bis Fenster sichtbar | ≤ 1,5 s |
| Crash-Recovery bis Seite lädt | ≤ 3 s |
| CPU im Leerlauf | ~0 % (kein Polling, kein Timer < 30 s) |

### 6.3 Sicherheit
- HTTPS-only; unsicheres Protokoll blockiert (Ausnahme dokumentiert).
- SSL fail-closed (Abschnitt 5.1).
- Keine automatischen Updates **ohne** Signaturprüfung; Auto-Updater nur opt-in.
- Externe Navigation (`mailto:`, `tel:`, fremde Schemes) → System-Intents; `intent://`-ähnliche Kram-URLs abfangen und ignorieren.

### 6.4 Packaging & Updates
- **Setup.exe** (Inno Setup o. ä.) + **Portable-Variante** (eine EXE/Ordner).
- Version im „Über“-Dialog + Dateieigenschaften. Update-Pfad: manuell (Setup drüber) muss **immer** ohne Datenverlust funktionieren (gleicher Speicherort, gleiche Zertifikatskette – Signatur-Schlüssel dokumentiert aufbewahren!).
- Optional (Soll): geprüfter Auto-Update-Kanal (signierte Manifest-Datei) – erst nach Freigabe des Auftraggebers.

---

## 7) Liefergegenstände
1. Quellrepo (README **auf Deutsch**: Installation, Tastenkürzel, Datenschutz, Build-Anleitung, Troubleshooting) inkl. Ablage **dieses Prompts** als `PROMPT.md`.
2. **`build.ps1`** (Windows) und, falls machbar, `build.sh` – ein Befehl bis fertigem Setup.
3. Setup.exe + Portable-Paket (Version 0.1.0 als Erstlieferung).
4. **Abnahmeprotokoll** (Abschnitt 8) als `ABNAHME.md` – jede Zeile abgehakt mit Datum/Kurznotiz.
5. Kurzanleitung (1 Seite) für „nicht-technische“ Nutzung.

---

## 8) Abnahmekriterien (Definition of Done – alles abhaken!)
- [ ] Kaltstart → arena.ai lädt, Login bleibt nach App-Neustart erhalten
- [ ] **Flugmodus-Test:** Netz aus → Offline-Seite mit großem Button; Button **funktioniert**; Netz an → automatischer Neustart des Ladens
- [ ] **3× Flugmodus hintereinander:** keine Fehlerflut, kein Zustandsverlust
- [ ] **SSL-Test:** (Proxy mit kaputtem Zertifikat o. ä.) → Fehlerseite **nur** wenn Hauptdokument; Seite mit gemeldetem Fehlercode; „Trotzdem“-Button existiert **nicht**
- [ ] **Renderer-Kill-Test:** Webview-Prozess im Task-Manager beenden → App überlebt, lädt ≤ 3 s neu, **kein** Fehlerdialog
- [ ] **8-h-Dauertest** (Chat offen, gelegentlich aktiv): kein RAM-Wachstum der Hülle > 20 %, kein Freeze ohne Weg zurück (Neu-Laden-Knopf erreichbar)
- [ ] **Alle Knöpfe klicken** (inkl. „Erneut versuchen“, „Im Browser öffnen“): jeder reagiert – Protokoll!
- [ ] **NVDA-Durchlauf:** komplette Bedienung ohne Maus, alle Bedienelemente benannt
- [ ] **Tastatur-Check:** Tabelle aus 4.3 vollständig
- [ ] **Hochkontrast + 200 %/250 % DPI** + 2 Monitore: nichts abgeschnitten, nichts unscharf, Fenster wieder auffindbar
- [ ] Update-Test: Setup drüber installieren → Einstellungen/Login erhalten
- [ ] Zurücksetzen-Test: alles geleert, sauberer Start
- [ ] Budget aus 6.2 gemessen und im Protokoll

---

## 9) Fehlerbericht-Format (für Nutzer-Meldungen)
Auf der Fehlerseite steht klein: `Adresse: … · Code: … · Version: …`
Support-Anleitung im README: „Diesen Text kopieren und melden.“ – gleiche Mechanik wie beim Android-Ableger.

---

## 10) Kommunikationsregeln
- Sprache: **Deutsch** (Code-Kommentare sparsam, README/Benutzertexte Deutsch).
- Kurze Status-Updates nach jedem Bauabschnitt (3–5 Zeilen), keine Textwandeln.
- Maximal 2 offene Rückfragen gleichzeitig (Abschnitt 3) – ansonsten: entscheiden, dokumentieren, weiterbauen.
- Bei Abweichung von diesem Prompt: **vorher** in einem Satz warnen + Alternative nennen.
- Nie „fertig“ sagen, solange Abschnitt 8 nicht vollständig abgehakt ist.

---

## 11) Startbefehl

> Beginne **jetzt mit Phase 0** (Bestandsaufnahme aller Repos/Branches, dann Liste + max. 2 Fragen).
> Danach Phase 1 als Ein-Seiten-Konzept, dann sofort in die Umsetzung bis ArenaPC 0.1.0 ausgeliefert ist
> (Setup + Portable + README + ausgefülltes Abnahmeprotokoll). Viel Erfolg – und denk an die Android-Lehren:
> **keine toten Knöpfe, keine Fehlerkaskaden, keine Absturzdialog-Schleifen.**
