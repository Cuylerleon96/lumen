# ADR 0004 — One unified app: technology-agnostic core, pluggable slicing backends

**Status:** accepted (2026-11-06) · **Supersedes the "two sides" framing of ADR 0002/0003 (the
*engines* stay separate; the *app* becomes one).**

## Context
The app is a fork of OrcaMicro (an OrcaSlicer Android port). It is a single Android app, but it is
**FDM/Orca-first with resin bolted on**: `UiState` carries parallel `resin*` fields beside the FDM
`catalog/selection/edits/inputs`, and `Screen.RESIN` branches off the FDM `PLATE` screen. That reads
as two apps stitched together.

The goal is **one new, better application** — fully on-device Android, feature-complete for everyday
slicing, and a place to **experiment with experimental slicing methods** without a laptop. A merge of
two apps is explicitly *not* the goal.

## Decision
Re-architect the **app layer** around a technology-agnostic domain with **pluggable slicing
backends**. Keep the parts that are already engine-agnostic and working.

- **`:core` owns the domain, engine-agnostic:**
  - `Project` / `Plate` (models, placement, transforms) — one model, not per-technology.
  - `PrintSettings` — a unified settings surface with technology-specific **sections** (FDM
    process/filament/machine; resin exposure/motion/supports), not a per-technology state split.
  - `SlicingBackend` — the plug point. FDM (Orca `libslic3r`) and Resin (PrusaSlicer SLA) are two
    implementations; **experimental methods are just more backends.**
  - `SliceJob` / `SliceOutcome` — one request/response vocabulary (G-code+toolpaths **or** layer
    images), so the UI and preview don't care which engine ran.
- **`:app` is one UI over that domain.** "Technology" is a property of the job/backend, not a mode
  that swaps the entire screen tree. One plate screen, one settings surface, one preview.
- **Keep** the native engines (two `.so`s, per ADR 0003), the JNI bridge, the GL renderer, and the
  reusable core utilities (mesh, gcode, profiles, resin writers).

## Consequences
- **+** Genuinely one app; a single mental model; experimental slicing is a first-class extension
  point (write a `SlicingBackend`, register it).
- **+** UI/preview are backend-agnostic → features (supports, hollowing, estimation, prep tools) are
  built once and shared.
- **−** A real refactor of `:app` and parts of `:core`; the working resin/FDM screens get folded into
  a unified plate screen. Mitigated by keeping engines/renderer/bridge untouched.
- **−** New app identity (namespace/branding) — rename away from the fork's `io.github.whoxllm.*`.

## Not in scope of this ADR
Which experimental methods to add (separate ADRs). The FDM vs resin engine choice (ADR 0003 stands:
two engines, one app).
