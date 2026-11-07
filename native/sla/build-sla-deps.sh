#!/usr/bin/env bash
# PrusaSlicer-SLA engine dependencies for Android arm64 (ADR 0003-B: the resin engine is its own
# cross-compiled libslic3r, so it gets its OWN dep set/versions in a separate prefix).
#
# Reuses the OrcaMicro toolchain + helper patterns, but installs into $WORK_DIR/prefix-sla.
# Only what the SLA pipeline needs: boost, tbb, eigen, gmp, mpfr, cgal, cereal, nlopt, expat,
# png, jpeg, freetype, openvdb (hollowing). No OCCT/OpenCV/draco (those are FDM geometry).
#
# usage: build-sla-deps.sh [library...]   (default: all)
set -euo pipefail
NATIVE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$NATIVE_DIR/scripts/common.sh"
export ORCAMICRO_PREFIX="$WORK_DIR/prefix-sla"
require_ndk

CC_TARGET="$NDK_BIN/aarch64-linux-android${ANDROID_API}-clang"
CXX_TARGET="$NDK_BIN/aarch64-linux-android${ANDROID_API}-clang++"
mkdir -p "$DOWNLOAD_DIR" "$SOURCE_DIR" "$BUILD_DIR" "$ORCAMICRO_PREFIX/.stamps"
log() { echo "==> $*" >&2; }
die() { echo "error: $*" >&2; exit 1; }

