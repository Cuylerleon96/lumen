# Complete feature surface + "mature software" checklist

Goal: **100% usable by a power user** who's used slicers developed for years. This is the full surface
(mined from OrcaSlicer's GUI page structure + PrusaSlicer's settings groups), plus the workflow/polish
that makes software feel finished. Anything not yet in `FEATURES.md` becomes a tracker item.

## FDM — full settings surface (OrcaSlicer pages → sections)
- **Quality**: layer height · layer-height limits (adaptive layers) · initial layer height · line width
  (default/first/outer/inner/top/infill/support) · seam position + gap + painted seams · precision
  (slice-gap closing radius, resolution, **arc fitting**) · **ironing** (type/flow/spacing/speed/angle) ·
  wall generator (classic/**Arachne**) · wall transition · small-perimeter threshold · gap fill · top
  surface skin · bridge flow · overhang speed.
- **Strength**: wall loops · top/bottom shell layers · sparse infill (density/pattern/anchor/width/flow) ·
  internal solid infill · top surface density · infill/wall overlap · infill combination · gap fill ·
  infill direction · minimum sparse-infill threshold.
- **Speed**: initial-layer speed + infill · outer/inner wall · top surface · sparse/internal solid infill ·
  travel · acceleration (print/travel/initial) + **acceleration limits** · jerk + **jerk limits** ·
  junction deviation · volumetric speed limit · overhang slowdown · layer-cooling slowdown · min feedrate.
- **Support**: enable · type (normal/**tree**) · style · threshold angle · on-build-plate-only ·
  interface (top/bottom layers, spacing, pattern, loops) · contact distance (top/bottom/z) · XY distance ·
  base pattern/spacing · tree (branch angle/diameter/tip/distance/wall) · support/object first-layer gap ·
  support speed · support filament · **painted supports**.
- **Multimaterial**: **prime tower** (enable/width/angle/brim/position) · ooze prevention · wipe tower ·
  **flush volumes** · filament change/ramming · **AMS/MMU**.
- **Others**: bed adhesion (skirt/**brim**+ears/**raft**) · special modes (**spiral vase**,
  **sequential printing**, slicing mode) · G-code output (arc fitting, file header, output options) ·
  **post-processing scripts** · **timelapse** · **fuzzy skin** · notes · print sequence · label objects.
- **Filament** (per filament): nozzle/bed temp (per plate type) · flow ratio · **pressure advance** ·
  cooling (fan min/max, thresholds, slowdown) · **retraction** (length/speed/z-hop/wipe/**firmware
  retraction**) · overrides · filament start/end G-code · volumetric speed · shrinkage · softening temp.
- **Printer**: nozzle diameter (+ **nozzle variants**) · extruder clearance · machine limits (accel/jerk) ·
  **machine G-code** (start/end/layer-change/before-layer/change-filament) · **adaptive bed mesh** ·
  aux/exhaust fan + chamber temp · **clumping detection** · plate types + per-plate temps.

## Resin (SLA) — full surface (PrusaSlicer pages)
**General** · **Motion ability** (lift/retract/TSMC/tilt) · **Material** (resin: density, correction
XYZ, print speed, vendor/type) · **Layers and perimeters** · **Supports** (full param set) · **Pad**
(wall thickness/height/slope, brim size, edge radius, object gap, around-object, connectors) ·
**Hollowing** (enable/min-thickness/quality/closing-distance) · **Output options** (archive format,
precision, SL1/SL1S) · **Advanced**.

## P. Power-user workflow & polish (what makes it feel "finished")
- **Preset management**: save/load/import/export · **inheritance** ("inherited from") · **compare
  presets** · **dependencies** view · compatible printers/processes/materials.
- **Physical printer management** (add/edit) + **device/network** (send to printer).
- **Project files** (save scene/plate) · **multi-plate** · **per-object settings** (overrides).
- **Undo/redo** · **autosave + crash recovery** · session restore.
- **Batch**: batch-process current scene · batch convert · queue multiple files.
- **G-code console + preview** (layer slider, toolpath inspection, speed/flow color view).
- **Validation**: printability warnings (out-of-bounds, thin walls, overhangs, floating parts) with
  "how to fix" hints · **printable-box / shadow / triangle** display toggles.
- **Settings search** · **visibility modes (Simple/Advanced/Develop)** · favorites/"Frequent".
- **Community profile DB** (download/update printer/filament/process/resin profiles) + profile sharing.
- **CLI** + **scripting/plugin hooks** · config import/export · **check for updates**.
- **Calibration** wizards (FDM towers, resin exposure tests).

## Q. Accessibility & ergonomics
- Large-text scaling · **colorblind-safe** palettes · high-contrast option · **left-handed** layout ·
  full **touch/gesture** parity (see `docs/touch-interaction.md`) · haptics · screen-reader labels ·
  one-handed reachability.

---
New tracker groups: **P (power-user workflow & polish)** and **Q (accessibility & ergonomics)**;
expansions to **C (FDM)** to the full page surface above.
