# Sudoku

Einfache Sudoku-App für Android (Kotlin + Jetpack Compose).

## Funktionen

- Generator mit drei Schwierigkeitsgraden (Leicht, Mittel, Schwer), jedes Rätsel hat genau eine Lösung
- Notizen (Kandidaten) pro Feld, werden beim Setzen einer Ziffer in den Nachbarfeldern automatisch entfernt
- Rückgängig, Löschen, Tipp
- Fehleranzeige zuschaltbar
- Hell- und Dunkelmodus nach Systemeinstellung

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
