# ArenaAndroid – To-do und Entscheidungsprotokoll

Stand: 2026-09-28
Status: ausführliche Arbeitsliste; keine der offenen technischen Aufgaben ist dadurch automatisch freigegeben

Dieses Dokument sammelt die bisher besprochenen Befunde, Entscheidungen, offenen Fragen und den geplanten Ablauf für das nächste Modul.

---

## 1. Aktueller Ausgangspunkt

Repository und Code wurden zunächst nur analysiert. Seit Beginn dieser Vereinbarung wurden keine bestehenden Projektdateien verändert.

Der aktuelle Code ist ein nativer Java-WebView-Wrapper für Arena:

- Start-URL: `https://arena.ai/code`
- `minSdk 19`
- `targetSdk 28`
- `compileSdk 34`
- keine externen App-Bibliotheken
- WebView-Navigation, Login, Upload, Download, Popup-, Zoom-, Dark- und Desktop-Funktionen liegen überwiegend in `MainActivity.java`
- Fehler- und Offline-Seiten liegen unter `app/src/main/assets/html/`
- aktueller dokumentierter Release-Stand: `0.2.9`

Die vorliegenden APKs im Ordner `release/` bleiben unverändert, solange keine andere Entscheidung getroffen wird.

---

## 2. Bereits bewertete Punkte

### 2.1 Safe Browsing – Priorität 1, noch offen

**Repository-Befund:**

`ArenaWebViewClient.java` enthält:

```java
response.proceed(false);
```

Der aktuelle Code setzt damit eine Safe-Browsing-Warnung fort, statt die Navigation zu blockieren.

**Dokumentierter Zeitpunkt und Grund:**

`DOKUMENTATION.md` ordnet die Entscheidung Version `0.2.0` und `18:59 UTC` zu. Als Grund wird die damalige Erfahrung mit wiederholten Fehlermeldungen und einer Safe-Browsing-Blockade für Arena genannt. Der Code-Kommentar sagt ebenfalls, dass Arena ausdrücklich gewünscht sei.

**Wichtige Abweichung:**

Die Dokumentation spricht von Arena. Die konkrete Methode wird jedoch nicht auf `arena.ai` beschränkt, sondern gilt für Safe-Browsing-Treffer innerhalb dieser WebView allgemein.

**Noch nicht geklärt:**

- Welche konkrete URL wurde blockiert?
- Welcher Threat-Typ lag vor?
- War es ein Fehlalarm oder eine echte Warnung?
- Sollte nur eine bekannte Arena-Adresse behandelt werden?
- Welche offizielle Android-WebView-Empfehlung gilt für diesen Fall?

**Entscheidung:**

Noch keine Änderung. Zuerst offizielle Dokumentation und konkrete historische Ursache prüfen.

---

### 2.2 Mixed Content – für Modul 2 vorgemerkt

**Repository-Befund:**

In `MainActivity.java` wird verwendet:

```java
s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
```

**Spannung:**

README und Projektbeschreibung sprechen von „nur HTTPS“. Die aktuelle WebView-Einstellung ist jedoch eine Kompatibilitätsoption für gemischte Inhalte.

**Status:**

Der Auftraggeber möchte diesen Punkt gemeinsam mit dem nächsten Modul behandeln. Keine Änderung vor dieser Besprechung.

**Noch zu prüfen:**

- Welche Ressourcen lädt Arena tatsächlich?
- Gibt es auf dem XCover 5 sichtbare Fehler, wenn Mixed Content strenger blockiert wird?
- Welche Einstellung ist offiziell und funktional für den konkreten Arena-Login erforderlich?

---

### 2.3 Erlaubte HTTPS-Domains – für Modul 2 vorgemerkt

**Repository-Befund:**

`handleUrl()` lässt HTTP- und HTTPS-URLs grundsätzlich in der WebView laden. Die Manifest-Deep-Links beziehen sich dagegen nur auf `arena.ai` und `www.arena.ai`.

