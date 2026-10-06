# ArenaAndroid – Arbeits-, Prüf- und Änderungsregeln

Stand: 2026-09-28
Status: Arbeitsdokument für die gemeinsame Entwicklung

Dieses Dokument fasst die im Gespräch ausdrücklich vereinbarten Regeln zusammen. Ergänzungen im Abschnitt **„Noch zu bestätigen“** sind Vorschläge und gelten erst verbindlich, wenn sie ausdrücklich bestätigt wurden.

---

## 1. Grundprinzip

Wir arbeiten in kleinen, nachvollziehbaren Schritten. Sicherheit und überprüfbare Ergebnisse sind wichtiger als Geschwindigkeit.

Es gilt:

- Keine ungeprüften Behauptungen.
- Keine unnötigen Änderungen.
- Keine stillschweigenden Erweiterungen des Auftrags.
- Keine größeren Umbauten, nur weil sie technisch „schöner“ wären.
- Bei Unsicherheit wird angehalten und nachgefragt.

---

## 2. Änderungen an Dateien

### Verbindliche Regeln

1. Dateien werden nur geändert, wenn der konkrete Änderungsumfang vorher besprochen wurde.
2. Eine Zustimmung zu einem Modul ist nicht automatisch eine Zustimmung zu allen denkbaren Änderungen in diesem Modul.
3. Vor einer Änderung werden mindestens folgende Punkte genannt:
   - betroffene Dateien,
   - beabsichtigtes Verhalten,
   - technische Begründung,
   - erwartete Risiken,
   - geplante Prüfung.
4. Nicht zum Auftrag gehörende Formatierungen, Umbenennungen oder Refactorings werden nicht nebenbei durchgeführt.
5. Release-APK, Versionsnummer, Signierung und Build-Skripte werden nicht eigenmächtig verändert.
6. Bestehende Dokumentation wird nicht stillschweigend umgeschrieben. Neue Regeln oder Entscheidungen werden nachvollziehbar ergänzt.

Die Erstellung dieses Dokuments und des separaten To-do-Dokuments ist ausdrücklich beauftragt.

---

## 3. Kleine Schritte und Zwischenstopps

Ein Arbeitsschritt soll möglichst genau eine überprüfbare Sache ändern.

Nach jedem Schritt wird angehalten, wenn:

- der Schritt abgeschlossen ist,
- ein Build oder eine Prüfung fehlgeschlagen ist,
- eine neue technische Frage auftaucht,
- der Umfang größer wird als vereinbart,
- eine Entscheidung des Auftraggebers benötigt wird.

Es wird nicht automatisch mit dem nächsten Schritt weitergemacht.

---

## 4. Offizielle Wege zuerst

Vor einer technischen Lösung wird geprüft, ob Android, WebView, GitHub oder die betroffene Plattform einen offiziellen Weg dafür dokumentiert.

Reihenfolge:

1. Problem und gewünschtes Ergebnis exakt beschreiben.
2. Bestehende Implementierung und Dokumentation im Repository prüfen.
3. Offizielle Dokumentation und offizielle technische Vorgaben recherchieren.
4. Erst danach alternative oder eigene Lösungen bewerten.
5. Vor Umsetzung eine kleine, konkrete Lösung vorschlagen.
6. Nur nach Zustimmung ändern.

Nicht-offizielle Workarounds werden als solche gekennzeichnet. Sie dürfen nicht als offizielle Lösung dargestellt werden.

---

## 5. Verifikation und Wahrheitspflicht

Jede Aussage wird einer Kategorie zugeordnet:

- **Geprüfter Repository-Befund:** direkt im Code, Manifest, Build oder in einer Datei festgestellt.
- **Offiziell belegt:** durch eine offizielle Dokumentation oder Quelle belegt.
- **Lokal geprüft:** mit einem konkreten Befehl, Build oder statischen Check geprüft.
- **Auf dem XCover 5 geprüft:** vom Auftraggeber auf dem echten Gerät getestet.
- **Hypothese:** plausible Erklärung, aber noch nicht verifiziert.
- **Unbekannt:** es fehlen Daten.

Hypothesen werden ausdrücklich als Hypothesen bezeichnet.

Wenn etwas nicht sicher feststellbar ist, lautet die korrekte Antwort nicht „wahrscheinlich erledigt“, sondern zum Beispiel:

- „Noch nicht geprüft.“
- „Nur im Code gesehen, nicht auf dem Gerät getestet.“
- „Das muss auf dem XCover 5 verifiziert werden.“
- „Dafür fehlen uns noch Logs, Screenshots oder ein reproduzierbarer Ablauf.“

---

## 6. Build- und Testablauf

Nicht jede einzelne interne Codeänderung löst automatisch einen Build aus. Geprüft und gebaut wird immer dann, wenn ein vorher besprochener Änderungsstand inhaltlich fertig ist und übergeben werden soll.

Für jeden solchen Übergabestand gilt grundsätzlich:

1. Änderungsumfang besprechen.
2. Ausgangszustand prüfen.
3. Die vereinbarten Änderungen durchführen.
4. Vor der Übergabe die passenden statischen Prüfungen und den erforderlichen Build ausführen.
5. Ergebnis mit den genauen Befehlen und Ergebnissen melden.
6. Eine APK nur erstellen oder aktualisieren, wenn das zum besprochenen Übergabestand gehört.
7. Die übergebene APK auf dem echten XCover 5 testen lassen.
8. Das Testergebnis abwarten und dokumentieren.
9. Erst danach den nächsten Änderungsstand planen.

