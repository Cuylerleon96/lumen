# Slicer feature discovery — what we haven't implemented yet

Standing practice: at waiting points, mine slicer programs (OrcaSlicer, PrusaSlicer, Lychee, UVTools,
Bambu, Chitubox) for features not yet in `FEATURES.md`, and add them. Sources below are the shipped
binaries/sources, not marketing. First pass, 2026-11-06.

## K. Post-slice analysis & repair (UVTools-class) — **NEW category**
A whole reliability layer that operates on the *sliced layers*, after slicing — huge for resin success
rate. UVTools' feature set:
- **Issue detection**: **Islands** (unsupported regions), **Overhangs**, **Resin traps** (internal
  uncured resin), **Suction cups**, **Touching bounds** (pixels at plate/print-area limits), **Print
  height**, **Empty layers**.
- **Repair / auto-fix**: remove islands ≤ N px, **recursively** remove islands up to N layers,
  **attach islands down** N layers (bridge a missed support tip), fill resin traps (solidify),
  per-issue Ignore/Delete, "repair layers & issues".
- **Pixel editor**: paint/delete pixels per layer, add drain holes at 90° via pixels.
- **Mutations/filters**: **Pixel dimming** (per-layer AA/exposure gradient), **Morph** (erode/dilate
  to kill elephant-foot / sharpen), layer filter/mutate ops.
- **Auto-corrections/suggestions**: layer height, bottom layer count, wait before/after cure,
  transition layers, model position.
- **Change any parameter** on an existing sliced file (exposure, burn-in, speeds…).

## L. Calibration & testing — **NEW category**
- Orca: **Auto-Calibration**, **Calibration Guide**, towers for **temperature, flow rate, pressure
  advance, retraction, cornering**, volumetric-flow measurement.
- Resin: **exposure test** (a plate of quadrants at different exposures), **XP/Cones of Calibration**.
- We should generate calibration artifacts on-device.

## D.3+ Supports — the full parameter set (from PrusaSlicer SLA + Lychee)
Our supports item is thin; the real surface:
- **Pillars**: `support_pillar_diameter`, `support_small_pillar_diameter_percent`,
  `support_pillar_widening_factor`, `support_pillar_connection_mode`, `support_max_bridge_length`,
  `support_max_bridges_on_pillar`, `support_max_pillar_link_distance`.
- **Base**: `support_base_diameter`, `support_base_height`, `support_base_safety_distance`.
- **Head/tip**: `support_head_front_diameter`, `support_head_penetration`, `support_head_width`.
- **Object**: `support_object_elevation`, `support_critical_angle`, `support_max_weight_on_model`.
- **Points**: `support_points_density_relative`, `support_points_minimal_distance`.
- **Enforcers**: `support_enforcers_only`, `support_material_enforce_layers`.
- **Tree**: `support_tree_type`, `support_tree_angle(+_slow)`, `support_tree_branch_diameter(+_angle,
  _double_wall)`, `support_tree_branch_distance`, `support_tree_tip_diameter`, `support_tree_top_rate`.
- **Lychee advanced editing**: mini-supports (alt+ctrl), anchored supports, **branching** (alt+shift),
  **advanced mode** (resize any support segment, move anchors), **proximity checker** (flag supports
  too close to the model), **bracing/grid**, custom bracing params.

## M. Hollowing & material (resin) — full set
`hollowing_enable`, `hollowing_min_thickness`, `hollowing_quality`, `hollowing_closing_distance`,
`material_density`, `material_correction`(+`_x/_y/_z`), `material_print_speed`, `material_type/vendor`,
`sla_output_precision`, `sla_archive_format`. Plus **editable hollowing voxels** (Lychee 8: tube brush
to fill cavities behind the surface, apply-on-release) and **pocket detection**.

## N. FDM extras (OrcaSlicer) we haven't captured
**Adaptive bed mesh**, **Clumping detection** (+ prime-tower dependency), **Bridging** controls,
**Cooling fan / Auxiliary part cooling / Exhaust fan / Chamber temperature**, **Damping**, **Cornering**
(accel/velocity), **Acceleration limitation**, **plate types** (Cool Plate / SuperTack / Engineering /
Textured PEI / Smooth PEI, per-plate bed temps), **prime tower**, **timelapse**, **flushing volumes /
AMS/MMU**, **spiral vase**, **per-object settings**, **preset compare / inheritance / dependency graph**,
**physical printer management**, **custom G-code hooks** (before/after print, per-layer, per-object,
change-filament), **printable-box / shadow / triangle display** toggles.

## O. File/scene & export
- **Duplicate current plate**, clone/array, **export selection as new scene** (Lychee).
- Multi-object **3MF Production Extension**; **Goo v5**; **LGS**; **SL1/SL1S** round-trip.
- **Batch process current scene** (Lychee); **batch convert**.

---
These become tracker groups **K (post-slice analysis & repair)** and **L (calibration)**, plus
expansions to **D.3 supports**, **D.4 hollowing**, **C. FDM**, and **F. project/platform**.
