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
- **Resin engine source:** PrusaSlicer SLA (not Orca — Orca has none).
- **First resin output format:** SL1 (open) → then CTB, then GOO.
- **Local-only for now**; no remote/deploy.
