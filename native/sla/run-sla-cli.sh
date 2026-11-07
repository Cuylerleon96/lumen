#!/usr/bin/env bash
# Build the sla_cli harness (arm64) and run it on the emulator: STL -> SL1.
set +e
R="$HOME/Projects/mobile-slicer-src"; CM="$HOME/.local/opt/cmake-3.30.5-linux-x86_64"
ADB="$HOME/android-sdk/platform-tools/adb"
CFG="${1:-/tmp/prusa297-cfg}"
BIN="$CFG/src/sla_cli/sla_cli"
export ANDROID_NDK_ROOT="$HOME/android-sdk/ndk/27.2.12479018" ORCAMICRO_PREFIX="$R/native/build/prefix-sla"

echo "== build sla_cli =="
"$CM/bin/cmake" --build "$CFG" --target sla_cli -j 4 2>&1 | tail -6
[ -x "$BIN" ] || { echo "BIN MISSING"; exit 1; }
file "$BIN" | cut -c1-90

echo "== test STL =="
python3 "$R/native/sla/gen-test-stl.py" /tmp/cube.stl 20

echo "== push + run on emulator =="
$ADB push "$BIN" /data/local/tmp/sla_cli >/dev/null 2>&1 && $ADB shell chmod +x /data/local/tmp/sla_cli
$ADB push /tmp/cube.stl /data/local/tmp/cube.stl >/dev/null 2>&1
$ADB shell 'cd /data/local/tmp && ./sla_cli cube.stl out.sl1 0.05 2.5; echo RC=$?'
echo "== result =="
$ADB shell 'ls -la /data/local/tmp/out.sl1' 2>&1
$ADB pull /data/local/tmp/out.sl1 /tmp/out.sl1 >/dev/null 2>&1 && { echo "pulled:"; ls -la /tmp/out.sl1; file /tmp/out.sl1; }