fetch() { # fetch <name> <url>  (no checksum: SLA deps are upstream releases)
    local name=$1 url=$2
    local file="$DOWNLOAD_DIR/$name-$(basename "$url")"
    [ -f "$file" ] || { log "Downloading $name"; curl -fsSL --retry 5 --retry-delay 10 -o "$file.part" "$url"; mv "$file.part" "$file"; }
    echo "$file"
}
unpack() {
    local archive=$1 dest="$SOURCE_DIR/$2" tmp="$SOURCE_DIR/$2.unpack"
    rm -rf "$dest" "$tmp"; mkdir -p "$tmp"
    case "$archive" in *.zip) unzip -q "$archive" -d "$tmp";; *) tar -xf "$archive" -C "$tmp";; esac
    local e=("$tmp"/*); if [ "${#e[@]}" -eq 1 ] && [ -d "${e[0]}" ]; then mv "${e[0]}" "$dest"; rm -rf "$tmp"; else mv "$tmp" "$dest"; fi
    echo "$dest"
}
cmake_build() {
    local name=$1 src=$2; shift 2; local bld="$BUILD_DIR/sla-$name"; rm -rf "$bld"
    cmake -S "$src" -B "$bld" -G Ninja -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$ORCAMICRO_PREFIX" \
        -DCMAKE_PREFIX_PATH="$ORCAMICRO_PREFIX" -DBUILD_SHARED_LIBS=OFF \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DCMAKE_POLICY_VERSION_MINIMUM=3.5 "$@"
    cmake --build "$bld" -j "$JOBS"; cmake --install "$bld"; rm -rf "$bld"
    case "$src" in "$SOURCE_DIR"/*) rm -rf "$src";; esac
}
autotools_env() {
    export CC="$CC_TARGET" CXX="$CXX_TARGET" AR="$NDK_BIN/llvm-ar" RANLIB="$NDK_BIN/llvm-ranlib" \
        NM="$NDK_BIN/llvm-nm" STRIP="$NDK_BIN/llvm-strip" CFLAGS="-O2 -DNDEBUG -fPIC -DPIC" CXXFLAGS="-O2 -DNDEBUG -fPIC -DPIC"
}

build_eigen()  { cmake_build eigen  "$(unpack "$(fetch eigen https://gitlab.com/libeigen/eigen/-/archive/3.4.0/eigen-3.4.0.tar.gz)" eigen)" -DBUILD_TESTING=OFF -DEIGEN_BUILD_DOC=OFF -DEIGEN_BUILD_PKGCONFIG=OFF; }
build_expat()  { cmake_build expat  "$(unpack "$(fetch expat https://github.com/libexpat/libexpat/releases/download/R_2_6_0/expat-2.6.0.tar.gz)" expat)" -DEXPAT_BUILD_TOOLS=OFF -DEXPAT_BUILD_EXAMPLES=OFF -DEXPAT_BUILD_TESTS=OFF; }
build_png()    { cmake_build png    "$(unpack "$(fetch png https://github.com/glennrp/libpng/archive/refs/tags/v1.6.35.zip)" png)" -DPNG_SHARED=OFF -DPNG_STATIC=ON -DPNG_TESTS=OFF -DPNG_ARM_NEON=off; }
build_jpeg()   { cmake_build jpeg   "$(unpack "$(fetch jpeg https://github.com/libjpeg-turbo/libjpeg-turbo/archive/refs/tags/3.0.1.zip)" jpeg)" -DENABLE_SHARED=OFF -DENABLE_STATIC=ON -DWITH_JPEG8=ON -DWITH_TURBOJPEG=OFF -DWITH_SIMD=OFF -DCMAKE_INSTALL_LIBDIR:PATH=lib; }
build_freetype(){ cmake_build freetype "$(unpack "$(fetch freetype https://download.savannah.gnu.org/releases/freetype/freetype-2.13.2.tar.gz)" freetype)" -DFT_DISABLE_ZLIB=TRUE -DFT_DISABLE_BZIP2=TRUE -DFT_DISABLE_PNG=TRUE -DFT_DISABLE_HARFBUZZ=TRUE -DFT_DISABLE_BROTLI=TRUE; }
build_boost()  { cmake_build boost  "$(unpack "$(fetch boost https://github.com/boostorg/boost/releases/download/boost-1.84.0/boost-1.84.0.tar.gz)" boost)" "-DBOOST_EXCLUDE_LIBRARIES=contract;fiber;numpy;stacktrace;wave;test;cobalt;python;mpi;graph_parallel" -DBOOST_LOCALE_ENABLE_ICU=OFF -DBUILD_TESTING=OFF -DBOOST_IOSTREAMS_ENABLE_BZIP2=OFF -DBOOST_IOSTREAMS_ENABLE_ZSTD=OFF -DBOOST_IOSTREAMS_ENABLE_LZMA=OFF; }
build_tbb()    { cmake_build tbb    "$(unpack "$(fetch tbb https://github.com/oneapi-src/oneTBB/archive/refs/tags/v2021.5.0.zip)" tbb)" -DANDROID_DISABLE_FORMAT_STRING_CHECKS=ON -DTBB_TEST=OFF -DTBB_EXAMPLES=OFF -DTBB_STRICT=OFF -DTBBMALLOC_BUILD=ON -DTBB_ENABLE_IPO=OFF -DTBB_DISABLE_HWLOC_AUTOMATIC_SEARCH=ON -DCMAKE_DEBUG_POSTFIX=_debug -DCMAKE_INSTALL_LIBDIR=lib; }
build_gmp()    { local s; s=$(unpack "$(fetch gmp https://gmplib.org/download/gmp/gmp-6.3.0.tar.xz)" gmp); ( cd "$s"; autotools_env; ./configure --host=aarch64-linux-android --prefix="$ORCAMICRO_PREFIX" --enable-static --disable-shared --enable-cxx --with-pic; make -j "$JOBS"; make install ); rm -rf "$s"; }
build_mpfr()   { local s; s=$(unpack "$(fetch mpfr https://ftp.gnu.org/gnu/mpfr/mpfr-4.2.2.tar.bz2)" mpfr); ( cd "$s"; autotools_env; ./configure --host=aarch64-linux-android --prefix="$ORCAMICRO_PREFIX" --enable-static --disable-shared --with-pic --with-gmp="$ORCAMICRO_PREFIX"; make -j "$JOBS"; make install ); rm -rf "$s"; }
build_cgal()   { cmake_build cgal   "$(unpack "$(fetch cgal https://github.com/CGAL/cgal/releases/download/v5.6.3/CGAL-5.6.3.zip)" cgal)" -DWITH_CGAL_Qt5=OFF -DCGAL_DISABLE_GMP=OFF; }
build_cereal() { cmake_build cereal "$(unpack "$(fetch cereal https://github.com/USCiLab/cereal/archive/refs/tags/v1.3.0.zip)" cereal)" -DJUST_INSTALL_CEREAL=ON -DSKIP_PERFORMANCE_COMPARISON=ON -DBUILD_TESTS=OFF; }
build_nlopt()  { cmake_build nlopt  "$(unpack "$(fetch nlopt https://github.com/stevengj/nlopt/archive/v2.5.0.tar.gz)" nlopt)" -DNLOPT_PYTHON=OFF -DNLOPT_OCTAVE=OFF -DNLOPT_MATLAB=OFF -DNLOPT_GUILE=OFF -DNLOPT_SWIG=OFF -DNLOPT_TESTS=OFF -DCMAKE_INSTALL_LIBDIR=lib; }
build_openvdb(){ cmake_build openvdb "$(unpack "$(fetch openvdb https://github.com/AcademySoftwareFoundation/openvdb/archive/refs/tags/v11.0.0.tar.gz)" openvdb)" -DOPENVDB_BUILD_CORE=ON -DOPENVDB_CORE_SHARED=OFF -DOPENVDB_CORE_STATIC=ON -DOPENVDB_BUILD_BINARIES=OFF -DOPENVDB_BUILD_UNITTESTS=OFF -DOPENVDB_BUILD_PYTHON_MODULE=OFF -DOPENVDB_BUILD_NANOVDB=OFF -DOPENVDB_BUILD_VDB_LOD=OFF -DOPENVDB_BUILD_VDB_RAYSPP=OFF -DOPENVDB_BUILD_VDB_RENDER=OFF -DOPENVDB_USE_IMATH_HALF=OFF -DUSE_BLOSC=OFF -DUSE_ZLIB=ON -DCMAKE_INSTALL_LIBDIR=lib; }
build_imath()  { cmake_build imath "$(unpack "$(fetch imath https://github.com/AcademySoftwareFoundation/Imath/archive/refs/tags/v3.1.9.tar.gz)" imath)" -DIMATH_BUILD_TESTS=OFF -DIMATH_BUILD_EXAMPLES=OFF -DIMATH_INSTALL_PKG_CONFIG=OFF -DBUILD_SHARED_LIBS=OFF -DCMAKE_INSTALL_LIBDIR=lib; }
build_qhull()  { cmake_build qhull "$(unpack "$(fetch qhull http://www.qhull.org/download/qhull-2020-src-8.0.2.tgz)" qhull)" -DINCLUDE_INSTALL_DIR="$ORCAMICRO_PREFIX/include" -DLIB_INSTALL_DIR="$ORCAMICRO_PREFIX/lib" -DBUILD_SHARED_LIBS=OFF -DCMAKE_INSTALL_LIBDIR=lib; }
build_libbgcode(){ cmake_build libbgcode "$(unpack "$(fetch libbgcode https://github.com/prusa3d/libbgcode/archive/b5c57c423c958a78dacae468aeee63ab3d2de947.zip)" libbgcode)" -DLibBGCode_BUILD_TESTS=OFF -DLibBGCode_BUILD_CMD_TOOL=OFF -DLibBGCode_BUILD_SHARED=OFF -DLibBGCode_BUILD_STATIC=ON -DCMAKE_INSTALL_LIBDIR=lib; }
build_blosc()  { cmake_build blosc "$(unpack "$(fetch blosc https://github.com/Blosc/c-blosc/archive/refs/tags/v1.21.5.tar.gz)" blosc)" -DBUILD_TESTS=OFF -DBUILD_BENCHMARKS=OFF -DBUILD_SHARED=OFF -DBUILD_STATIC=ON -DPREFER_EXTERNAL_ZLIB=ON -DCMAKE_INSTALL_LIBDIR=lib; }
build_nanosvg(){ local s; s=$(unpack "$(fetch nanosvg https://github.com/fltk/nanosvg/archive/abcd277ea45e9098bed752cf9c6875b533c0892f.zip)" nanosvg); mkdir -p "$ORCAMICRO_PREFIX/include/nanosvg"; cp "$s"/src/*.h "$ORCAMICRO_PREFIX/include/nanosvg/"; rm -rf "$s"; }
apply_patches() { local src=$1; shift; for p in "$@"; do log "Patching with $(basename "$p")"; patch -d "$src" -p1 --forward --ignore-whitespace --silent < "$p"; done; }
build_occt() {
    local src; src=$(unpack "$(fetch occt https://github.com/Open-Cascade-SAS/OCCT/archive/refs/tags/V7_6_0.zip)" occt)
    apply_patches "$src" "$WORK_DIR/orcaslicer/deps/OCCT/0001-OCCT-fix.patch"
    cmake_build occt "$src" -DCMAKE_CXX_STANDARD=17 -DBUILD_LIBRARY_TYPE=Static \
        -DUSE_TK=OFF -DUSE_TBB=OFF -DUSE_FFMPEG=OFF -DUSE_VTK=OFF -DUSE_FREEIMAGE=OFF -DUSE_RAPIDJSON=OFF \
        -DUSE_OPENGL=OFF -DUSE_GLES2=OFF -DUSE_D3D=OFF -DBUILD_DOC_Overview=OFF \
        -DBUILD_MODULE_ApplicationFramework=OFF -DBUILD_MODULE_Draw=OFF -DBUILD_MODULE_FoundationClasses=OFF \
        -DBUILD_MODULE_ModelingAlgorithms=OFF -DBUILD_MODULE_ModelingData=OFF -DBUILD_MODULE_Visualization=OFF \
        -DINSTALL_DIR_LAYOUT=Unix -DINSTALL_DIR_BIN=bin -DINSTALL_DIR_LIB=lib -DINSTALL_DIR_INCLUDE=include/opencascade \
        -DINSTALL_DIR_CMAKE=lib/cmake/opencascade -DINSTALL_DIR_RESOURCE=share/opencascade/resources \
        -DINSTALL_DIR_DATA=share/opencascade/data -DINSTALL_DIR_SAMPLES=share/opencascade/samples \
        -DINSTALL_DIR_TESTS=share/opencascade/tests -DINSTALL_DIR_DOC=share/doc/opencascade -DINSTALL_DIR_SCRIPT=bin \
        -D3RDPARTY_DIR="$ORCAMICRO_PREFIX" -D3RDPARTY_FREETYPE_DIR="$ORCAMICRO_PREFIX" \
        -D3RDPARTY_FREETYPE_INCLUDE_DIR_ft2build="$ORCAMICRO_PREFIX/include/freetype2" \
        -D3RDPARTY_FREETYPE_INCLUDE_DIR_freetype2="$ORCAMICRO_PREFIX/include/freetype2" \
        -D3RDPARTY_FREETYPE_LIBRARY="$ORCAMICRO_PREFIX/lib/libfreetype.a" -D3RDPARTY_FREETYPE_LIBRARY_DIR="$ORCAMICRO_PREFIX/lib"
}
build_curl()   { local s; s=$(unpack "$(fetch curl https://curl.se/download/curl-8.6.0.tar.gz)" curl); ( cd "$s"; autotools_env; ./configure --host=aarch64-linux-android --prefix="$ORCAMICRO_PREFIX" --disable-shared --enable-static --without-ssl --without-libidn2 --without-zstd --without-brotli --without-libpsl --without-nghttp2 --disable-ldap --disable-threaded-resolver --with-zlib="$ORCAMICRO_PREFIX"; make -j "$JOBS"; make install ); rm -rf "$s"; }

ALL=(eigen expat png jpeg freetype boost tbb gmp mpfr cgal cereal nlopt curl imath qhull libbgcode blosc nanosvg openvdb occt)
run() { local n=$1 stamp="$ORCAMICRO_PREFIX/.stamps/sla-$1"
    local key; key=$({ declare -f "build_$n"; cat "$TOOLCHAIN_FILE"; } | sha256sum | cut -c1-16)
    [ "$(cat "$stamp" 2>/dev/null || true)" = "$key" ] && { log "$n: up to date"; return; }
    log "Building $n"; local t=$SECONDS; "build_$n"; echo "$key" > "$stamp"; log "$n: done in $((SECONDS-t))s"; }

deps=("$@"); [ "${#deps[@]}" -gt 0 ] || deps=("${ALL[@]}")
for d in "${deps[@]}"; do run "$d"; done
log "SLA dependencies installed in $ORCAMICRO_PREFIX"
