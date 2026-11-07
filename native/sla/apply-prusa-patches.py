#!/usr/bin/env python3
# Applies the Android/SLA cross-compile patches to a PrusaSlicer checkout (any 2.8/2.9 tag).
# Idempotent. usage: apply-prusa-patches.py <prusaslicer-dir>
import os, re, sys

P = sys.argv[1]

def edit(path, fn):
    full = os.path.join(P, path)
    if not os.path.isfile(full):
        print(f"  skip (missing): {path}"); return
    s = open(full).read(); n = fn(s)
    if n != s:
        open(full, "w").write(n); print(f"  patched: {path}")
    else:
        print(f"  no change: {path}")

def top(s):
    # 1. Guard OpenGL behind the GUI flag (headless SLA build has no GL).
    s = s.replace('set(OpenGL_GL_PREFERENCE "LEGACY")\nfind_package(OpenGL REQUIRED)',
                  'if (SLIC3R_GUI)\n    set(OpenGL_GL_PREFERENCE "LEGACY")\n    find_package(OpenGL REQUIRED)\nendif ()')
    # 2. Guard GLEW the same way.
    s = re.sub(r'(?m)^(\s*)find_package\(GLEW REQUIRED\)',
               r'\1if (SLIC3R_GUI)\n\1    find_package(GLEW REQUIRED)\n\1endif ()', s)
    # 3. Honor CMAKE_CROSSCOMPILING (disables the host encoding-check that can't run on x86_64).
    if 'if (CMAKE_CROSSCOMPILING)' not in s:
        s = s.replace('set(IS_CROSS_COMPILE FALSE)\n',
                      'set(IS_CROSS_COMPILE FALSE)\nif (CMAKE_CROSSCOMPILING)\n    set(IS_CROSS_COMPILE TRUE)\nendif ()\n', 1)
    return s

def vdb(s):
    # Accept Imath::Imath as IlmBase::Half when IlmBase (OpenEXR-2.x naming) is absent.
    old = "find_package(IlmBase QUIET)\nif(NOT IlmBase_FOUND)\n  pkg_check_modules(IlmBase QUIET IlmBase)\nendif()\n"
    add = ("if (NOT IlmBase_FOUND)\n  find_package(Imath QUIET)\n  if (Imath_FOUND AND TARGET Imath::Imath)\n"
           "    add_library(IlmBase::Half INTERFACE IMPORTED)\n"
           "    target_link_libraries(IlmBase::Half INTERFACE Imath::Imath)\n"
           "    set(IlmBase_FOUND TRUE)\n  endif()\nendif()\n")
    if old in s and add not in s:
        s = s.replace(old, old + add)
    # Force ABI 11 to match the OpenVDB 11 we build (vdb_print isn't built to infer it).
    s = s.replace('OPENVDB_ABI_VERSION_NUMBER=${OpenVDB_ABI}', 'OPENVDB_ABI_VERSION_NUMBER=11')
    return s

def occt(s):
    return re.sub(r'find_package\(OpenCASCADE [0-9.]+ REQUIRED\)', 'find_package(OpenCASCADE REQUIRED)', s)

def src_cmake(s):
    # libseqarrange (2.9+) needs Z3 and is only linked into the GUI app, never libslic3r — skip it.
    return s.replace('add_subdirectory(libseqarrange)',
                     '# add_subdirectory(libseqarrange)  # disabled: needs Z3, GUI-only')

edit("CMakeLists.txt", top)
edit("cmake/modules/FindOpenVDB.cmake", vdb)
edit("src/occt_wrapper/CMakeLists.txt", occt)
edit("src/CMakeLists.txt", src_cmake)
print("done")
