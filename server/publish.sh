#!/usr/bin/env bash
# Build the APK and publish it for in-app update. Run on the build host (the build host),
# against the build tree (default ~/Projects/mobile-slicer-src). Each run bumps versionCode.
# Usage: server/publish.sh [output_dir]
set -euo pipefail
ROOT="${MOBILE_SLICER_ROOT:-$HOME/Projects/mobile-slicer-src}"
OUT="${1:-$HOME/mslicer_updates}"

# JDK 17
if [ -z "${JAVA_HOME:-}" ]; then
  for j in /usr/lib/jvm/java-17-openjdk /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17; do
    [ -x "$j/bin/javac" ] && export JAVA_HOME="$j" && break
  done
fi
[ -n "${JAVA_HOME:-}" ] || { echo "No JDK 17 found; set JAVA_HOME"; exit 1; }
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

mkdir -p "$OUT/update"

# Next version code = previous + 1.
PREV=$(python3 - "$OUT/update/info.json" <<'PY' 2>/dev/null || echo 0
import json,sys
try: print(json.load(open(sys.argv[1])).get("versionCode", 0))
except Exception: print(0)
PY
)
CODE=$((PREV + 1))
VER="0.1.$CODE"

cd "$ROOT"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
ORCAMICRO_VERSION_CODE="$CODE" JAVA_HOME="$JAVA_HOME" \
  ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx3072m -Dkotlin.daemon.jvmargs=-Xmx1024m \
  -PorcaResourcesDir="$ROOT/native/build/orcaslicer/resources" assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "APK not found: $APK"; exit 1; }

cp "$APK" "$OUT/update/app.apk"
SHA=$(sha256sum "$OUT/update/app.apk" | cut -d' ' -f1)
cat > "$OUT/update/info.json" <<EOF
{"version": "$VER", "versionCode": $CODE, "sha256": "$SHA", "notes": "build $(date -Iseconds)"}
EOF

HOST=$(hostname -I | awk '{print $1}')
echo "published v$VER ($CODE) sha=$SHA"
echo "serve:  python3 $ROOT/server/update_server.py --dir $OUT --port 8799"
echo "app update URL -> http://$HOST:8799   (or the Tailscale address)"
