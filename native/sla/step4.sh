#!/usr/bin/env bash
# SLA graft step 4: build OCCT (STEP import dep), then re-configure libslic3r.
set +e
R="$HOME/Projects/mobile-slicer-src"; PRE="$R/native/build/prefix-sla"; P="$R/native/build/prusaslicer"
CM="$HOME/.local/opt/cmake-3.30.5-linux-x86_64"
export ANDROID_NDK_ROOT="$HOME/android-sdk/ndk/27.2.12479018" ORCAMICRO_PREFIX="$PRE" ORCAMICRO_JOBS=4
echo "=== building occt ==="
bash "$R/native/sla/build-sla-deps.sh" occt
echo "=== configure libslic3r ==="
rm -rf /tmp/prusa-cfg
"$CM/bin/cmake" -S "$P" -B /tmp/prusa-cfg -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$R/native/cmake/android-arm64.toolchain.cmake" -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="$PRE" -DOPENVDB_ROOT="$PRE" \
  -DSLIC3R_GUI=OFF -DSLIC3R_STATIC=ON -DSLIC3R_PCH=OFF -DSLIC3R_BUILD_TESTS=OFF -DIS_CROSS_COMPILE=ON -DCMAKE_POLICY_VERSION_MINIMUM=3.5
echo "CONFIGURE_RC=$?"
echo done > "$R/native/build/SLA-CFG.DONE"
