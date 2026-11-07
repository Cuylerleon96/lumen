# Next steps — staged plan (RESUME HERE)

Status snapshot: FDM engine building on the build host; resin core (SL1 writer, grayscale PNG,
naive rasterizer) + first-run FDM/Resin picker + shared `PlateShell` are **written and committed**,
but **not yet compiled/run**. Pick up at step 1.

## 0. In flight right now
- **FDM engine build** = build host user unit `build.service`, marker
  `~/Projects/mobile-slicer-src/BUILD.DONE`, log `~/Projects/mobile-slicer-src/build-run.log`.
  A dev machine watcher is parked on the marker and pings on completion. (Was ~5075/5109 — OCCT.)
- Remote build tree = build host `~/Projects/mobile-slicer-src/` (synced with local `app/` `core/`).

## 1. WHEN THE ENGINE BUILD LANDS — do this first
On the build host. Its login shell is **fish** → always `ssh build-host bash -s <<'SH' … SH`.
1. `cd ~/Projects/mobile-slicer-src`
2. Validate the resin core unit tests: `./gradlew :core:test` — runs
   `PngEncoderTest`, `Sl1WriterTest`, `MeshRasterizerTest`. **Fix any Kotlin compile errors** (the
   app/core changes have never been compiled).
3. Build the app:
   `JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=$HOME/android-sdk ./gradlew assembleDebug`
4. Boot the emulator (see the `build-notes` skill), `adb install`, then **capture and
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

## 3. Phase 2 — bring the PrusaSlicer SLA engine over (THE BIG ONE)
**Decision first → write ADR 0003.** Key fact: OrcaSlicer's `libslic3r` does **not** contain the SLA
pipeline (BambuStudio stripped it). Two ways to get real resin slicing:
- **(A) Graft** PrusaSlicer's SLA code into our Orca `libslic3r`: copy `src/libslic3r/SLAPrint.{cpp,hpp}`,
  `SLAPrintSteps.{cpp,hpp}`, `src/libslic3r/sla/**` (SupportTree, raster, hollowing, JobController),
  `src/libslic3r/Format/SL1.cpp`, and wire `printer_technology` + `sla_print` config through the CLI
  config path the JNI bridge already mirrors. Heavy: reconcile PrusaSlicer↔OrcaSlicer type drift.
- **(B) Second engine**: cross-compile **PrusaSlicer's `libslic3r`** (has FDM **and** SLA) for
  arm64 as a second `.so`; call it for resin only, keep the Orca `.so` for FDM. SLA already works in
  PrusaSlicer → far less reconciliation; cost = two engines + two profile sets.
- **Recommendation: (B)** — fastest path to a *correct* resin slice; both AGPL so direct vendoring
  is licensed. Consolidate later only if desired.
Then:
- **Reverse the `build-deps.sh` line that excludes OpenVDB** (SLA hollowing needs it).
- Keep `Sl1Writer` as a **cross-check**; PrusaSlicer emits SL1 natively.
- JNI: add a resin entry point (mesh → `SLAPrint::process()` → SL1).
- **Exit test:** slice an STL **with supports** on-device → unzip the .sl1 → **UVTools reads it
  clean** → print it.

## 4. Phase 3 — CTB then GOO writers
Clean-room from **UVTools** behaviour + **catibo/grid.space** specs. **Do NOT link mslicer's
GPL-3.0 Rust** — reimplement (license cleanliness). CTB v3+ = variable RLE + XOR (key `0x67`) +
RGB565 previews + SHA-256.

## 5. Later
- iOS (same engine, SwiftUI front-end).
- Network print (Moonraker / Chitu LAN); resin + filament libraries; cost/time estimation.

## Backlog from the Lychee gap analysis
See **`docs/research/lychee-feature-gap.md`** (extracted from Lychee on the build host). Priority after
the SLA graft: **P0** motion settings (lift/retract/rest) · supports · hollowing+drain; **P1** AA,
resin/printer profile DB + import/export, island detection, resin/time/cost estimation,
auto-orient/cut/mirror/array.

## Resume pointers
- Repo: `~/Projects/mobile-slicer/` (this file = `docs/NEXT-STEPS.md`).
- Remote build tree: build host `~/Projects/mobile-slicer-src/`.
- Vault: `Projects/Mobile Slicer.md`.
- Decisions so far: ADR 0001 (resin engine = PrusaSlicer SLA), ADR 0002 (Phase-1 base = fork OrcaMicro).