**Offene Produktfrage:**

Soll die App beliebige HTTPS-Ziele innerhalb der WebView anzeigen oder nur Arena und die für Login/GitHub zwingend benötigten Domains?

**Status:**

Wird gemeinsam mit dem nächsten Modul betrachtet. Keine Änderung vor einer Entscheidung.

---

### 2.4 Signierung – beobachtet, aber nicht als Bug geändert

**Repository-Befund:**

Der Release-Build verwendet derzeit den Debug-Signatur-Schlüssel. Die Dokumentation begründet das mit Update-Kompatibilität bei Sideloading und weist darauf hin, dass derselbe Schlüssel aufbewahrt werden muss.

**Beobachtung des Auftraggebers:**

Die aktuelle Signierung scheint erforderlich gewesen zu sein, damit die App auf dem Gerät installiert werden konnte.

**Technische Abgrenzung für später:**

Eine gültig signierte APK ist grundsätzlich installierbar; derselbe Schlüssel ist besonders wichtig, damit eine neue APK als Update über die vorhandene Installation akzeptiert wird. Ob im konkreten Fall Signatur, Signaturverfahren, SDK-Metadaten oder ein anderer APK-Unterschied die Installation beeinflusst hat, wurde nicht neu verifiziert.

**Status:**

Keine Änderung. Nur bei Relevanz für das nächste Modul oder einen Update-Test untersuchen.

---

### 2.5 Automatisierte Tests – aktuell kein Einwand

Der Auftraggeber hat zu diesem Punkt keinen aktuellen Änderungsbedarf genannt.

**Status:**

Keine kurzfristige Aufgabe. Tests können später modulbezogen ergänzt werden, falls ein konkreter reproduzierbarer Fehler das rechtfertigt.

---

### 2.6 Größe von `MainActivity.java` – nächstes Modul

`MainActivity.java` enthält rund 1.146 Zeilen und mehrere Verantwortlichkeiten.

**Status:**

Wird beim nächsten Modul beziehungsweise bei einem konkreten Anlass behandelt. Kein eigenständiger Refactor vor der GitHub-Analyse.

---

### 2.7 Build-Weg – Baseline vor dem Modul, Umbau danach

**Empfehlung:**

Vor dem Modul nur eine unverändernde Ausgangsprüfung durchführen:

- Branch prüfen
- `git status` prüfen
- aktuelle Versionsnummer festhalten
- verfügbaren Build-Weg feststellen
- vorhandenes Ausgangs-APK und Hash dokumentieren, falls benötigt

Die Build-Infrastruktur selbst sollte erst nach dem Modul verbessert werden, solange sie den nächsten Schritt nicht blockiert. Falls das Modul wegen fehlender Reproduzierbarkeit nicht gebaut oder geprüft werden kann, wird hier vorher angehalten.

**Status:**

Noch keine Build-Infrastruktur geändert.

---

## 3. Modul 2 – GitHub-Integration

### 3.1 Ist-Zustand laut Auftraggeber

Die Probleme treten in der Android-App-Ansicht auf. Die PC-Version besitzt eine funktionierende bzw. bessere GitHub-Integration und dient als Referenz.

Aktuell in der Android-App:

- Es gibt ein Dropdown zur Repository-Auswahl.
- Die GitHub-Anbindung ist nach Einschätzung des Auftraggebers rudimentär.
- Die PC-Version besitzt zusätzlich einen Button beziehungsweise eine einfache Datei-Browser-Ansicht des Repositories.
- Gewünscht sind dort auch die weiteren Funktionen der PC-Version.

### 3.2 Konkrete Android-Probleme

#### Problem A – getrennte Anmeldungen

Die Arena-Anmeldung und die GitHub-Anmeldung sollen getrennt sein. In der Android-App funktioniert GitHub laut Beobachtung jedoch nur, wenn Arena-Account-E-Mail und GitHub-E-Mail identisch sind.

