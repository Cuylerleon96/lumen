#!/usr/bin/env bash
# SLA graft step 3: OpenVDB with internal half + patch PrusaSlicer's FindOpenVDB to accept Imath::Half,
# then rebuild OpenVDB and re-configure libslic3r.
set +e
R="$HOME/Projects/mobile-slicer-src"; PRE="$R/native/build/prefix-sla"; P="$R/native/build/prusaslicer"
CM="$HOME/.local/opt/cmake-3.30.5-linux-x86_64"
export ANDROID_NDK_ROOT="$HOME/android-sdk/ndk/27.2.12479018" ORCAMICRO_PREFIX="$PRE" ORCAMICRO_JOBS=4

echo "=== patch FindOpenVDB (Imath fallback) ==="
python3 - "$P/cmake/modules/FindOpenVDB.cmake" <<'PY'
import sys
p=sys.argv[1]; s=open(p).read()
old="find_package(IlmBase QUIET)\nif(NOT IlmBase_FOUND)\n  pkg_check_modules(IlmBase QUIET IlmBase)\nendif()\n"
add="if (NOT IlmBase_FOUND)\n  find_package(Imath QUIET)\n  if (Imath_FOUND AND TARGET Imath::Half)\n    add_library(IlmBase::Half INTERFACE IMPORTED)\n    target_link_libraries(IlmBase::Half INTERFACE Imath::Half)\n    set(IlmBase_FOUND TRUE)\n  endif()\nendif()\n"
if add in s: print("already patched")
elif old in s: open(p,"w").write(s.replace(old, old+add)); print("patched")
else: print("PATTERN NOT FOUND")
PY

echo "=== rebuild openvdb (internal half) ==="
bash "$R/native/sla/build-sla-deps.sh" openvdb

echo "=== configure libslic3r ==="
rm -rf /tmp/prusa-cfg
"$CM/bin/cmake" -S "$P" -B /tmp/prusa-cfg -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$R/native/cmake/android-arm64.toolchain.cmake" -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="$PRE" -DOPENVDB_ROOT="$PRE" \
  -DSLIC3R_GUI=OFF -DSLIC3R_STATIC=ON -DSLIC3R_PCH=OFF -DCMAKE_POLICY_VERSION_MINIMUM=3.5
echo "CONFIGURE_RC=$?"
echo done > "$R/native/build/SLA-CFG.DONE"
