# Roadmap

Each phase has a **falsifiable exit test** (evidence, not vibes).

## Phase 0 — Research + scaffold ✅ (2026-11-06)
Exit: research docs + architecture + licenses written; project dir persisted.
*Done.*

## Phase 1 — FDM engine on device (Android)
Reuse the proven path: cross-compile a `libslic3r` engine-only for arm64, JNI
bridge, ship `.so`. Either fork **OrcaMicro** or start from PrusaSlicer's engine.
- **Exit test:** on a phone, slice a test cube + a Benchy via Orca/Prusa profiles
  and **byte-compare the G-code against desktop** for the same config (must match
  within tolerance). Load + move/rotate/scale, preview layers.

## Phase 2 — Resin engine + SL1 output
Integrate PrusaSlicer SLA: orient → (no hollow yet) → supports → slice →
raster → **SL1** (zip of PNGs + config.ini).
- **Exit test:** slice an STL on-device with supports; **unzip the .sl1**, confirm
  one PNG per layer + valid config; a desktop slicer (UVTools) **reads it without
  error**; a real printer (or NanoDLP/emulator) prints it.

## Phase 3 — Resin format writers (CTB, then GOO)
Implement CTB v3+ (variable RLE + XOR key 0x67 + previews) and GOO.
- **Exit test:** round-trip: write CTB → **UVTools opens it**, layer count/exposure/
  pixels match; print on a Chitu-board printer (Elegoo/Anycubic). Same for GOO.

## Phase 4 — Resin authoring features (the hard, high-value bits)
Hollowing + drain holes (OpenVDB), manual + auto supports UI, anti-aliasing levels,
bottom-layer/elephant-foot control, print-failure analysis (islands, suction cups).
- **Exit test:** hollow + support a figurine; island detector flags the same
  islands a reference tool does.

## Phase 5 — iOS
Shared engine, SwiftUI front-end, same exit tests on an iPhone/iPad.

## Phase 6 — Polish / extras
More formats (PW*/Photon/CXDLP), network print (Moonraker / Chitu LAN), resin &
filament libraries, cost/time estimation, forks of community profiles.

## Cross-cutting risks
- **Disk/build host:** engine build needs tens of GB → use the build host
  (`build-notes` skill), not the dev machine (nearly full).
- **NDK version sensitivity** (output correctness) — pin and document.
- **AGPL compliance** at every release.
- **Maintenance of a large C++ fork** — budget for upstream tracking.

## Immediate next actions
1. Pick Phase-1 base: fork **OrcaMicro** vs PrusaSlicer engine (ADR 0002 TBD).
2. Free disk on the build host; clone + attempt the **engine-only** cross-build.
3. Stand up the JNI bridge with a hardcoded test cube (proof of life).
