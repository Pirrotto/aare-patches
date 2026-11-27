#!/usr/bin/env bash
# Smoke test on an Android emulator: the patched AARE installs, starts with every patch switch on,
# shows the patches' Settings and the car screen preview, and the rail's buttons can be pressed,
# without a crash or an ANR. No car, Vela, WhatsApp or Shizuku here: audio, microphone, tunnels and
# messages are not tested. Screenshots and the log go to $OUT for the run's artifact.
set -u
PKG=net.prototux.aare
APK=${1:?patched apk}
OUT=${2:-smoke}
mkdir -p "$OUT"
PATCH_LOG=aare-patches-log.txt
fail=0

shot() { adb exec-out screencap -p > "$OUT/$1.png" || true; }
alive() { adb shell pidof "$PKG" > /dev/null; }
crashed() {
  # A crash or ANR of AARE's process since the start.
  adb logcat -d -b crash > "$OUT/crash.txt" 2>/dev/null || true
  adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
  if grep -q "Process: $PKG" "$OUT/crash.txt" || grep -qE "ANR in $PKG|FATAL EXCEPTION.*$PKG" "$OUT/logcat.txt"; then
    return 0
  fi
  return 1
}
check() {
  sleep "${2:-4}"
  if crashed; then echo "::error::AARE crashed or froze after: $1"; fail=1; return 1; fi
  echo "ok: $1 (in front: $(adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity|ResumedActivity:' | grep -oE '[a-zA-Z0-9_.]+/[a-zA-Z0-9_.$]+' | head -1))"
}

adb root > /dev/null 2>&1 || true
sleep 3
adb wait-for-device
adb logcat -c || true
adb logcat -b crash -c || true

adb install -r -g "$APK" || { echo "::error::install failed"; exit 1; }

# Every patch switch on (the patches' own preferences file), as a user who turns everything on.
KEYS=$(grep -oE 'public static final String [A-Z_]+ = "[a-z_]+"' \
  extensions/extension/src/main/java/app/template/extension/aare/PatchSettings.java | sed -E 's/.*"([a-z_]+)"/\1/')
{
  echo "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"
  echo "<map>"
  for k in $KEYS; do echo "    <boolean name=\"$k\" value=\"true\" />"; done
  echo "</map>"
} > "$OUT/aare_patches.xml"
adb shell am start -W -n "$PKG/.ui.MainActivity" > /dev/null
sleep 5
adb shell am force-stop "$PKG"
APP_UID=$(adb shell stat -c %u "/data/data/$PKG" | tr -d '\r')
adb shell mkdir -p "/data/data/$PKG/shared_prefs"
adb push "$OUT/aare_patches.xml" "/data/local/tmp/aare_patches.xml" > /dev/null
adb shell cp /data/local/tmp/aare_patches.xml "/data/data/$PKG/shared_prefs/aare_patches.xml"
adb shell chown "$APP_UID:$APP_UID" "/data/data/$PKG/shared_prefs/aare_patches.xml"
adb shell chmod 660 "/data/data/$PKG/shared_prefs/aare_patches.xml"
adb shell restorecon -R "/data/data/$PKG/shared_prefs" || true
echo "switches on: $(echo $KEYS | wc -w)"

# 1. AARE starts.
adb shell am start -W -n "$PKG/.ui.MainActivity" > /dev/null
check "start" 6 && shot 1-main
alive || { echo "::error::AARE isn't running after the start"; fail=1; }

# 2. The patches' Settings (not exported: started as root).
adb shell am start -W -n "$PKG/app.template.extension.aare.PatchSettingsActivity" > /dev/null
check "patches' Settings" 4 && shot 2-settings
adb shell input swipe 500 1500 500 400 300
check "patches' Settings, scrolled" 2 && shot 3-settings-scrolled
adb shell input keyevent KEYCODE_BACK

# 3. The car screen preview runs AARE's car screen without a car.
adb shell am start -W -n "$PKG/.ui.CarPreviewActivity" > /dev/null
check "car screen preview" 10 && shot 4-preview

# 4. Every clickable control on the left part of the preview (the rail: apps, home, mic).
adb shell uiautomator dump /data/local/tmp/ui.xml > /dev/null 2>&1
adb pull /data/local/tmp/ui.xml "$OUT/ui.xml" > /dev/null 2>&1 || true
W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
n=0
if [ -f "$OUT/ui.xml" ]; then
  for b in $(grep -oE 'clickable="true"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$OUT/ui.xml" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]'); do
    read -r x1 y1 x2 y2 <<< "$(echo "$b" | tr '[],' '   ')"
    x=$(( (x1 + x2) / 2 )); y=$(( (y1 + y2) / 2 ))
    [ "$x" -lt $(( W / 4 )) ] || continue
    n=$((n + 1))
    adb shell input tap "$x" "$y"
    check "preview control $n at $x,$y" 4 && shot "5-control-$n"
    adb shell am start -W -n "$PKG/.ui.CarPreviewActivity" > /dev/null
    [ "$n" -ge 8 ] && break
  done
fi
echo "preview controls pressed: $n"

# 5. Still alive at the end.
check "the end" 3
alive || { echo "::error::AARE isn't running at the end"; fail=1; }
# What the patches caught and kept out of AARE (their try/catch log a warning): shown, not failed,
# since some are expected here (no car, no Shizuku).
echo "--- the patches' own diagnostics log (technical state only; an emulator, no personal data)"
adb shell cat "/data/data/$PKG/no_backup/$PATCH_LOG" 2>/dev/null | tail -40 || echo "(none)"
echo "--- patches' warnings in the log"
grep -E " (W|E) AarePatches" "$OUT/logcat.txt" | sed -E 's/^.{0,31}//' | sort | uniq -c | sort -rn | head -40 || true
grep -A3 -E " W AarePatches[A-Za-z]*: " "$OUT/logcat.txt" | grep -E "Exception|Error" | sort | uniq -c | sort -rn | head -20 || true
exit $fail
