#!/usr/bin/env bash
set -euo pipefail

sdk="${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
export ANDROID_AVD_HOME="${RUNNER_TEMP:?Run this script on the CI runner}/listen45-avd"
image=system-images/android-36/default/x86_64
avd="$ANDROID_AVD_HOME/listen45.avd"
mkdir -p "$avd"

cat > "$ANDROID_AVD_HOME/listen45.ini" <<CONFIG
avd.ini.encoding=UTF-8
path=$avd
target=android-36
CONFIG
cat > "$avd/config.ini" <<CONFIG
AvdId=listen45
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.lcd.width=720
hw.lcd.height=1280
hw.lcd.density=240
disk.dataPartition.size=2G
image.sysdir.1=$image/
tag.id=default
tag.display=Default Android System Image
PlayStore.enabled=false
CONFIG

adb="$sdk/platform-tools/adb"
"$adb" start-server
"$sdk/emulator/emulator" -avd listen45 -no-window -no-snapshot -no-boot-anim \
    -gpu swiftshader_indirect -no-audio -no-metrics > "$RUNNER_TEMP/emulator.log" 2>&1 &
emulator_pid=$!
trap 'kill "$emulator_pid" 2>/dev/null || true' EXIT

if ! timeout 240s bash -c '
    adb=$1
    "$adb" wait-for-device
    until [[ $("$adb" shell getprop sys.boot_completed | tr -d "\r") == 1 ]]; do
        sleep 2
    done
' _ "$adb"; then
    tail -100 "$RUNNER_TEMP/emulator.log"
    exit 1
fi

"$adb" shell input keyevent 82
release_dir="$RUNNER_TEMP/listen45-release"
unsigned="$release_dir/app-release-unsigned.apk"
test_apk="$release_dir/emulator-only.apk"
apksigner="$sdk/build-tools/35.0.0/apksigner"
sha256sum "$unsigned"

# This disposable key and the signed APK stay on this fresh emulator runner.
export LISTEN45_EMULATOR_PASSWORD
LISTEN45_EMULATOR_PASSWORD="$(openssl rand -hex 24)"
keytool -genkeypair -alias emulator-only -keyalg RSA -keysize 2048 -validity 2 \
    -storetype PKCS12 -keystore "$release_dir/emulator-only.p12" \
    -storepass:env LISTEN45_EMULATOR_PASSWORD -dname 'CN=Listen45 emulator only'
"$apksigner" sign --ks "$release_dir/emulator-only.p12" --ks-key-alias emulator-only \
    --ks-pass env:LISTEN45_EMULATOR_PASSWORD --v4-signing-enabled false --out "$test_apk" "$unsigned"
unset LISTEN45_EMULATOR_PASSWORD
"$apksigner" verify --min-sdk-version 24 "$test_apk"

app=eu.x45.listen45
"$adb" install "$test_apk"
"$adb" shell pm list packages -e --user 0 "$app" | tr -d '\r' | grep -Fx "package:$app"
resolved=$("$adb" shell cmd package resolve-activity --brief --user 0 \
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$app" |
    tr -d '\r' | tail -n 1)
printf 'Launcher component: %s\n' "$resolved"
test "$resolved" = "$app/.ui.MainActivity"
"$adb" shell am start -W --user 0 \
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$resolved" |
    tr -d '\r' | tee "$RUNNER_TEMP/listen45-start.log"
if ! grep -Fxq 'Status: ok' "$RUNNER_TEMP/listen45-start.log"; then
    "$adb" logcat -d -t 200
    exit 1
fi
"$adb" shell uiautomator dump /sdcard/listen45-launcher.xml
"$adb" exec-out cat /sdcard/listen45-launcher.xml > "$RUNNER_TEMP/listen45-launcher.xml"
python3 - "$RUNNER_TEMP/listen45-launcher.xml" <<'PYTHON'
from sys import argv
from xml.etree import ElementTree
nodes = list(ElementTree.parse(argv[1]).iter("node"))
assert any(node.get("package") == "eu.x45.listen45" and node.get("text") == "Listen45" for node in nodes)
print("Listen45 is installed and its home screen is visible.")
PYTHON
