#!/usr/bin/env bash
# Installs the debug APK on the running emulator, plays it with synthetic touch
# input, and captures screenshots, logcat and the frame rate the app reports.
#
#   play.sh <package> [drag|hold]
#
# "drag" swipes and taps (good for swipe/spin/aim games); "hold" alternates long
# presses with short gaps (good for press-and-release games). Tune the input
# coordinates to match your controls — the goal is legal input that exercises a
# real session and yields usable store screenshots in artifacts/shots.
set -uo pipefail

PKG="$1"
STYLE="${2:-drag}"
OUT="artifacts"
mkdir -p "$OUT/shots"

shot() { adb exec-out screencap -p > "$OUT/shots/$1.png"; echo "  shot $1"; }

echo "== waiting for the device to settle =="
adb wait-for-device
adb shell input keyevent 82 >/dev/null 2>&1 || true

# Suppress Android's one-time "Viewing full screen" overlay. It covers the top
# of every screenshot, and on a loaded emulator its window ANRs waiting for a
# focus event, which looks alarming and has nothing to do with the app.
adb shell settings put secure immersive_mode_confirmations confirmed

echo "== installing =="
adb install -r -t app/build/outputs/apk/debug/app-debug.apk

SIZE=$(adb shell wm size | tail -1 | tr -d '\r' | sed 's/.*: *//')
W=${SIZE%x*}
H=${SIZE#*x}
CX=$((W / 2))
echo "== screen ${W}x${H} =="

adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 6
shot 01-title

echo "== playing ($STYLE) =="
adb shell input tap $CX $((H * 3 / 4))
sleep 2
shot 02-run-started

if [ "$STYLE" = "hold" ]; then
  for i in $(seq 1 14); do
    adb shell input swipe $CX $((H / 2)) $CX $((H / 2)) $((400 + RANDOM % 700))
    sleep 0.3
    [ "$i" = "3" ] && shot 03-mid-run
    [ "$i" = "8" ] && shot 04-later
  done
else
  for i in $(seq 1 14); do
    if [ $((RANDOM % 2)) = 0 ]; then
      adb shell input swipe $((CX - 300)) $((H * 2 / 3)) $((CX + 300)) $((H * 2 / 3)) 250
    else
      adb shell input swipe $((CX + 300)) $((H * 2 / 3)) $((CX - 300)) $((H * 2 / 3)) 250
    fi
    adb shell input tap $CX $((H * 2 / 3))
    [ "$i" = "3" ] && shot 03-mid-run
    [ "$i" = "8" ] && shot 04-later
    sleep 0.4
  done
fi

echo "== letting the run play out =="
sleep 25
shot 05-after-25s
adb shell input tap $CX $((H * 3 / 4))
sleep 3
shot 06-after-tap

echo "== collecting =="
adb logcat -d > "$OUT/logcat.txt"
adb shell dumpsys meminfo "$PKG" > "$OUT/meminfo.txt" 2>&1 || true

echo; echo "== frame rate reported by the app =="
grep -h "fps=" "$OUT/logcat.txt" | tail -20 | tee "$OUT/fps.txt" || echo "(no fps lines)"

echo; echo "== crash check =="
FAIL=0
if grep -qE "FATAL EXCEPTION|AndroidRuntime: .*Exception" "$OUT/logcat.txt"; then
  echo "CRASH DETECTED:"; grep -A 25 -E "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60; FAIL=1
fi
if grep -q "ANR in $PKG" "$OUT/logcat.txt"; then
  echo "ANR DETECTED IN THE APP"; grep -A 10 "ANR in $PKG" "$OUT/logcat.txt" | head -30; FAIL=1
fi

PID=$(adb shell pidof "$PKG" | tr -d '\r')
if [ -z "$PID" ]; then
  echo "PROCESS IS GONE - the app died during the run"; FAIL=1
else
  echo "process alive (pid $PID) after the full session"
fi

echo; echo "== AdMob =="
grep -iE "Ads|admob|Interstitial|Rewarded" "$OUT/logcat.txt" | grep -viE "^$" | head -25 | tee "$OUT/ads.txt"
if grep -q "Missing application ID" "$OUT/logcat.txt"; then
  echo "ADMOB APP ID MISSING - this crashes on launch"; FAIL=1
fi

exit $FAIL
