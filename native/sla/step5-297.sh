#!/usr/bin/env bash
# Build PrusaSlicer 2.9.7 (newest) libslic3r for Android arm64.
set +e
R="$HOME/Projects/mobile-slicer-src"; CM="$HOME/.local/opt/cmake-3.30.5-linux-x86_64"
export ANDROID_NDK_ROOT="$HOME/android-sdk/ndk/27.2.12479018" ORCAMICRO_PREFIX="$R/native/build/prefix-sla"
rm -f "$R/native/build/LIBSLIC3R297.DONE"
"$CM/bin/cmake" --build /tmp/prusa297-cfg --target libslic3r -j 4 > "$R/native/build/libslic3r297.log" 2>&1
echo "BUILD_RC=$?" >> "$R/native/build/libslic3r297.log"
echo done > "$R/native/build/LIBSLIC3R297.DONE"
