# Next steps — staged plan (RESUME HERE)

Status snapshot (2026-11-07): **SLA engine WORKS.** PrusaSlicer **2.9.7** (newest) `libslic3r` +
`liblibseqarrange` cross-compiled for arm64 (deps incl. OpenVDB 11, OCCT, **Z3**, nlohmann_json). The
`native/sla/sla_cli.cpp` harness **slices a 20 mm cube → valid SL1 on the emulator** (config.ini +
90 layer PNGs 1440×2560, verified; emulator runs arm64 via native bridge). JNI bridge
`libsla_engine.so` (`native/sla/sla_jni.cpp` → `SlaEngine.nativeSlice`) building. FDM engine
(`liborcamicro_engine.so`) built; app builds + runs (picker); `:core:test` 36/0. Dark `AppTheme`
implemented. **Build native trees into a DISK-BACKED dir — host `/tmp` is a 3.9 G tmpfs.**
Pick up at the JNI wiring + app integration below.

## 0. In flight right now
- **FDM engine build** = build-host user unit `build.service`, marker
  `~/Projects/mobile-slicer-src/BUILD.DONE`, log `~/Projects/mobile-slicer-src/build-run.log`.
  A watcher is parked on the marker and reports on completion. (Was ~5075/5109 — OCCT.)
- Remote build tree = `~/Projects/mobile-slicer-src/` on the build host (synced with local `app/` `core/`).

## 1. WHEN THE ENGINE BUILD LANDS — do this first
On the build host. If its login shell is **fish**, wrap multi-line remote commands:
  `ssh <build-host> bash -s <<'SH' … SH`.
1. `cd ~/Projects/mobile-slicer-src`
2. Validate the resin core unit tests: `./gradlew :core:test` — runs
   `PngEncoderTest`, `Sl1WriterTest`, `MeshRasterizerTest`. **Fix any Kotlin compile errors** (the
   app/core changes have never been compiled).
3. Build the app:
   `JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=$HOME/android-sdk ./gradlew assembleDebug`
4. Boot the emulator (see the build-host notes), `adb install`, then **capture and
   send screenshots**: ① technology picker ② FDM plate ③ resin plate — so Cuyler can judge the
   visual match.
5. **Relay the worker's real output** (Cuyler's standing preference), not just a summary.

## 2. Quick app polish
- **FDM "switch slicer" entry**: the resin top bar has a "Switch" action; the FDM plate doesn't.
  Add one (top bar or Settings) so both can flip technology.
- Confirm the resin **SAF save** (`CreateDocument`) path actually writes on-device; today the slice
  goes to `cacheDir`.
- Optional: a **layer-image preview** for resin (render a rasterized layer), mirroring the G-code
  toolpath preview.

## 3. Phase 2 — PrusaSlicer SLA engine — **DONE (2026-11-07)**
Option **(B)** chosen (ADR 0003): a **second cross-compiled PrusaSlicer `libslic3r`** (own `.so`) for
resin, Orca `.so` for FDM. **PrusaSlicer 2.9.7 (newest)** builds for arm64; `sla_cli` slices a cube →
valid SL1 on the emulator. Patches in `native/sla/apply-prusa-patches.py` (OpenGL/GLEW guards,
`IS_CROSS_COMPILE`, Imath/OpenVDB ABI 11, OpenCASCADE version). New deps: `nlohmann_json`, `Z3`
(`libseqarrange`), `nanosvg`. `sla_cli.cpp` proves the pipeline; **`sla_jni.cpp`** is the JNI bridge
(`SlaEngine.nativeSlice(in, out, settingsJson)`), building as `libsla_engine.so`.
Remaining:
- **Kotlin side**: `core/resin/SlaEngine.kt` — `System.loadLibrary("sla_engine")` + `external fun
  nativeSlice(in: String, out: String, settingsJson: String): Int`; package the `.so` into
  `app/src/main/jniLibs/arm64-v8a/`; wire `ResinBackend` to call it.
- **Exit test:** slice an STL **with supports** on-device → unzip the .sl1 → **UVTools reads it clean**.

## 4. Phase 3 — CTB then GOO writers
Clean-room from **UVTools** behaviour + **catibo/grid.space** specs. **Do NOT link mslicer's
GPL-3.0 Rust** — reimplement (license cleanliness). CTB v3+ = variable RLE + XOR (key `0x67`) +
RGB565 previews + SHA-256.

## 5. Later
- iOS (same engine, SwiftUI front-end).
- Network print (Moonraker / Chitu LAN); resin + filament libraries; cost/time estimation.

## Backlog from the Lychee gap analysis
See **`docs/research/lychee-feature-gap.md`** (extracted from a Lychee install). Priority after
the SLA graft: **P0** motion settings (lift/retract/rest) · supports · hollowing+drain; **P1** AA,
resin/printer profile DB + import/export, island detection, resin/time/cost estimation,
auto-orient/cut/mirror/array.

## Living tracker (update as work lands)
- **`docs/FEATURES.md`** — every feature (FDM + resin) with status (✅/🚧/🔜/📋/💤). Single source of truth.
- **`docs/research/feature-methods.md`** — method + open reference for each feature (answers "how does Lychee do X").
- REA (`morluto/rea` MCP) is the optional lens for behavior/format **investigation** (not for lifting engine code).

## Build the app — working command
SDK is a **symlink** `~/android-sdk` → `/mnt/storage/tools/android-sdk` (different fs: `find -xdev`
misses it). JDK: `/usr/lib/jvm/java-17-openjdk`. The app task **requires** the Orca profiles arg:
```bash
cd ~/Projects/mobile-slicer-src
printf 'sdk.dir=%s\n' "$HOME/android-sdk" > local.properties
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=$HOME/android-sdk ANDROID_SDK_ROOT=$HOME/android-sdk \
  ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx3072m -Dkotlin.daemon.jvmargs=-Xmx1024m \
  -PorcaResourcesDir="$HOME/Projects/mobile-slicer-src/native/build/orcaslicer/resources" \
  :core:test assembleDebug
```
Without `-PorcaResourcesDir` → `:app:copyOrcaResources` fails ("Gradle property 'orcaResourcesDir' has no
value"). The Orca checkout (with `resources/profiles`, 133 entries) is at `native/build/orcaslicer/` from
the engine build — no separate `fetch-orca.sh --profiles-only` needed. `:core:compileKotlin` passes.

## Resume pointers
- Repo: `~/Projects/mobile-slicer/` (this file = `docs/NEXT-STEPS.md`).
- Remote build tree: `~/Projects/mobile-slicer-src/` on the build host.
- Vault: `Projects/Mobile Slicer.md`.
- Decisions so far: ADR 0001 (resin engine = PrusaSlicer SLA), ADR 0002 (Phase-1 base = fork OrcaMicro).