Die PC-Version zeigt laut Auftraggeber, dass die getrennte Anmeldung grundsätzlich funktioniert.

**Noch nicht geklärt:**

- Ist die Ursache die WebView-Session?
- Werden Cookies oder Redirects anders behandelt?
- Wird ein OAuth-Callback nicht korrekt abgeschlossen?
- Kommt die Website in der Android-WebView in einen anderen Flow?
- Ist es ein Arena-Web-/Backend-Problem, das nur durch die Android-Umgebung sichtbar wird?

Es wird keine dieser Ursachen als Tatsache angenommen, bevor sie belegt ist.

#### Problem B – kein Rückweg nach GitHub

Nach der GitHub-Seite mit dem Hinweis, dass die Seite beziehungsweise das Fenster nun geschlossen werden kann, gibt es in der Android-App keinen funktionierenden Home-Rückweg zur Arena.

**Mögliche, noch nicht beschlossene Zielanforderung:**

- Ein definierter Home-Button oder eine vergleichbare Rückkehrmöglichkeit.
- Rückkehr zu `https://arena.ai/code`.
- Erhalt der Arena-Session.
- Kein Verlust einer bereits getroffenen Repository-Auswahl.

Ob ein nativer Home-Button oder eine andere Navigation die richtige Lösung ist, wird erst nach Analyse des Redirect-Ablaufs entschieden.

#### Problem C – fehlender Repository-Datei-Browser und weitere Funktionen

Die PC-Version besitzt laut Auftraggeber eine einfach gehaltene, aber nützliche Datei-Browser-Ansicht. Die Android-App bietet aktuell mindestens die Repository-Auswahl, aber nicht denselben Funktionsumfang.

„Alle Funktionen“ muss vor der Umsetzung in konkrete Einzelfunktionen zerlegt werden.

Mögliche Kategorien für die Bestandsaufnahme:

- Repository auswählen
- Repository öffnen
- Verzeichnis anzeigen
- Datei öffnen
- Dateiinhalt lesen
- zurück in ein Verzeichnis gehen
- Branch oder Version auswählen
- Suche
- Datei- oder Repository-Aktionen
- Fehler- und Berechtigungszustände
- Rückkehr zur Arena

Diese Liste ist eine Arbeitsstruktur, keine Behauptung, dass jede Funktion in der PC-Version vorhanden ist.

---

## 4. Vorgeschlagene Modul-Aufteilung

### M2-A – Bestandsaufnahme, keine Codeänderung

Ziel: PC-Sollverhalten und Android-Istverhalten exakt beschreiben.

Ergebnis:

- kurze Funktionsliste
- reproduzierbarer Ablauf für Problem A
- reproduzierbarer Ablauf für Problem B
- sichtbare Unterschiede beim Repository-Browser
- noch unbekannte Punkte

Es ist nicht nötig, jeden Bildschirm doppelt zu dokumentieren. Benötigt werden nur die relevanten Vergleichspunkte:

- funktionierender PC-Zustand,
- entsprechender Android-Zustand,
- Android-Fehlerzustand.

### M2-B – GitHub-Login und Rücksprung

Erst nach M2-A:

- OAuth-/Redirect-Ablauf untersuchen
- Cookie- und WebView-Verhalten prüfen
- unterschiedliche Arena-/GitHub-E-Mail reproduzieren
- Rückkehr aus dem „Fenster schließen“-Zustand prüfen
- kleinste mögliche Änderung vorschlagen

### M2-C – Repository-Datei-Browser

Erst nach stabiler Anmeldung und Rückkehr:

- PC-Funktionen inventarisieren
- kleinste fehlende Funktion auswählen
- Umsetzung begrenzen
- Build und XCover-5-Test durchführen

### M2-D – Weitere Funktionen einzeln ergänzen

Jede weitere Funktion bekommt:

