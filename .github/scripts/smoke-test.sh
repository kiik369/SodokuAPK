#!/usr/bin/env bash
# Installiert die Debug-APK im Emulator, startet sie, tippt ein paar Mal aufs Brett
# und prüft, dass die App nicht abstürzt. Screenshots und Logcat landen in smoke/.
set -euo pipefail

PKG=de.sudoku.app
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=smoke
mkdir -p "$OUT"

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

check_alive() {
  adb logcat -d > "$OUT/logcat.txt"
  if grep -E "FATAL EXCEPTION|ANR in $PKG" "$OUT/logcat.txt"; then
    echo "::error::App ist abgestürzt oder hängt ($1)"
    grep -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" || true
    exit 1
  fi
  if ! adb shell pidof "$PKG" > /dev/null; then
    echo "::error::App-Prozess läuft nicht mehr ($1)"
    exit 1
  fi
  echo "OK: $1"
}

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 15
shot 1-start
check_alive "Start"

# Bildschirmgröße holen und ein paar Felder und Tasten antippen
SIZE=$(adb shell wm size | tail -1 | awk '{print $NF}' | tr -d '\r')
W=${SIZE%x*}
H=${SIZE#*x}
for frac in 30 40 50; do
  adb shell input tap $((W * frac / 100)) $((H * frac / 100))
  sleep 1
  adb shell input tap $((W * frac / 100)) $((H * 85 / 100))
  sleep 1
done
sleep 3
shot 2-nach-eingabe
check_alive "Eingabe"

# App beenden und neu starten (lädt gespeicherte Einstellungen)
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity"
sleep 10
shot 3-neustart
check_alive "Neustart"

echo "Smoke-Test bestanden"
