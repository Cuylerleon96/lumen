#!/usr/bin/env bash
# Build PrusaSlicer 2.9.7 (newest) libslic3r for Android arm64 — into a DISK-BACKED build dir.
# (The host's /tmp is a 3.9G tmpfs; a big build there dies with "No space left on device".)
set +e
R="$HOME/Projects/mobile-slicer-src"; CM="$HOME/.local/opt/cmake-3.30.5-linux-x86_64"
P="$R/native/build/prusaslicer-2.9.7"; PRE="$R/native/build/prefix-sla"; CFG="$R/native/build/prusa297-cfg"
export ANDROID_NDK_ROOT="$HOME/android-sdk/ndk/27.2.12479018" ORCAMICRO_PREFIX="$PRE"
rm -f "$R/native/build/LIBSLIC3R297.DONE"

"$CM/bin/cmake" -S "$P" -B "$CFG" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$R/native/cmake/android-arm64.toolchain.cmake" \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_PREFIX_PATH="$PRE" -DOPENVDB_ROOT="$PRE" \
  -DSLIC3R_GUI=OFF -DSLIC3R_STATIC=ON -DSLIC3R_PCH=OFF -DSLIC3R_BUILD_TESTS=OFF \
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5 > "$R/native/build/libslic3r297.log" 2>&1
echo "CMAKE_RC=$?" >> "$R/native/build/libslic3r297.log"

"$CM/bin/cmake" --build "$CFG" --target libslic3r -j 4 > "$R/native/build/libslic3r297-build.log" 2>&1
echo "BUILD_RC=$?" >> "$R/native/build/libslic3r297-build.log"
echo done > "$R/native/build/LIBSLIC3R297.DONE"
