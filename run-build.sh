#!/usr/bin/env bash
# Mobile-slicer Phase-1 engine build. Detached job for the build host.
# - caps parallelism (low-RAM RAM box) - logs to build-run.log - writes BUILD.DONE marker
set -uo pipefail
ROOT="$HOME/Projects/mobile-slicer-src"
LOG="$ROOT/build-run.log"
: > "$LOG"
exec > >(tee -a "$LOG") 2>&1

echo "=== mobile-slicer engine build start: $(date) ==="
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export ANDROID_NDK_ROOT="$HOME/android-sdk/ndk/27.2.12479018"
export ORCAMICRO_JOBS=4              # cap for the low-RAM RAM host (avoid OOM)
echo "NDK=$ANDROID_NDK_ROOT  JOBS=$ORCAMICRO_JOBS  cores=$(nproc)"
echo "disk:"; df -h "$HOME" | tail -1

cd "$ROOT/native/scripts" || { echo "no scripts dir" > "$ROOT/BUILD.DONE"; exit 1; }

rc_fetch=0; rc_deps=0; rc_eng=0
echo ">>> fetch-orca.sh"; ./fetch-orca.sh; rc_fetch=$?; echo "fetch rc=$rc_fetch"
if [ $rc_fetch -eq 0 ]; then
  echo ">>> build-deps.sh $(date)"; ./build-deps.sh; rc_deps=$?; echo "deps rc=$rc_deps"
  if [ $rc_deps -eq 0 ]; then
    echo ">>> build-engine.sh $(date)"; ./build-engine.sh ../../app/src/main/jniLibs; rc_eng=$?; echo "engine rc=$rc_eng"
  fi
fi

echo "fetch=$rc_fetch deps=$rc_deps engine=$rc_eng" > "$ROOT/BUILD.DONE"
echo "=== build end: $(date) ==="
if [ $rc_eng -eq 0 ] && [ -f "$ROOT/app/src/main/jniLibs/arm64-v8a/liborcamicro_engine.so" ]; then
  echo "RESULT: SUCCESS $(du -h "$ROOT/app/src/main/jniLibs/arm64-v8a/liborcamicro_engine.so" | cut -f1)"
else
  echo "RESULT: FAILED"
fi