- eigene Beschreibung,
- eigene Abnahmekriterien,
- eigenen Testablauf,
- eigene Entscheidung, ob die Website oder der App-Wrapper geändert werden muss.

Es wird nicht nach jeder einzelnen Codeänderung gebaut. Erst wenn ein vorher besprochener Funktionsstand fertig ist und übergeben werden soll, wird dieser statisch geprüft, gebaut und anschließend auf dem XCover 5 abgenommen.

---

## 5. Geparkte Idee: vollständige HTML-Kopie oder Online-Emulator

### Vollständige HTML-Kopie

Die Idee wurde besprochen, aber nicht als Lösung gewählt.

Eine moderne Arena-Seite besteht nicht nur aus einer HTML-Datei, sondern auch aus JavaScript, API-Aufrufen, Serverzustand, Cookies, OAuth-Redirects und möglicherweise privaten Daten. Eine gespeicherte HTML-Kopie würde den echten Login und die echte GitHub-Integration nicht zuverlässig reproduzieren.

Eine lokale, bereinigte Testsimulation wäre zwar möglich, würde aber nur WebView-Navigation und App-Logik testen, nicht die echte Arena-/GitHub-Anbindung.

### Online-Emulatoren und Cloud-Geräte

Es wurden als mögliche Werkzeuge recherchiert:

- Appetize für browserbasierte APK-Emulation,
- BrowserStack App Live für interaktive Tests auf echten Cloud-Geräten,
- AWS Device Farm für Remote-Zugriff auf echte Geräte,
- Firebase Test Lab für automatisierte Tests mit Logs, Screenshots und Videos,
- Genymotion SaaS für cloudbasierte virtuelle Android-Geräte,
- Chrome DevTools Remote Debugging für die echte WebView auf dem XCover 5.

Diese Dienste würden keinen direkten Zugriff des Agents auf einen persönlichen Login herstellen. Sie könnten nur genutzt werden, wenn der Auftraggeber selbst die Sitzung startet und bereinigte Screenshots, HTML-, HAR- oder Logdateien bereitstellt.

Das Thema ist vorerst geparkt. Es wird nicht umgesetzt und erzeugt keine Dateiänderung.

---

## 6. Abnahmekriterien für Modul 2

Vor einer Freigabe müssen mindestens diese Punkte einzeln geprüft werden:

1. Arena- und GitHub-Anmeldung können mit unterschiedlichen E-Mail-Adressen verwendet werden, sofern die PC-Version dies erlaubt.
2. Der konkrete GitHub-Login-Ablauf funktioniert auf dem XCover 5.
3. Nach einem GitHub-Redirect gibt es keinen unauflösbaren „Seite schließen“-Zustand.
4. Die Rückkehr zur Arena ist eindeutig und erhält die Sitzung.
5. Der Repository-Datei-Browser entspricht den vorher festgelegten PC-Funktionen.
6. Jede ergänzte Funktion wurde einzeln geprüft.
7. Kein Debugging-Schalter bleibt in der Release-APK aktiv.
8. Die bestehende Arena-Navigation, Downloads, Uploads und Fehlerseiten werden durch das Modul nicht unbeabsichtigt beschädigt.
9. Die APK wurde lokal gebaut und anschließend vom Auftraggeber auf dem XCover 5 getestet.
10. Nicht getestete Punkte werden ausdrücklich als offen dokumentiert.

---

## 7. Nächster erlaubter Schritt

Noch keine Codeänderung.

Der nächste Schritt ist nur die Besprechung von **M2-A**:

- Welche PC-Funktion soll als erste Referenz gelten?
- Welcher konkrete Android-Ablauf reproduziert Problem A?
- Welcher konkrete Android-Ablauf reproduziert Problem B?
- Welche Repository-Funktionen sind für den ersten kleinen Umsetzungsschritt zwingend?

Erst nach dieser Eingrenzung wird eine konkrete Dateiänderung vorgeschlagen.
