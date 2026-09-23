# DOKUMENTATION — Arena für Android

**Projekt:** Arena für Android · stabiler, zugänglicher WebView-Client für arena.ai
**Aktueller Stand:** **0.2.7** (`versionCode` 10) auf Branch `arena/01a0cd80-arenaandroid` (PR #2 **offen, nicht mergen**)
**main:** 0.2.0 (PR #1, Commit `fefbe97`) — bewusst nicht nachgezogen, weil Merge die Coding-Session beendet
**Erstellt:** 2026-09-21 · weitergeführt 2026-09-23
**Autor der Umsetzung:** Arena.ai Agent Mode (auf Basis des Auftragstextes des Projektinhabers)
**Repo:** TukTuck/ArenaAndroid

---

## 1. Ausgangslage & Auftrag

Der Projektinhaber betreibt arena.ai auf **zwei Handys**, die mit der Website nicht klarkommen:

| Gerät | Android | Problem |
|---|---|---|
| Samsung Galaxy S8 | 7 | Seite lädt schlecht, UI kollabiert/hängt ständig |
| Samsung Galaxy XCover 5 | 14 | ebenfalls überfordert |

**Auftrag (Original sinngemäß):** „Eine App für max Android 7, die die Seite Arena.ai stabil und zugänglich auf Android bringt. Zuerst Repos/Branches nach Vorhandenem durchsuchen und auflisten."

Zusätzliche Randnotiz, die das Projekt charakterisiert: Der Projektinhaber nutzte Arena.ai **auf dem Handy selbst** – dort fiel sogar das Frage-Dialog-UI des Agenten aus („musste neu laden, kam nur eine Fehlermeldung"). Das Problem der Session war buchstäblich das Produktproblem. Deshalb: **kein JavaScript-Ballast, keine toten Knöpfe.**

---

## 2. Phase 0 — Bestandsaufnahme (bevor irgendetwas gebaut wurde)

Durchsucht wurden **~44 Repos** des GitHub-Kontos `TukTuck` inklusive aller Branches.

| Repo | Befund | Entscheidung |
|---|---|---|
| `TukTuck/ArenaAndroid` | leer (nur README-Zeile) | wird zum Projekt-Repo |
| `TukTuck/mwco` | „Android-App" deklariert, **null Code** (nur LICENSE+.gitignore) | Platzhalter, nicht nutzbar |
| `TukTuck/Arena-Wrap` | Arena-**Desktop**-Client (Windows/Python v0.9.0) | kein Android, aber Architektur-Vorbild (fail-closed Launcher, Health-Checks, Routing, Credential-Handling) |
| `TukTuck/openclaw-android-assistant` | echtes Android-Projekt (Kotlin/Gradle/WebView), aber Fork für AI-Agenten | kein Arena-Client, Musterquelle für APK-/WebView-Struktur |
| `TukTuck/Bad-Wolf` | OmniRoute+Schaltwerk, darin `lmarena`-Provider (Cookie/Stream/ELO) | serverseitige Arena-API-Erfahrung, kein UI |
| Rest (KSP, Electribe, Tools, Forks …) | themenfremd | verworfen |

**Ergebnis:** Es existiert **nichts** für Android. → Komplettneubau in `ArenaAndroid`.
Der Befund wurde zuerst als Liste ausgewiesen (genau wie gewünscht), dann wurde gebaut.

---

## 3. Produktentscheidungen und ihre Begründungen

### P1 — Schlanker nativer WebView-Wrapper (kein Hybrid-Framework)
**Warum:** Das Problem ist die **schwere Website auf schwacher Hardware**. Jede zusätzliche Schicht (React Native, Flutter, Cordova, gar Electron-Ansätze) kostet Startzeit, RAM und APK-Größe. Eine WebView-Hülle aus ~800 Zeilen Java maximiert den Anteil der Ressourcen, die arena.ai bekommt. Ergebnis: **166 KB APK** statt 50–150 MB.

### P2 — minSdk 19, bewusst KEIN `maxSdkVersion`, Ziel-Ära bis Android 7
**Warum:** „max Android 7" wurde so interpretiert: **das neue Gerät (S8) hat höchstens Android 7** – die App muss also dort laufen. minSdk 19 (Android 4.4) deckt alles ab „bis 7" ab und darüber hinaus gleich mit (XCover 5, Android 14) – eine APK für beide Handys. Eine `maxSdkVersion`-Sperre hätte das XCover ausgesperrt; bewusst weggelassen, im README erklärt.

### P3 — Reines Java + Framework, NULL externe Bibliotheken
**Warum:** Jede Dependency (auch AndroidX/AppCompat) ist auf alten Geräten Ballast und Angriffsfläche für Build-Probleme. Framework-Widgets + eigene Form-Drawables reichen für eine Toolbar-App vollständig. APK bleibt winzig, der Build braucht kein Dependency-Management (was in der Sandbox Gold wert war, siehe Abschnitt 5).

### P4 — targetSdk 28 (nicht höher, nicht tiefer)
**Warum:** Android 14 **blockiert** die Installation von APKs mit `targetSdk < 23` – 28 ist also installierbar und bleibt zugleich frei von modernen Zwängen (Background-Limits, Scoped Storage, Notification-Pflichten), die eine WebView-App nur bremsen. Für Sideloading (kein Play Store) der ideale Kompromiss.

### P5 — Dex-Format 035 (klassisch)
**Warum:** 035 ist das älteste, universellste Dex-Format – läuft bis runter zu Android 4.x. Moderne D8/R8-Ausgabe (038/039) hätte das S8 mit Android 7 unnötig in Gefahr gebracht (Dex-Version-Prüfung auf alten Runtimes). Über den gewählten Umweg (Soot) bekamen wir 035 „gratis".

### P6 — Signatur v1+v2+v3 mit eigenem Debug-Zertifikat (10.000 Tage)
**Warum:** v1 brauchen alte Geräte, v2/v3 wollen neue (Android 7+ bevorzugt v2). Beide Signaturen = auf beiden Handys installierbar. Eigenes Zertifikat in `keystore/` (nicht im Git!): nur mit demselben Schlüssel wird ein Update ohne Deinstallation möglich – deshalb ausdrücklich „aufbewahren" im README.

### P7 — Feature-Set, abgeleitet aus den echten Schmerzen
| Feature | Begründung (aus dem Nutzer-Alltag) |
|---|---|
| Auto-Reload + Crash-Recovery ohne Dialog-Serie | „alles ist ständig kollabiert und aufgehangen" |
| Offline-/Fehlerseite mit Riesen-Taste | „Fehlermeldung nach der anderen" statt weißem Bildschirm |
| Seiten-Zoom A−/A+ 25–300 % (ab 0.2.3, CSS zoom) | 100 % = Normalmaß; A− verkleinert die **ganze** Seite, nicht nur Schrift |
| 48-dp-Tasten, TalkBack-Labels | „zugänglich" wörtlich genommen |
| Sparmodus (Bilder aus) | S8-RAM schonen, falls die Seite selbst zu schwer ist |
| Desktop-UA-Umschalter | mobile Seitenvarianten sind manchmal die kaputten Varianten |
| Dunkler Modus (Invert-Filter) | funktioniert auch auf alter WebView ohne Seiten-CSS-Kenntnisse |
| Login-Persistenz, Upload, Downloads, Teilen | muss „eine echte App" sein, kein Screenshot-Viewer |
| Popups (OAuth-Login) im selben Fenster | sonst scheitert die Anmeldung im Wrapper |
| SSL fail-closed | Sicherheit schlägt Bequemlichkeit |
| Fehlerseite mit Adresse+Code klein | Nutzer kann exakt Fehler melden, ohne Debugger |
| Mini-Versionsschild in der Werkzeugleiste (ab 0.2.2-b) | Sideload-Tester erkennt die installierte APK ohne Menü/Reload |

### P8 — UI-Sprache Deutsch, Layout werkzeugleisten-artig
**Warum:** Zielgruppe ist deutschsprachig; eine Toolbar aus Zurück/Vorwärts/Neuladen/**Versionsschild**/A−/A+/Menü ist auf alten Samsungs sofort verständlich.

### P9 — Jede lieferbare Version enthält eine signierte APK in `release/`
**Warum (Auftrag 2026-09-23):** „bitte die apk auch immer miterstellen, nicht nur eine skript anleitung“ — Sideload-Tests gehen sonst nur über Selbstbauen. Build-Kette bleibt `build.sh`; das Artefakt liegt als `release/arena-<versionName>.apk` im Repo.

---

## 4. Versionen & Fehlerchronik (inkl. „Warum die Fehler entstanden")

### 0.1.0 (18:50 UTC) — Erstlieferung
Komplette App inkl. Build-Kette, ausgeliefert als `release/arena-0.1.0.apk`, Quellcode via PR #1 nach `main`.

### Nutzerbericht am Abend: „Fehlermeldung nach der anderen, sehr krass"
Root-Cause-Analyse im Code – **drei echte Bugs**, alle in der Hülle:

**BUG 1 (Hauptursache) — Tote „Erneut versuchen"-Taste.**
*Was:* Die JS-Brücke (`ArenaApp`) wurde in `onPageStarted` **jedes Mal entfernt** – auch beim Laden der eigenen Fehlerseite, obwohl sie dort gerade gebraucht wird. Der Button rief `ArenaApp.retry()` auf und traf auf `undefined`.
*Folge:* Fehlerseite → Taste drücken → **nichts** → Nutzer drückt erneut → endlos „Fehlermeldung nach der Fehlermeldung".
*Fix:* Brücke wird nur noch bei **fremden** Seiten entfernt; auf lokalen Seiten (`file:///android_asset/…`) wird sie garantiert aktiviert (`onPageStarted` **und** `onPageFinished` als Sicherung).
*Lehre:* „Keine toten Knöpfe" – jeder Button bekommt einen Funktionstest.

**BUG 2 — Fehlerkaskaden durch zu empfindliche Fehlererkennung.**
*Was:* `onReceivedError` feuert bei Chromium auch für **Teilfehler** (Favicons, XHR, Tracking-CDN) und für **abgebrochene** Navigationen (ERR_ABORTED, Alltag bei Redirects & SPA-Routing). `onReceivedSslError` warf die Fehlerseite schon bei SSL-Mucken **einzelner** Werbe-Ressourcen.
*Folge:* Jede Seitenflöte ersetzte die App durch eine Fehlerseite.
*Fix (Abschnitt 5.1 der App-Spez ist daraus entstanden):* Fehlerseite **nur** wenn (a) Hauptdokument, (b) Fehlercode ≠ abgebrochen, (c) Fehler-URL = gewünschte Haupt-URL, (d) kein identischer Fehler < 2,5 s (Dedupe). SSL: fail-closed bleibt, aber nur Hauptdokument zeigt die Fehlerseite.

**BUG 3 — Doppelte Fehlerbehandlung + Absturzdialog-Schleife.**
*Was:* Ab API 23 rufen alte und neue `onReceivedError`-Signaturen beide aus (doppelte Seitenwechsel). Und der Crash-Handler rief den Default-Handler auf → Android zeigt „App wurde beendet" beim Wiederherstellungsversuch.
*Fix:* Alte Signatur deaktiviert ab API 23 (Neue übernimmt); Crash-Handler: stiller Neustart per `Process.killProcess` + 15-s-Schleifenschutz.

**Außerdem in 0.2.0 (18:59 UTC):** Auto-Reload bei Netz-Rückkehr (BroadcastReceiver), Sparmodus, Safe-Browsing-Blockade für arena.ai umgangen (`proceed(false)`), Fehlerseite mit Diagnosecode, `WebView.saveState` an API-28-Stubs angepasst.
`release/arena-0.2.0.apk`, SHA-256 `b158c83ce688244d277a1e4a5256e0493af703e8061163527ddc85d512c4e0b8`.

### Die vier Lehren (gehen in jedes Folgeprojekt)
1. Keine toten Knöpfe.
2. Keine Fehlerkaskaden (nur Hauptdokument-Fehler sind Fehler).
3. Keine Absturzdialog-Schleifen (still wiederherstellen).
4. Fehlerseiten mit Diagnose (Adresse + Code + Version).

### 0.2.1 (2026-09-23, Session `01a0cd80`) — Viewport-/Zoom-Versuch, **Regression**
**Auftrag:** Nachzug des lokalen Patches der vorigen Session (Doku + PROMPT-PC + Zoom-Heuristik), plus immer eine APK mitliefern.

**Was geändert wurde:**
- `JS_VIEWPORT_FIX` (Viewport-Meta) — im Endstand **ungenutzt** (nicht in `onPageFinished` aufgerufen).
- `WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING` + `setInitialScale(0)`.
- Beim Start: gespeicherter Zoom **&lt; 75 %** wurde zwangsweise auf 100 % gesetzt.
- `versionCode` 3 / `versionName` 0.2.1.
- `DOKUMENTATION.md` + `PROMPT-PC.md` nach Git.
- `release/arena-0.2.1.apk` (SHA-256 `572f4475c2631ed9701a4301f5daee0724a4ad59694ff41d46ce4bce850a9144`). Neuer Debug-Schlüssel, weil der 0.2.0-Key nicht im Git lag.

**Warum TEXT_AUTOSIZING:** Annahme, arena.ai sei auf alter WebView unleserlich / Layout kollabiere — Autosizing sollte Schrift „von allein“ lesbar machen.

**BUG 4 — Zoom tot.** Nutzerbericht: „ok jetzt funktioniert der zoom ganicht mehr“.
*Was:* A−/A+ rufen `WebSettings.setTextZoom()` auf. `TEXT_AUTOSIZING` berechnet Schriftgrößen selbst und **ignoriert** `setTextZoom` (bekanntes WebView-Verhalten). Zusätzlich machte der 75 %-Reset kleine Zoom-Stufen beim nächsten Start rückgängig.
*Folge:* Toast konnte noch „Schriftgröße: x %“ zeigen, die Seite änderte sich nicht.
*Lehre:* Schriftgröße nur über `setTextZoom` + `LayoutAlgorithm.NORMAL`. TEXT_AUTOSIZING und setTextZoom schließen sich aus.

### 0.2.2 (2026-09-23) — Zoom wiederhergestellt
**Was:**
- `TEXT_AUTOSIZING` und `setInitialScale(0)` entfernt, Layout `NORMAL`.
- 75 %-Zwangsreset entfernt (50–300 % bleiben gespeichert).
- `applyTextZoom()` nach jedem `onPageFinished` (manche WebViews setzen Zoom bei Navigation zurück).
- Zoom-Reset im Menü mit Toast.
- `versionCode` 4 / `versionName` 0.2.2.
- `release/arena-0.2.2.apk` (SHA-256 `b23ae4a4a976895eb557e1f6e8bca2901ed0d1c239d5a51abfa8cfb082a68b99`), **gleiche Signatur wie 0.2.1** (Update ohne Deinstallation, sofern 0.2.1 aus dieser Session).

**Warum nicht 0.2.3:** Auftrag: erst den Zoom-Schaden von 0.2.1 beheben, Versionsschild kommt als Patch-Letter.

### 0.2.2-b (2026-09-23) — Mini-Versionsschild in der Werkzeugleiste
**Auftrag (Original):** „bitte füge oben in unsere app leiste ein mini versions nummern schild ein. ich erkenne sonst nicht welche version geladen ist ohne neu zu laden, was genau wurde gemacht, und warum? mit in die doku, es muss alles hart genau mitgeloggt werden“ · Nachtrag: „das wird dann nur die 0.2.2.-b und danach gehen wir weiter mit 0.2.3“

**Was:**
- `TextView` `lbl_version` in `activity_main.xml`, zwischen Spacer und A−/A+, 10 sp, abgerundetes Schild (`version_badge_bg`).
- Text = `PackageInfo.versionName` zur Laufzeit (nie hardcodiert) — zeigt also genau die **installierte** APK.
- Tippen öffnet denselben „Über“-Dialog wie das Menü.
- TalkBack: `cd_version` / „App-Version“.
- `versionCode` 5 / `versionName` `0.2.2-b`.
- `release/arena-0.2.2-b.apk` (SHA-256 `a678d574b2d9ba56dea505e9096eaf6ccb9b5fc1c1ee05a5f300bf5293107aaf`), gleiche Signatur wie 0.2.1/0.2.2.

**Warum ein sichtbares Schild statt nur „Über“:** Beim Sideloaden mehrerer APKs hintereinander (0.2.0 / 0.2.1 / 0.2.2) war ohne Reload/Menü nicht erkennbar, welche Binary wirklich läuft. Das Schild ist der Test-Kanal.

**Nächste Versionsnummer laut Auftrag:** 0.2.3.

### 0.2.3 (2026-09-23) — Seiten-Zoom statt Schriftzoom
**Auftrag (Original):** „okay zoom funktioniert wieder, aber halt immernoch alles viel zu groß, warum gibt es die funktion überhaupt? im android browser hat man das doch auch nicht zwingend. und wie kann es sein das bei einem zoomstufe von 100% alles vviiiiiiieeeeel zu gezomt ist, das sollte doch sozusagen 0 sein, wenn man schon nichtmal bis zur null runter schrauben kann?“

**Warum A−/A+ überhaupt existieren:** Ursprung 0.1.0/P7 — Barrierefreiheit (große Schrift auf S8). Der Android-Browser hat Pinch-Zoom, keine extra A−/A+-Leiste; die Tasten sind die Tastatur-/TalkBack-taugliche Entsprechung. Behalten, aber **umgebaut**, weil sie das eigentliche Problem (alles zu groß) nicht lösen konnten.

**Warum 100 % nicht „0 / ungezomt“ war:**
- `WebSettings.setTextZoom(100)` ist die **WebView-Normalgröße**, kein Extra-Zoom — und auch kein „aus“.
- 0 % Schrift/Zoom gibt es nicht: die Seite wäre unsichtbar. Untergrenze war 50 %.
- `setTextZoom` ändert **nur Schrift**. Buttons, Abstände, Chat-UI von arena.ai bleiben groß. Deshalb wirkte selbst 50 % noch „vvieel zu gezomt“.
- Samsung-Systemschrift (`Configuration.fontScale` > 1) legt sich oft **obendrauf** — 100 % in der App wirkte dann schon wie 130–150 %.

**BUG 5 — Zoom-Modell falsch (Schrift statt Seite).**
*Was:* A−/A+ = `setTextZoom`, Min 50 %.
*Folge:* 100 % = Browser-Default der WebView (oft schon groß); A− schrumpft nur Text, Layout bleibt riesig; kein Weg Richtung „alles kleiner wie rausgezoomt“.
*Fix:*
- A−/A+ setzen CSS `document.documentElement.style.zoom` (ganze Seite, wie Pinch).
- `setTextZoom` bleibt fest 100, damit sich beide nicht stapeln.
- Bereich **25–300 %**, Schritt 25. 25 % = ein Viertel der Seite. 0 % bewusst unmöglich.
- 100 % = Normalmaß minus System-`fontScale` (große Systemschrift wird herausgerechnet).
- Fehler-/Offlineseiten ohne CSS-Zoom (große Retry-Taste bleibt).
- Toast/Menü heißen „Zoom“, nicht „Schriftgröße“.

**Was nicht geändert wurde:** Pinch-Zoom der WebView bleibt an (`setSupportZoom`).

- `versionCode` 6 / `versionName` 0.2.3.
- `release/arena-0.2.3.apk` (SHA-256 `8c66c13c6bb8c3c5ace813000161490cb473c70e7b6e0523942f5fe08013f00a`), gleiche Signatur wie 0.2.1–0.2.2-b.

### 0.2.4 (2026-09-23) — Wrapper aus dem Weg, Stabilität/Tempo
**Auftrag (Original, sinngemäß):** Weg vom Zoom-Thema. Zoom darf bleiben. arena.ai hat selbst ein funktionierendes browserbasiertes Layout. Der Wrapper existiert, weil die Seite **unstabil** ist und ein Chat **teilweise 10 Minuten** braucht – nicht wegen Gerät oder Internet.

**Produktentscheidung P10:** Die Hülle gestaltet die Seite nicht um. Sie soll sie **nicht abstürzen lassen und nicht ausbremsen**. A−/A+ bleiben optional.

**Was in der Hülle 10-Minuten-Loads und Hänger begünstigt hat:**
1. **User-Agent mit `; wv` / `Version/4.0`:** SPAs erkennen WebView und gehen oft in langsamere oder defekte Codepfade. Desktop-UA war fest **Chrome/60** (2017) – Feature-Detection kann endlos polyfillen.
2. **`setLoadWithOverviewMode(true)`:** WebView zoomt lange Dokumente (Chats!) künstlich auf Bildschirmbreite. arena.ai hat eigenes Responsive. Overview = Extra-Layout auf jeder Nachricht.
3. **CSS-`zoom` + Click-Listener bei jedem `onPageFinished`:** Relayout und gestapelte Listener, obwohl der Nutzer Zoom nie angefasst hat.

**Fix:**
- Mobile-UA: `; wv` und `Version/4.0` entfernen, Chrome-Version der **echten** WebView behalten.
- Desktop-UA: dieselbe Chrome-Version auf Linux-Desktop-UA, nicht mehr Chrome/60.
- Overview-Mode **aus**.
- CSS-Zoom nur noch nach A−/A+ (`zoomTouched`); Default 100 % = kein JS.
- `JS_KEEP_BLANK` einmal pro Dokument (`window.__arenaKeepBlank`).
- Cache `LOAD_DEFAULT`, Hardware-Layer, `RenderPriority.HIGH`, `resumeTimers` in `onResume`.
- `shouldOverrideUrlLoading(WebResourceRequest)` für API 24+ (S8).

- `versionCode` 7 / `versionName` 0.2.4.
- `release/arena-0.2.4.apk` (SHA-256 `774418474aa7d05411cf15a249c4c129ac28715904dd0182ccc11ec22071096d`).

### 0.2.5 (2026-09-23) — Start-URL = Produkt `/code`, nicht Marketing `/`
**Beleg:** Screenshots XCover 5 Chrome, 22:36–22:37, Adresszeile `arena.ai/code`.
- Bild 1: Sidebar New Chat / Leaderboard / Search, Login `borny22@googlemail.com` — echte App.
- Bild 2: „What would you like to do?“ — mobile Produkt-UI, ordentlich skaliert.

**Zwei Gesichter derselben Domain (bestätigt):**
| URL | Was man sieht |
|---|---|
| `https://arena.ai/` | Marketing-Hero „Experience the frontier“, 4:3-artig, Get started — das zeigte **unsere App** |
| `https://arena.ai/code` | Produkt (Sidebar, Chat, Code) — das zeigt **Chrome auf dem X5** |

Die App startete bisher auf `/`. Deshalb wirkte es wie „eine andere, kaputte Version“, war aber nur die **Landing statt der App**.

**Fix:** `HOME_URL = https://arena.ai/code`. Bloße `/`-Links werden dorthin umgebogen. Andere Pfade (`/leaderboard`, Deep Links) unverändert.

- `versionCode` 8 / `versionName` 0.2.5.
- `release/arena-0.2.5.apk` (172579 B, SHA-256 `8a4a16e8064b0ead21e476d125ac424b0a2e144cdcaae5c77dfbb9aa550ad185`).

**Signatur:** Diese Session hatte den Debug-Schlüssel von 0.2.1–0.2.4 **nicht** (`keystore/` ist gitignoriert, Sandbox war frisch). 0.2.5 ist mit einem **neuen** Schlüssel signiert. Android lässt das nicht als Update zu → **einmal 0.2.4 deinstallieren**, dann 0.2.5 sideloaden. Danach wieder Updates ohne Deinstall, solange derselbe Schlüssel da ist.

**Nutzer zum Install:** Drag-and-Drop der APK reicht, kein extra Deinstall-Ritual.

### 0.2.6 (2026-09-23) — Viewport wie Chrome, Login erreichbar
**Beleg:** Screenshots 0.2.5, 22:54–22:57, Schild `0.2.5`.
1. Start: Cookie-Text riesig, nur Crop (goldenes „frontier“ an den Rändern).
2. A− auf 25 %: Cookie-Dialog komplett, Accept sichtbar.
3. Cookies angenommen: Hero „Experience the frontier“ + Composer — **ausgeloggt**.
4. Linke Sidebar: N…/Lead/S… abgeschnitten, „Get More Done With Agents“, Login darunter **nicht erreichbar**, kein Scroll.

**Was 0.2.5 nicht war:** `/code` ohne Login **ist** der Hero. Chrome-X5-Bilder waren eingeloggt (`borny22@…`). Die URL-Änderung war richtig, die Session nicht.

**BUG 6 — Desktop-Viewport, Overview aus.**
*Was:* 0.2.4 hat `setLoadWithOverviewMode(false)` gesetzt, ohne `width=device-width` zu erzwingen. WebView legt dann oft ~980–1440 px Desktop an und zeigt bei Scale 1 nur den **Crop**. 100 % wirkt 4× zu groß. CSS-`zoom: 25 %` macht den Crop sichtbar, ändert aber **nicht** `window.innerWidth` → die Seite bleibt im Desktop-Breakpoint, Sidebar-Overflow stirbt, Login unter dem Promo-Block.
*Fix:*
- Viewport-Meta `width=device-width, initial-scale=1` früh injizieren (`onProgress` ab 10 % + `onPageFinished`), nur wenn noch kein `device-width`.
- Overview **wieder an**: Sicherheitsnetz für den ersten Paint (Cookies), mit device-width Scale ≈ 1 (Chats nicht extra klein).
- Gespeicherten 25 %-Zoom von 0.2.5 **einmal** auf 100 % zurück (`zoom_cleared_026`). CSS-Zoom weiter nur nach A−/A+.
- Menü **Anmelden** klickt den Login-Knopf der Seite (kein Auto-Accept der Cookies).

Cookies bleiben sichtbar — das ist die Website, nicht unser Dialog. Nach Login sollte die Chrome-Produkt-UI kommen.

- `versionCode` 9 / `versionName` 0.2.6.
- `release/arena-0.2.6.apk` (175071 B, SHA-256 `958a47f771bdadcd156ff2f129ef468c68bc7a0076c712993197c94f9c72ca3d`).

### 0.2.7 (2026-09-23) — Zoom-Leiste Standard AUS, Anzeige wie Chrome
**Auftrag:** Zoom-Feature raus aus dem Default. Oben ein/aus. Aus = Standard. Nicht vorschreiben, wie groß die Seite sein soll — nehmen, was der Android-Chrome-Browser macht.

**Was Chrome macht (kein Rätsel):** Viewport-Meta **der Seite**, Pinch-Zoom, keine A−/A+, kein CSS-`zoom` auf `html`, kein `setTextZoom`, kein Overview-Fit. WebView braucht `setUseWideViewPort(true)`, sonst ignoriert sie die Meta der Seite.

**Fix:**
- Toolbar: **Aa** schaltet die Zoom-Leiste. Standard **aus** (blass). A−/A+ nur sichtbar, wenn an (blau).
- Leiste aus: kein CSS-zoom, kein Viewport-JS, kein `setTextZoom`, Overview **aus**. Pinch bleibt (wie Chrome).
- Leiste an: bisheriges A−/A+ (CSS-zoom 25–300 %).
- 0.2.6-Viewport-Inject und Overview-an sind damit wieder weg — das war „wir schreiben die Größe“.

Vergleichstest: dieselbe URL in **Chrome** und in der App (Schild `0.2.7`, Aa blass). Wenn die App anders ist als Chrome, Screenshot beider — dann ist es WebView≠Chrome, nicht unser Zoom.

- `versionCode` 10 / `versionName` 0.2.7.
- `release/arena-0.2.7.apk` (176590 B, SHA-256 `9e44e4226bbf7aade4732f65eb3bce7f6219f9cf21e6e901e7e7fc0829958f55`).

**Ehrliche Grenze:** Wenn Android-System-WebView auf dem S8 uralt ist, bleibt JS langsam – dann WebView im Play Store aktualisieren. Der Wrapper kann keine neue JS-Engine einbauen.

---

## 5. Die Build-Kette — Dokumentation einer Werkzeug-Odyssee

### 5.1 Umgebungs-Befund (Sandbox)
Vorhanden: git, gh, node 22, npm, python3, pip, ImageMagick, openssl. **Nicht** vorhanden: Java, Android-SDK, Gradle.
Netztest (curl): erreichbar **ausschließlich** `github.com`, `api.github.com`, `codeload.github.com`, `pypi.org`, `files.pythonhosted.org`, `registry.npmjs.org`. **Gesperrt:** alles von Google (dl.google.com, maven.google.com), repo1.maven.org, services.gradle.org, adoptium, objects.githubusercontent.com (Release-Assets!), raw.githubusercontent.com, archive.org, gitlab u. a.
*Folge:* Der reguläre Android-Weg (SDK-Tools + Gradle + AGP + Maven) ist tot. Notwendig war eine **Voll-Ersatzkette aus GitHub-Git-Bäumen, PyPI und npm**.

### 5.2 Endgültige Werkzeugkette (jedes Stück mit Herkunft)
| Bauteil | Werkzeug | Herkunft | Bemerkung |
|---|---|---|---|
| Java-Laufzeit | Temurin JRE 25.0.2 | PyPI `jdk4py` 25.0.2.1 | hat `java`+`keytool`, aber **kein** `javac`/`jarsigner` |
| Java-Kompilierung | javac 1.8.0_131 (`com.sun.tools.javac.Main`) | npm `dataslope-tools-jar` (OpenJDK-8-`tools.jar`) | läuft auf JRE 25, braucht zwingend `-bootclasspath android.jar` (sonst NPE – JDK-8-Suche nach rt.jar) |
| Ressourcen/Manifest | aapt2 2.19-7832930 (Linux-Binary) | PyPI `aapt2` 0.2.1 | `compile` + `link` inkl. `--java` (R.java) und `-A` (Assets) |
| Plattform-Stubs | android.jar API 28 (+25 als Reserve) | GitHub `Sable/android-platforms` (git sparse clone, echte Blobs) | 46 MB, quelloffen abrufbar |
| Klassen → classes.dex | **Soot 4.3.0** (`-f dex`) | Git-Tree `Fraunhofer-SIT/ECOOP2024-DynamicCallbackSummaries` (vollständiges `maven-repository` als **echte** Dateien!) | der kreative Kern: Soot ist kein DEX-Compiler, kann aber Klassen lesen und Dex 035 schreiben |
| Soot-Laufzeit | guava 31.1-jre, failureaccess 1.0.1, ASM 9.2 (asm/tree/analysis/commons/util), dexlib2 2.5.2, util 2.5.2, heros 1.2.3, axml 2.1.3, commons-cli 1.5.0, slf4j-api 1.7.36, protobuf-java 3.22.2, jasmin, polyglot, baksmali | derselbe Git-Tree | per `git/blobs`-API (base64) gezogen, nach jeder `NoClassDefFoundError` gezielt ergänzt |
| Signierung v1+v2+v3 | `apk_sign_ts` 1.0.1 (Node/ESM) | npm (+8 Deps) | Ersatz für fehlendes `jarsigner`; erzeugt JAR-v1 + APK-v2/v3 |
| Debug-Schlüssel | openssl (RSA 2048, 10.000 Tage) | System | Ablage `keystore/`, gitignoriert |
| Icons | ImageMagick `convert` + generiertes Launcher-Icon (Bildmodell) | — | Toolbar-Pfeile prozedural gezeichnet, Launcher-Icon generiert+beschnitten |

### 5.3 Aufgegebene Sackgassen (der Weg war das Ziel)
- **Release-Assets von GitHub** (fertige `d8.jar`, `apksigner.jar`, ganze Android-SDK-Trees in Repos): alles **Git-LFS-Pointer** (132-Byte-Dateien) → LFS-Medien-Host gesperrt → tot.
- **`d8-termux` (npm):** enthält `d8.jar` als **Dalvik-Dex** für Handys (`dalvikvm`-Wrapper) – auf dem Desktop-JVM unbrauchbar (Lesson learned: Paketnamen sind keine Funktionsversprechen).
- **`jdk4py`:** Java 25 läuft, aber das jlink-Image enthält weder `jdk.compiler` noch `jdk.jartool`.
- **ECJ-Jars in Git-Bäumen:** alle Treffer LFS-Pointer.
- **Maven-Mirrors** (aliyun, jitpack, google, central): alle gesperrt.
- **Entscheidung danach:** „Was kann `.class` lesen und Dex schreiben?" → **Soot** – und es klappte nach dem Ergänzen von `failureaccess` + ASM-Familie sauber. Ausgerechnet ein Forschungs-Archiv der Fraunhofer SIT (FlowDroid-Projekt) rettete den Build.

### 5.4 Build-Ablauf (`build.sh`, ein Befehl)
1. Manifest: `package`/`versionCode`/`versionName` injiziert (bei Gradle-Builds kommt das aus `build.gradle` – bewusst getrennt)
2. `aapt2 compile` → `aapt2 link` (erzeugt APK-Skelett inkl. binäres Manifest, `resources.arsc`, Assets + `R.java`)
3. `javac` (mit `android.jar` als Bootclasspath, `-source/target 1.7`) → Klassen
4. `soot.Main -src-prec class -f dex` → `classes.dex` (Format 035!)
5. `classes.dex` unkompriemt in den APK (Python-zipfile, STORED)
6. Signieren (`scripts/sign-apk.mjs`) mit `keystore/debug.key.pem`

### 5.5 Validierung des Ergebnisses (was wir messen konnten)
- Zip-Struktur: Manifest(binär), `resources.arsc`, 5 Mipmap-Dichten, Layout/Menu/Drawable-Varianten, Assets, `classes.dex` (STORED, `dex\n035`), `META-INF` (v1) – korrekt.
- Klassen-Strings im Dex: MainActivity, ArenaWebViewClient, ArenaChromeClient, JsBridge, Brückenname `ArenaApp` – vollständig.
- Signatur: v1-Signaturdateien **und** „APK Sig Block 42" (v2/v3) vorhanden.
- `aapt2 dump badging`: `package=de.tuktuck.arena versionCode=2 versionName=0.2.0`, genau 2 Permissions (INTERNET, ACCESS_NETWORK_STATE), Label „Arena", Icons korrekt referenziert.
- **Ehrliche Grenze:** Kein Emulator/kein Gerät in der Sandbox – Laufzeittests (Login, TalkBack, S8-WebView) blieben beim Nutzer. Deshalb die Diagnosezeile auf der Fehlerseite als Rückmelde-Kanal.

---

## 6. Repo-Inventar (was wofür ist)

| Pfad | Inhalt |
|---|---|
| `app/src/main/java/de/tuktuck/arena/` | MainActivity (UI, Menü, Crash-Recovery, JS-Brücke), ArenaWebViewClient (Fehlerlogik!), ArenaChromeClient (Upload, Vollbild, Popups) |
| `app/src/main/res/` | Toolbar-Layout (48-dp-Ziele), Menü (Desktop/Dunkelmodus/Sparmodus), Icons, Farben, DE+EN-Strings |
| `app/src/main/assets/html/` | offline.html + error.html (Riesen-Tasten, Diagnosezeile) |
| `build.sh`, `scripts/sign-apk.mjs` | kompletter APK-Build ohne Android Studio |
| `release/arena-0.2.0.apk` | 0.2.0 auf `main` |
| `release/arena-0.2.1.apk` | Zoom-Regression (nicht nutzen) |
| `release/arena-0.2.2.apk` | Schriftzoom-Fix |
| `release/arena-0.2.2-b.apk` | + Versionsschild |
| `release/arena-0.2.3.apk` | Seiten-Zoom 25–300 % |
| `release/arena-0.2.4.apk` | UA/Overview/kein Extra-JS |
| `release/arena-0.2.5.apk` | Start `/code`, Desktop-Crop (nicht nutzen) |
| `release/arena-0.2.6.apk` | Viewport-Inject (nicht Default) |
| `release/arena-0.2.7.apk` | **aktuell** — Zoom-Leiste aus, wie Chrome |
| `keystore/` (lokal, gitignoriert) | Signierschlüssel – für Updates zwingend aufbewahren |
| `PROMPT-PC.md` | Wahnsinnsprompt für den PC-Ableger (ArenaPC) |
| `DOKUMENTATION.md` | dieses Dokument |
| Gradle-Dateien (`settings.gradle`, `app/build.gradle` …) | für klassische Builds mit Android Studio (AGP 8.5.2) |

---

## 7. GitHub- & Sessionspezifika (praxiserprobt)

| Ereignis | Wirkung |
|---|---|
| `git push` (4×) | unproblematisch, Session bleibt offen |
| `gh pr create` + **`gh pr merge`** (PR #1) | Code sicher auf `main` (`fefbe97`) – **aber**: das Mergen schloss den Remote-Zugriff dieser Coding-Session („session ended because its pull request was merged or closed") |
| Danach | lokale Commits gehen weiter, Push/gh nicht mehr – neue Session für GitHub-Arbeit nötig |
| Lokale Sicht nach Session-Ende | frischer Clone-Zustand: `main` = gemergter Stand, Session-Branch neu bei „Initial commit", Arbeitsdateien als untracked – per lokalem Commit gesichert (Arbeitsstand + `PROMPT-PC.md` + dieses Dokument) |

**Merksatz für künftige Sessions:** Vor dem Mergen einmal überlegen, ob noch GitHub-Arbeit in derselben Session geplant ist – der Merge ist der point of no return, nicht der Push.

---

## 8. Reproduzierbarkeit

- **Normaler Weg:** Projekt in Android Studio öffnen (AGP 8.5.2, compileSdk 34) → *Build APK*. Benötigt nur ein SDK.
- **SDK-freier Weg (dokumentiert, hier erprobt):** Werkzeuge aus Abschnitt 5.2 besorgen, dann:
  `JAVA=… JAVAC_JAR=… AAPT2=… ANDROID_JAR=… SOOT_DIR=… APKSIGN_LIB=… ./build.sh`
- **Updates:** immer mit demselben `keystore/`-Schlüssel signieren, sonst erzwingt Android Deinstallation vor Neuinstallation.

---

## 9. Offene Punkte & Ausblick

1. **Feldtest 0.2.7:** Schild `0.2.7`, **Aa blass**, keine A−/A+. Vergleich mit Chrome dieselbe URL. Bei Bedarf Aa antippen.
2. ~~Doku nicht auf GitHub~~ — erledigt auf Session-Branch (PR #2, **nicht mergen**).
3. Zoom nur noch hinter dem Aa-Schalter.
4. Mögliche Ausbaustufen: Lesezeichen, „letzte Position merken", Auto-Reload bei Renderer-Freeze, ZIP-Alignment, geprüfter Auto-Updater.
5. PC-Ableger **ArenaPC** (`PROMPT-PC.md`) — bewusst zurückgestellt, erst die Android-App.

---

## 10. Epilog — was diese Session zeigt

Ausgehend von einem Zweizeiler („App für max Android 7…") entstanden in **einer** Session: eine lauffähige, signierte Android-App (166 KB) für zwei reale Gerätegenerationen, eine vollständig dokumentierte Ersatz-Buildkette aus JRE, Fremd-Compiler, Forschungs-Archiv-Soot und Browser-Signierbibliothek, eine ehrliche Fehlerchronik inklusive der eigenen Bugs und ihrer Behebung – und ein fertiges Lastenheft (in Promptform) für den PC-Ableger. **Das ist Arena.**
