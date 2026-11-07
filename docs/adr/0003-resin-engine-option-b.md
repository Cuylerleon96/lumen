# ADR 0003 — Resin engine = a second PrusaSlicer libslic3r (option B)

- **Status:** accepted (2026-11-06) — Cuyler chose **B**.
- **Context:** OrcaSlicer's `libslic3r` contains **no SLA pipeline** (BambuStudio stripped it
  before OrcaSlicer forked). Two ways to get real resin slicing:
  - **(A) Graft** PrusaSlicer's SLA sources (`SLAPrint`, `sla/**`, `Format/SL1.cpp`) into our
    Orca `libslic3r`, reconciling the Orca↔Prusa type drift.
  - **(B) Second engine** — cross-compile **PrusaSlicer's `libslic3r`** (which has FDM *and* SLA)
    for arm64 as its own `.so`, and use it for resin only; keep the Orca engine for FDM.
- **Decision:** **(B).** Add a second cross-compiled engine (`libprusaslicer_sla.so` or similar)
  built from PrusaSlicer, used exclusively for resin. The existing Orca-based engine stays the FDM
  path.
- **Rationale:** SLA already works in PrusaSlicer, so there is far less source reconciliation than
  graft (A); both projects are AGPL-3.0 so direct vendoring is licensed. Speed to a *correct* resin
  slice beats a single-engine ideal for now.
- **Consequences:**
  - Two engines + two profile sets to ship and maintain (`liborcamicro_engine.so` for FDM;
    a second `.so` for SLA).
  - **Reintroduce OpenVDB** in the SLA engine's dep build (hollowing needs it).
  - The JNI bridge gains a **resin entry point** (mesh → `SLAPrint::process()` → SL1/CTB).
  - Consolidate the two engines later only if it becomes worth it (a follow-up ADR).
- **Next:** cross-compile PrusaSlicer's `libslic3r` **engine-only** for arm64 (mirror the
  OrcaMicro engine-only recipe: no GUI/CLI, MD5 shim, pinned NDK), wire the resin JNI path, and
  retire the naive `MeshRasterizer` (keep `Sl1Writer` as a cross-check).
