#!/usr/bin/env bash
# Build the APK, write the in-app update manifest, and stage the artifacts.
# Run on a build host against the build tree (default ~/Projects/mobile-slicer-src).
#
# Usage: server/publish.sh <version> [lan_out_dir]
#   server/publish.sh 0.7.2
#
# Outputs:
#   <repo>/update/info.json   COMMITTED. The manifest the app fetches from GitHub (raw master).
#   <repo>/update/app.apk     gitignored local copy (the LAN update server serves this one).
#   <lan_out_dir>/update/*    the same two files, for `server/update_server.py` on the tailnet.
#
# Then, from a machine that can reach github.com (a dev box may not, hence the split):
#   1. commit update/info.json and push
#   2. lumen-release v<version> <commit-sha> <notes.md>    (uploads the APK as a release asset)
#
# versionCode always increments: the app installs only when the served code is GREATER than the
# installed one, so re-publishing the same code is a silent no-op.
set -euo pipefail
ROOT="${MOBILE_SLICER_ROOT:-$HOME/Projects/mobile-slicer-src}"
VER="${1:?version, e.g. 0.7.2 (no leading v)}"
OUT="${2:-$HOME/mslicer_updates}"
TAG="v$VER"
REPO_SLUG="Cuylerleon96/lumen"

# JDK 17
if [ -z "${JAVA_HOME:-}" ]; then
  for j in /usr/lib/jvm/java-17-openjdk /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17; do
    [ -x "$j/bin/javac" ] && export JAVA_HOME="$j" && break
  done
fi
[ -n "${JAVA_HOME:-}" ] || { echo "No JDK 17 found; set JAVA_HOME"; exit 1; }
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# versionCode = previous + 1, read from the committed manifest (the source of truth).
PREV=$(python3 - "$ROOT/update/info.json" <<'PY' 2>/dev/null || echo 0
import json,sys
try: print(json.load(open(sys.argv[1])).get("versionCode", 0))
except Exception: print(0)
PY
)
CODE=$((PREV + 1))

cd "$ROOT"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
ORCAMICRO_VERSION_CODE="$CODE" ORCAMICRO_VERSION_NAME="$VER" JAVA_HOME="$JAVA_HOME" \
  ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx3072m -Dkotlin.daemon.jvmargs=-Xmx1024m \
  -PorcaResourcesDir="$ROOT/native/build/orcaslicer/resources" assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "APK not found: $APK"; exit 1; }

mkdir -p "$ROOT/update" "$OUT/update"
cp "$APK" "$ROOT/update/app.apk"
cp "$APK" "$OUT/update/app.apk"
SHA=$(sha256sum "$ROOT/update/app.apk" | cut -d' ' -f1)

python3 - "$ROOT/update/info.json" "$VER" "$CODE" "$SHA" "$TAG" "$REPO_SLUG" <<'PY'
import json, sys
path, ver, code, sha, tag, slug = sys.argv[1:7]
json.dump({
    "version": ver,
    "versionCode": int(code),
    "sha256": sha,
    "url": f"https://github.com/{slug}/releases/download/{tag}/app.apk",
    "release": f"https://github.com/{slug}/releases/tag/{tag}",
    "channel": "preview",
    "notes": f"build {ver} ({code})",
}, open(path, "w"), indent=2)
open(path, "a").write("\n")
PY
cp "$ROOT/update/info.json" "$OUT/update/info.json"

echo "built and staged v$VER (versionCode $CODE) sha=$SHA"
echo
echo "next, from a machine with GitHub access:"
echo "  git -C ~/Projects/mobile-slicer add update/info.json && git commit -m 'release: v$VER' && lumen-push"
echo "  lumen-release $TAG <commit-sha> <notes.md>   # uploads app.apk to the GitHub release"
echo
echo "LAN server (optional, offline testing):"
echo "  python3 $ROOT/server/update_server.py --dir $OUT --port 8799"
