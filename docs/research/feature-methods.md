# How we know the method for each feature

Short answer: **yes — but from the *right* sources.** We do not learn the methods by decompiling
Lychee's proprietary engine (`libEmeraude.so` is closed, stripped C++). We learn them from
(a) **open-source reference implementations** that solve the same problem, (b) **published file-format
specs**, and (c) **behavioral observation** of the app (including its own readable UI bundle
`bin/renderUI.js`, and tools like **REA** for runtime/format/binary *investigation*).

> **Legal line (non-negotiable for an AGPL project):** we *understand* Lychee (interoperability,
> behavior, formats — which the law and industry norms allow, and which UVTools etc. already did for
> CTB/GOO), but we *reimplement* from open references. We never paste proprietary code into our repo.
> This is also the correct engineering path: open references are testable, portable, and licensed for
> our use. REA is used to *understand*, not to lift.

## Reference implementations we lean on
| Project | License | What it gives us |
|---|---|---|
| **PrusaSlicer** (`SLAPrint`, `sla::SupportTree`, OpenVDB hollowing) | AGPL-3 | The whole resin engine — supports, hollowing, drainage, island detect, rasterization, SL1. |
| **Encrust** (`vvvlladimir/Encrust`) | AGPL-3 | Clean, documented **auto-orient scoring**, island scan, **pull-force model**. Rust, readable. |
| **DragonFruit Slicer docs** | (docs) | Documented **6-phase auto-support pipeline** + auto-orient method — algorithm prose, not code. |
| **mslicer** | GPL-3 | **Anti-aliasing / 3D-SSAA** rasterization behavior reference. |
| **UVTools** | GPL-3 | Format writers (CTB/GOO/…) + **island detection** — clean-room *spec* reference only. |
| **Manifold** | MIT | Robust boolean/CSG (cut, drain holes, hollow blockers). Fastest + watertight. |
| **OpenVDB** | MPL-2.0 | Volume/offset ops behind hollowing. |
| **CGAL / libigl** | GPL·LGPL / MPL-2 | Offset, boolean, mesh repair fallbacks. |

## Method per feature
| Feature | Method (how it actually works) |
|---|---|
| **Auto-supports** | 1) classify down-facing triangles below the self-support angle (overhang islands); 2) emit candidate points at island centroids/flanks; 3) dedupe; 4) place contacts (perimeter ring + grid infill at angle-dependent spacing); 5) size trunks (stepwise diameter vs height/load); 6) auto-brace between trunks; 7) collision-check vs model. |
| **Manual support editing / painting** | Ray-cast the brush cursor onto the mesh, sphere-select vertices, toggle/insert `SupportPoint`s; re-run trunk generation. |
| **Auto-orient** | Sample SO(3) (Fibonacci-sphere sweep + convex-hull **stable resting poses**), local-refine top-K; score cheap geometric proxies — **support volume**, **peel** (per-layer cross-section area curve), **height**, **plate adhesion** — and minimize. (Encrust publishes the exact 5-term score.) |
| **Island detection** | Rasterize each layer mask → connected components (union-find/flood fill) → an island is a component **not connected** to a support/plate seed in the layer below. |
| **Hollowing** | `meshToVolume` → **erode** by wall-thickness offset → `volumeToMesh`; **closing distance** suppresses thin cavities and sharp inner corners. (OpenVDB.) |
| **Drain holes** | Ray-cast to the surface hit point, subtract an **oriented cylinder** (CSG) of given radius/height. |
| **Anti-aliasing** | Supersample the layer mask (3D SSAA) or distance-field edge weighting → 8-bit grayscale for the writer. |
| **Resin/time/cost estimate** | Σ(covered-pixel area per layer × layer height) = model volume; + support volume; × resin density → mass; × price → cost; time = Σ per-layer exposure + motion. |
| **Motion / TSMC / tilt** | Not an algorithm — **encoding**: per-layer lift/retract/rest (and two-stage, and tilt cycles) written into the output file's per-layer header. Spec-driven. |
| **Planar cut / boolean** | Plane–mesh intersection + cap, or full CSG. Manifold/CGAL. |
| **Mesh repair** | Fill holes / re-weld non-manifold edges / re-orient faces (Manifold, libigl, PyMeshFix). |
| **Masks** | Composite the rendered layer PNG with a mask image per border rule (mode/outset/clip). |
| **Format writers (CTB/GOO/…)** | Reverse-engineered from published **specs** (catibo, grid.space, UVTools docs): RLE layer encoding, preview bitmaps, checksums/key. |

## Where REA (`morluto/rea`) fits
REA is an MCP agent toolchain for **reverse engineering**: JS/Electron module graphs, native binary
pseudocode (via Ghidra/IDA/Hopper), Android/adb, firmware, packet/log capture, etc.

- **Good, appropriate uses for our project:** map Lychee's **runtime behavior** and **Electron/JS
  module graph** (which feature calls what), observe the **settings→output** relationship, inspect the
  shipped **format SDK** (`libCTBAdapter.so`/`libCtbSDKFormat.so`) for *format/interop* details, and
  capture the exact bytes Lychee writes so we can validate our writers against real output.
- **What it will NOT realistically give us, and we should not chase:** recovering the *algorithms*
  inside `libEmeraude.so`. A huge stripped C++ engine decompiles to noise; the effort is enormous and
  lifting it into AGPL would be a license violation. We get far better, defensible results from
  PrusaSlicer/Encrust/mslicer + specs.
- **Safe-use note:** REA is for lawful analysis of software you own — fine for interop and format
  work. Keep the output as *knowledge* (which algorithm/parameter), not as copied code.

**Net:** the method map above is already strong and grounded in open projects *before* any binary
work. REA is a bonus lens for behavior/format verification, not the foundation.

## Method-status confidence
- Supports / hollowing / drainage / island detect / formats → **high** (open implementations exist).
- Auto-orient → **high** (Encrust + DragonFruit publish the method).
- Anti-aliasing → **medium-high** (mslicer behavior; exact gray-level algorithm is convention).
- Anything Lychee-tuned (their specific constants/presets) → we reproduce *behavior*, not their numbers.