Ein lokaler Build ersetzt keinen Gerätetest. Ein Emulator oder ein Cloud-Gerät ersetzt ebenfalls nicht automatisch den XCover-5-Test.

---

## 7. Gerätetest als Abnahmekriterium

Der echte XCover 5 ist das maßgebliche Gerät für die Abnahme der App.

Ein Schritt ist erst als geräteverifiziert zu betrachten, wenn der Auftraggeber ihn auf dem XCover 5 geprüft und das Ergebnis mitgeteilt hat.

Dabei sollen nach Möglichkeit festgehalten werden:

- APK-Version und Versionsschild,
- Android-Version und Gerät,
- genauer Ablauf,
- erwartetes Verhalten,
- tatsächliches Verhalten,
- Screenshot oder Fehlermeldung bei Abweichung.

---

## 8. Umgang mit Fehlern und unmöglichen Aufgaben

Wenn eine Aufgabe nicht möglich, nicht sicher prüfbar oder zu groß ist, wird vor der Umsetzung gestoppt.

Das gilt insbesondere, wenn:

- die benötigten Zugangsdaten oder Systeme nicht zugänglich sind,
- eine Website-Funktion serverseitig verborgen ist,
- ein echter Login oder OAuth-Ablauf nicht reproduziert werden kann,
- der vorgeschlagene Weg Sicherheits- oder Datenschutzrisiken erzeugt,
- der Umfang mehrere unabhängige Module umfasst,
- ein Build nicht reproduzierbar ist,
- ein Gerätetest erforderlich ist, aber noch nicht vorliegt.

Dann werden Problem, fehlende Information und mögliche nächste Schritte klar benannt. Es wird keine ungeprüfte Ersatzlösung als gleichwertig ausgegeben.

---

## 9. Sicherheit und Datenschutz

- Keine Passwörter, Tokens, Cookies, privaten Repository-Daten oder privaten E-Mail-Adressen in Chat, Git oder Testdateien übernehmen.
- Screenshots, HAR-Dateien, HTML-Exporte und Logs müssen vor dem Teilen bereinigt werden.
- Echte Login-Daten werden nicht in lokale Testseiten, APKs oder Dokumente eingebaut.
- Debugging- oder Inspektionsfunktionen dürfen nicht versehentlich in einer Release-APK bleiben.
- Sicherheitsausnahmen müssen mit Zeitpunkt, Anlass, betroffenen URLs und Geltungsbereich dokumentiert werden.
- Eine Funktion wird nicht als „sicher“ bezeichnet, wenn relevante Ausnahmen bestehen.

---

## 10. Git, Branches und Zusammenführen

- Die Arbeit bleibt auf dem vorgesehenen Projekt-Branch.
- Es wird kein Merge durchgeführt, außer der Auftraggeber ordnet es ausdrücklich an.
- Ein Merge ist nicht Teil eines normalen Arbeits- oder Testschritts.
- Vor einem Merge müssen die Änderungen, Testergebnisse und offenen Risiken separat bestätigt worden sein.

---

## 11. Kommunikation nach jedem Schritt

Jeder abgeschlossene Schritt wird mit diesem Schema gemeldet:

```text
Schritt:
Geänderte Dateien:
Nicht geänderte Dateien:
Was wurde geprüft:
Verwendete Befehle/Quellen:
Ergebnis:
Was ist noch unbestätigt:
Nächste Entscheidung:
```

Bei einem Fehler zusätzlich:

```text
Fehler:
Reproduzierbarer Ablauf:
Erwartet:
Tatsächlich:
Erste Hypothese:
Noch benötigte Belege:
```

---

## 12. Vorschläge für noch zu bestätigende Regeln

Diese Punkte erscheinen sinnvoll, wurden aber noch nicht ausdrücklich beschlossen:

1. **Jedes Modul bekommt vor dem Start ein schriftliches Abnahmekriterium.**
2. **Vor jeder Codeänderung wird ein Ausgangsprotokoll erstellt:** Branch, Git-Status, Version, vorhandenes APK und gegebenenfalls SHA-256.
3. **Jeder Schritt erhält eine Rückfallmöglichkeit:** betroffene Dateien und Möglichkeit, die Änderung sauber zurückzunehmen.
4. **Eine Änderung darf höchstens einen funktionalen Schwerpunkt haben.** Authentifizierung, Navigation und Datei-Browser werden nicht in einem einzigen unprüfbaren Umbau vermischt.
5. **Debug- und Release-Artefakte werden eindeutig getrennt benannt.**
6. **Jede Recherche wird mit URL und Abrufdatum dokumentiert.**
7. **Ein Modul gilt erst als abgeschlossen, wenn keine offenen Punkte der Priorität 1 bestehen oder diese ausdrücklich akzeptiert wurden.**
8. **Vor dem Erstellen einer neuen Release-APK wird die Signatur- und Update-Kompatibilität ausdrücklich geprüft.**
9. **Externe Dienste werden nur mit Testkonten verwendet, sofern eine Untersuchung nicht ohne Login möglich ist.**
10. **Wenn Screenshots oder Logs nicht ausreichen, wird nicht geraten, sondern gezielt ein reproduzierbarer Diagnose-Schritt geplant.**

Diese zehn Punkte sind Vorschläge und noch keine zusätzlichen verbindlichen Regeln.
