# ADR 0002 — Phase-1 base = fork OrcaMicro

- **Status:** accepted (2026-11-06) — Cuyler chose **A**.
- **Context:** Phase 1 needs a working FDM slice on Android fast. OrcaMicro
  (`whoxllm/OrcaMicro`, AGPL-3.0) already cross-compiles OrcaSlicer's `libslic3r`
  engine-only for arm64 with an NDK + JNI bridge; ~950 Orca profiles bundled.
  Alternative (B) was starting from PrusaSlicer's `libslic3r`.
- **Decision:** **Fork OrcaMicro** as the Phase-1 FDM base. Graft PrusaSlicer's
  **SLA module** (`SLAPrint`, Voronoi supports, hollowing) into that engine for
  resin (per ADR 0001). Pull richer Orca FDM features as needed later.
- **Consequences:**
  - Fastest route to a device-slicing proof-of-life (build recipe already exists).
  - Must **add OpenVDB back** for SLA hollowing (OrcaMicro dropped it).
  - Must **carry/maintain a patch** set (engine-only CMake, MD5 shim) — or upstream
    `-DORCA_ENGINE_ONLY=ON` (OrcaMicro proposed exactly this).
  - NDK version is correctness-sensitive — pin it and document.
  - AGPL-3.0 throughout.
- **Build host:** a dedicated multi-core machine, not a laptop
  (a laptop-class CPU; disk nearly full). Route the repo over and build there.
- **Next:** clone the fork base on the build host; attempt the engine-only
  cross-build; stand up the JNI bridge with a hardcoded cube.
