# ADR 0001 — Resin engine = PrusaSlicer SLA (not Orca)

- **Status:** accepted (2026-11-06)
- **Context:** We want one mobile app doing FDM **and** resin. OrcaSlicer is the
  best FDM engine but has **no SLA/resin and no roadmap** (issues #12003, #13132
  parked). PrusaSlicer is the only mature open-source FDM+resin slicer and shares
  Orca's Slic3r ancestry + core types.
- **Decision:** Source the **resin** pipeline from **PrusaSlicer's SLA module**
  (`SLAPrint`, `sla::SupportTree`, hollowing). FDM comes from Orca/Prusa
  `libslic3r`. Phase-1 engine = a single PrusaSlicer `libslic3r` fork (native FFF
  + SLA); optionally graft Orca FDM features later.
- **Consequences:**
  - Resin is proven path (Voronoi supports, hollowing, SL1 output) on day one.
  - Requires **OpenVDB** (hollowing) — cannot drop it like OrcaMicro did.
  - AGPL-3.0 throughout (already accepted).
  - Build is larger; defer to a dedicated build host (disk + CPU).
- **Alternatives rejected:** (a) Orca-only + write SLA from scratch — huge;
  (b) closed-source resin engine — against project goal.
