# Sudoku

Einfache Sudoku-App für Android (Kotlin + Jetpack Compose).

## Funktionen

- Generator mit drei Schwierigkeitsgraden (Leicht, Mittel, Schwer), jedes Rätsel hat genau eine Lösung
- Notizen (Kandidaten) pro Feld, werden beim Setzen einer Ziffer in den Nachbarfeldern automatisch entfernt
- Rückgängig, Löschen, Tipp
- Fehleranzeige zuschaltbar
- Hell- und Dunkelmodus nach Systemeinstellung

## Im Browser spielen

Die Web-Version liegt in `web/` und wird bei jedem Push auf `main` automatisch getestet und veröffentlicht:

**https://kiik369.github.io/SodokuAPK/**

Sie hat dieselbe Spiellogik und dasselbe Design wie die App sowie die wichtigsten Einstellungen. Der Spielstand bleibt im Browser gespeichert. Mit der Tastatur: Ziffern 1–9, Pfeiltasten, Entf/Rücktaste löscht, N Notizen, Z zurück, H Tipp.

Lokal starten (ohne Installation):

```bash
cd web && python3 -m http.server 8080
```

Dann http://localhost:8080 öffnen. Tests: `npm install`, dann `npm test` (Logik) und `npm run test:ui` (Browser mit Playwright).

Die Web-Version ist ein eigener Code. Änderungen an der Spiellogik müssen in `web/sudoku.js` und in der App gemacht werden.

## APK bauen

### Mit GitHub Actions (ohne Android Studio)

1. Im Repository auf **Actions → Build APK** gehen. Der Build startet bei jedem Push auf `main`, oder per **Run workflow**.
2. Nach dem Durchlauf liegt die Datei `app-debug.apk` als Artefakt `sudoku-debug-apk` am Ende der Workflow-Seite.
3. APK aufs Handy übertragen und installieren (Installation aus unbekannten Quellen erlauben).

### Mit Android Studio

Ordner öffnen, warten bis Gradle synchronisiert hat, dann **Build → Build APK(s)**.

## Aufbau

- `SudokuGenerator.kt`: Rätsel erzeugen und Lösungen zählen
- `GameViewModel.kt`: Spielzustand, Eingaben, Rückgängig, Tipp
- `MainActivity.kt`: Oberfläche
