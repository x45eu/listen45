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
./gradlew --no-daemon connectedDebugAndroidTest
