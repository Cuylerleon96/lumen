# Lumen

An open-source **mobile 3D-print slicer for both FDM and resin (MSLA)** — on-device, offline, no
cloud, no account.

The gap this fills: every existing mobile slicer port is **FDM-only**, and the mature resin slicers
(Lychee, Chitubox, VoxelDance) are closed-source. Lumen is one app that does both, with
FDM / resin / experimental methods as **pluggable slicing backends** behind a single plate / settings /
preview UI.

- **License:** **AGPL-3.0-or-later** (inherited from the engines — non-negotiable; see
  `docs/research/licenses.md`).
- **Platform:** Android (arm64-v8a), min SDK 26, Kotlin + Jetpack Compose + OpenGL ES.
- **Package:** `dev.lumen.slicer`.

## Status

**Working app, v0.7.1, running on a Pixel 6.** Both engines are cross-compiled and shipped as
prebuilt `.so`s; the app builds, slices and exports. This is past the prototype stage — the in-app
updater, project save/load, the profile catalog and the format writers are all in use.

- **20 features done** — estimation · resin placement (move/rotate/scale/mirror/fit) · resin profile
  DB · island detection · resin-trap detection · layer repair (speck + pinhole) · OBJ import ·
  3MF import · anti-aliasing · **CTB writer** · **GOO writer** (+ SL1→CTB / SL1→GOO converters) ·
  output-format picker · hollowing · support settings · project save/load · calibration generator ·
  model library/recents · real RGB565 previews · Elegoo Saturn (4K) + Saturn 2 (8K) profiles.
- **In progress** — **drain holes**: the boolean CSG (`Csg`) and the drilling (`DrainHoles`) are done
  and tested in `:core`; the tap-to-place touch UI is not.
- **Tracker:** `docs/FEATURES.md` is the single source of truth for feature status, with the
  remaining work ordered. `docs/HANDOFF.md` is the session-to-session brief.
- **Tests:** 91 passing tests in `:core` (`./gradlew -PcoreOnly :core:test`); 2 more run only when
  `-PorcaResourcesDir=` points at an OrcaSlicer profile checkout.

## Engines

Both engines are AGPL, cross-compiled for `arm64-v8a` with **NDK r27c**, and shipped prebuilt in
`app/src/main/jniLibs/arm64-v8a/` so a normal app build needs no NDK:

- **FDM — OrcaSlicer 2.4.2 `libslic3r`** → `liborcamicro_engine.so` (JNI shim `native/bridge/jni.cpp`).
- **Resin — PrusaSlicer 2.9.7 SLA `libslic3r`** → `libsla_engine.so` (JNI shim `native/sla/sla_jni.cpp`).

Two engines rather than one grafted fork (ADR 0003-B): Bambu Studio stripped the SLA sources before
OrcaSlicer forked, so grafting PrusaSlicer's `SLAPrint` back into Orca's `libslic3r` means
reconciling the whole Orca↔Prusa type drift. A second engine is far less work, and both are AGPL so
vendoring is licensed. Each engine runs **in its own process**, so a native crash cannot take the UI
down.

## Repo layout

```
app/      Kotlin + Jetpack Compose UI (plate, settings, preview, updater)
core/     pure Kotlin, JVM-unit-tested: mesh, placement, CSG, resin pipeline,
          settings model, profile catalog, G-code parsing
native/   engine cross-builds + JNI bridges (bridge/ = FDM, sla/ = resin)
docs/     FEATURES.md (tracker) · HANDOFF.md · architecture · adr/ · research/
server/   LAN update server + publish script
update/   update manifest (info.json) + the published APK
```

## Building

The engine cross-builds are heavy (tens of GB, hours) and want a many-core host with a real disk;
they are already done, and their outputs ship as prebuilt `.so`s. Day-to-day app builds:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
ANDROID_HOME=$HOME/android-sdk ANDROID_SDK_ROOT=$HOME/android-sdk \
./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx3072m \
  -PorcaResourcesDir="$PWD/native/build/orcaslicer/resources" :core:test assembleDebug
```

- **Pure-logic work** needs neither the NDK nor the Android SDK: `./gradlew -PcoreOnly :core:test`
  builds and tests just `:core` in about 20 seconds.
- **Engine rebuilds** live in `native/` and are documented in `docs/HANDOFF.md`; the pinned NDK is
  `27.2.12479018`.

## Lineage & credits

Lumen descends from **`whoxllm/OrcaMicro`** (OrcaSlicer on Android), which in turn builds on
**OrcaSlicer** → **Bambu Studio** → **PrusaSlicer** → **Slic3r**. Format writers are **clean-room**
implementations from open specifications, with **UVTools** (GPL) and **catibo** used as references for
byte layouts — never copied. The optional Bambu network plugin is deliberately **excluded**: it is
built on non-free BambuLab libraries.

Everything here is AGPL-3.0-or-later. If you run a modified Lumen as a network service, the AGPL
requires you to offer the source.

## Roadmap

The ordered remaining work lives in `docs/FEATURES.md`. Headline gaps: drain-hole placement UI,
interactive support add/edit/paint, the remaining output formats (PW0/PWS/PhotonS/CBDDLP), the touch
tool-rail + loupe, and FDM calibration towers / G-code post-processing.
