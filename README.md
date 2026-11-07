# Mobile Slicer (working title)

An open-source **mobile** slicer covering **both FDM and resin (MSLA/SLA)** —
on-device, offline, no cloud, no account. The gap: every existing mobile slicer
port is **FDM-only**, and the mature resin slicers (Lychee, Chitubox, VoxelDance)
are closed-source. This project is the union.

- **FDM engine:** reuse **OrcaSlicer's `libslic3r`** (engine-only) — already
  proven on Android by OrcaMicro/SliceBeam.
- **Resin engine:** reuse **PrusaSlicer's SLA backend (`SLAPrint`)** — the only
  mature open-source resin pipeline (Orca has no resin, and won't).
- **Formats:** reference **UVTools** (C#) + **mslicer** (Rust) + **catibo** (docs).
- **License:** **AGPL-3.0** (inherited — non-negotiable; see `docs/research/licenses.md`).

Status: **research phase** (2026-11-06). Nothing compiled yet. See `ROADMAP.md`.

## Repo layout
```
mobile-slicer/
├── README.md                 ← you are here
├── ROADMAP.md                ← phased plan
├── docs/
│   ├── architecture.md       ← proposed system design
│   ├── adr/                  ← architecture decision records
│   └── research/
│       ├── prior-art.md      ← existing slicers + mobile ports (what to reuse)
│       ├── resin-formats.md  ← MSLA output formats + encodings (deep dive)
│       └── licenses.md       ← AGPL/GPL implications
└── (src/ TBD — not started)
```

## The one-paragraph plan
Base the native engine on a **single `libslic3r` fork that carries both the
OrcaSlicer FDM features and PrusaSlicer's SLA module** (they share the Slic3r
ancestry and core types). Cross-compile engine-only for arm64 (Android NDK, then
iOS). Emit **SL1** first (ZIP of grayscale PNGs + `config.ini`) — the open,
documented resin container — then add **CTB** and **GOO** writers (≈95% of
printers). Wrap in a Kotlin/Compose (Android) / SwiftUI (iOS) touch UI. Reuse
PrusaSlicer's Voronoi tree supports + hollowing; reuse UVTools/mslicer algorithms
for the format writers.

## ⚠️ Environment note
Build host disk is at **93% (30 GB free)**. A full slicer engine build
(Boost/TBB/CGAL/OCCT/OpenVDB + libslic3r + NDK) needs **tens of GB**. Free space
on the build host / build host before starting engine work (see the
`build-notes` skill — builds belong there, not on the slow dev machine).

## Quick decisions locked so far
- **License:** AGPL-3.0-or-later.
- **Phase-1 base (ADR 0002):** **fork `whoxllm/OrcaMicro`** — OrcaSlicer 2.4.2
  engine on Android, AGPL, ~950 FDM profiles, clean module split.
- **Resin engine source (ADR 0001):** PrusaSlicer SLA grafted into that engine.
- **First resin output format:** SL1 (open) → then CTB, then GOO.
- **Local-only for now**; no remote/deploy.

## Progress (2026-11-06)
- **FDM engine build in flight** on the build host (fork of `whoxllm/OrcaMicro`,
  OrcaSlicer 2.4.2, NDK r27c). Deps cross-compiled; OCCT compiling (~2400/5100).
- **App — first-run technology choice shipped:** `TechnologyScreen` picks **FDM or
  resin**, persisted, routes into that slicer's flow; a "Change slicer" affordance
  switches later. New `Screen.TECHNOLOGY` / `Screen.RESIN`; `UiState`/`SavedState`
  carry the choice.
- **Resin core shipped** (`core/…/resin/`): `ResinPrinter`, `ResinSettings`,
  `LayerImage`, a platform-free **grayscale PNG encoder**, the **SL1 writer**
  (streams one layer at a time), and a **naive mesh→layer rasterizer**; unit tests.
  Format + rasterizer logic validated via a Python mirror (PIL + zipfile open the
  emitted SL1 cleanly).
- Resin UI: `ResinPlateScreen` — pick printer, set exposures, load STL, **slice to
  .sl1**, save via the system picker.
- **Pending:** Kotlin compile/`assembleDebug` (build host busy), FDM-side "switch
  slicer" entry, and grafting PrusaSlicer's SLA engine (supports/hollowing/AA).

## Fork base — OrcaMicro structure (what we extend)
```
app/     Kotlin + Jetpack Compose + OpenGL ES 2 (the UI)        → add resin UI
core/    pure Kotlin: STL, placement, ORCASlicer profiles,      → add resin model
         settings, G-code parsing (JVM-unit-tested)                types + SL1/CTB writers
native/  builds OrcaSlicer libslic3r for Android + JNI bridge   → add PrusaSlicer SLA
         (liborcamicro_engine.so); engine runs in its OWN process  (SLAPrint + OpenVDB)
docs/    design.md, plan.md (upstream)
```
Engine = **OrcaSlicer 2.4.2**, NDK **r27c**, Android 9+/arm64, **no bamboo**, engines
isolated in a separate process (native crash ≠ app crash). Native build "takes a
few hours from scratch" → **build host**.
Reference docs to read upstream: `docs/design.md`, `docs/plan.md`.
