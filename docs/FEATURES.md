# FEATURES — living tracker (FDM + resin mobile slicer)

**This is the single source of truth for feature status. Update it as work lands.**
Status marks: **✅ done** · **🚧 in progress** · **🔜 next (P0)** · **📋 todo (P1)** · **💤 backlog (P2)**.
Method/reference for each *how* → `docs/research/feature-methods.md`. Feature inventory origin →
`docs/research/lychee-feature-gap.md`.

_Last updated: 2026-11-06 (initial full inventory)._

## Progress log
- 2026-11-06 — FDM engine built (arm64 `.so`); **app APK builds**; `:core:test` **36 tests green**;
  app **runs** on emulator (picker + resin workspace). P0 **motion settings** added + tested.
  **REA** (reverse-engineering toolkit) installed on the build host; Lychee app structure mapped
  (`docs/research/rea-lychee-notes.md`). QC pass #1: `docs/QC-REPORT.md`.
- Still open (big): SLA engine graft (ADR 0003), supports, hollowing, AA, CTB/GOO, profiles,
  estimation, prep tools.
- 2026-11-06 — **feature-discovery pass** (Orca/Prusa/Lychee/UVTools): added groups **K** (post-slice
  analysis & repair), **L** (calibration); expanded supports/hollowing/FDM sets →
  `docs/research/slicer-feature-discovery.md`. Standing practice: mine slicers at waiting points.

---

## A. App shell & UX
- ✅ **Technology picker (FDM vs Resin)** on first launch, persisted, switchable.
- ✅ **Shared `PlateShell`** (top bar, bottom bar, Slice FAB, 3D plate, snackbar) for both sides.
- ✅ Themed (`OrcaMicroTheme`, dark/light + dynamic color).
- 🔜 **FDM-side "switch slicer"** entry (only resin has it today).
- 📋 Undo/redo across edits; multi-model plate (multiple objects on one plate).
- 📋 Onboarding / first-run tips; units + language settings (i18n).
- 💤 Theme editor, splash, print history screen.

## B. Shared model I/O & prep
- ✅ **Load STL**, place, move/rotate/scale (FDM `PlacementSheet`).
- ✅ Fit-check against build volume.
- 📋 **Import OBJ, 3MF, STEP, AMF** (Lychee reads all; we read STL only).
- 📋 **Mirror**, **Array/duplicate**, **"align to face"/lay-flat**, **planar cut**, **boolean CSG**.
- 📋 **Measure / dimension / text** tools.
- 💤 Smooth/emboss/warp; projection supports.

## C. FDM (via OrcaSlicer engine)
- ✅ **Orca `libslic3r` engine** cross-compiled for arm64 (build in flight on the build host).
- ✅ ~950 Orca printer profiles (bundled).
- ✅ Print/filament/process settings editor (the tabbed `SettingsScreen`).
- ✅ Slice → G-code; layer/toolpath preview; print time + filament estimate.
- ✅ Tree/normal supports, brim, spiral vase (engine features).
- 📋 G-code post-processing scripts; per-object settings; calibration wizards (flow/PA/temp).
- 📋 Network print: Moonraker/Klipper, OctoPrint, PrusaLink, Bambu LAN/dev.
- 💤 Belt printers; multi-material painting.

## D. Resin (MSLA) — the big build
### D.1 Engine
- 🚧 **PrusaSlicer SLA engine** (`SLAPrint` + `sla::SupportTree` + OpenVDB) as a **second `.so`**
  — ADR 0003 option **B**. PrusaSlicer 2.8.1 cloned; dedicated dep build (`native/sla/build-sla-deps.sh`
  → `prefix-sla`) running on the build host. Feasibility proven (toolchain OK; needs its own Eigen/Boost/etc.).
  (Currently a naive `MeshRasterizer` stands in.)
- ✅ **SL1 writer** (config.ini + per-layer grayscale PNG), streaming.
- ✅ Platform-free grayscale **PNG encoder**.
### D.2 Print settings
- ✅ Layer height; exposure; bottom exposure; bottom layer count; light-off (single).
- 🚧 **Motion: lift/retract distance+speed, bottom speeds, rest timers** — model + print-time wired
  (tested, 36 tests pass). CTB/GOO writers still to emit them.
- 📋 Bottom-layer taper (`burnLayerDecreasing`); grey-level compensation (`*Compensation`/`GreyValue`); PWM.
- 💤 **TSMC two-stage motion**; **tilt/vat-printer motion** (tiltUp/Down cycles/delays/speeds).
### D.3 Supports
- 🔜 **Auto-supports** (from the SLA engine) — island/overhang-driven, **load/peel-aware** tree supports.
- 📋 **Manual support editing**: place/move/delete · **struts** (point-to-point) · contact/strut **shapes** · advanced segment editing (Lychee) · **proximity checker**.
- 📋 **Support painting** (brush: light/medium/heavy tags, "no-support" faces).
- 📋 **Full param set** (PrusaSlicer SLA): pillar Ø/widening/connection · base Ø/height/safety · head front/penetration/width · object elevation/critical angle/max weight · point density/min-distance · enforcers · tree type/angle/branch/tip · **anchor + border-anchor distance**, **merge level**, **lattice XY/Z**, **intersection avoidance**.
- 📋 **Orientation strategies** (min-support / min-contacts / min-projection / face-up-down) — HeyGears-style (`docs/research/heygears-support.md`).
- 💤 Mini-supports; accordion supports.
### D.4 Hollowing & drainage
- 🔜 **Hollowing** (wall thickness, closing distance) via OpenVDB.
- 🔜 **Drain holes** (place/drill, diameter/depth).
- 📋 Hollow blockers/infill lattice; fill-holes.
### D.5 Anti-aliasing & masks
- 📋 **Anti-aliasing** (grayscale edge levels / SSAA) in the writer.
- 💤 Masks (mask PNG composited per border rules: mode/outset/slice).
### D.6 Detection & repair
- 🔜 **Island detection** (per-layer unsupported-region scan) — reliability.
- 📋 **Mesh repair** (auto-repair broken/non-manifold meshes); fill holes.
### D.7 Estimation
- 📋 **Resin volume/weight/cost + print-time** estimate (fast + real).
### D.8 Output formats
- ✅ SL1 / SL1S.
- 📋 **CTB v3+** (variable RLE + XOR key 0x67 + RGB565 previews + SHA-256).
- 📋 **GOO** (chunked RLE + inverted checksum).
- 💤 PW0/PWS/PWMA/PWMS, PHOTON(S), CBDDLP, NANODLP, ZCODE, CXDLP, LGS.

## E. Profiles & libraries
- ✅ **FDM profiles (rich, bundled from Orca):** ~1,545 machine (printer **+ nozzle variants**) · ~7,053 filament · ~3,341 process.
- 📋 **Resin profile DB** (exposures per resin) + import/export — we only have 6 stubs.
- 📋 **Resin printer DB** (LCD res, pixel pitch, motion caps) — beyond 6 stubs.
- 📋 **Strategy layer** (goal + material + printer → parameters; HeyGears-style) + per-resin calibrated defaults.
- 📋 Support presets; printer discovery/connection.

## F. Project & platform
- 📋 **Project format** (save/load a plate with models+settings) — Lychee LYS/LYL; we have none.
- 📋 Model library; recent files.
- 💤 Cloud sync / shop; CLI mode; viewer app.

## G. Build / release / infra
- ✅ Repo scaffold, ADRs, research docs, licensing (AGPL-3.0).
- 🚧 **FDM engine build** (build host) → then `:core:test` + `assembleDebug` + emulator screenshots.
- 📋 CI (engine-only build + tests); signed releases; in-app self-update.
- 📋 iOS build (same engine, SwiftUI).
- 📋 i18n/localization.

## H. Touch interaction & input
Design: `docs/touch-interaction.md` (mode separation · loupe/offset-cursor · stylus · per-tool mapping).
- ✅ 3D nav: 1-finger orbit, 2-finger pan, pinch zoom (`SceneView`).
- 📋 **Tool rail** (Navigate/Select/Move/Support/Paint/Hollow/Cut/Measure/Text) + active-tool state.
- 📋 **Loupe** (magnifier) for placement/paint precision · **dual-finger offset cursor** · **nudge pad** (0.1/1 mm).
- 📋 **Stylus**: hover preview, pressure = brush size, side button = erase, palm rejection.
- 📋 **Long-press context menu** (right-click equiv) · **Undo/redo** top-bar buttons + 2-finger tap.
- 📋 **Support tools**: tap-to-place + drag-a-row + snap; eraser brush; paint (light/med/heavy + no-support); select/trash.
- 📋 **Masking**: paint brush · freehand lasso · box-select · fill/clear · mask-border sheet.
- 📋 **Hollow** sheet + live preview · **drain holes** tap-to-place ⟂ surface, drag, select→diameter/depth.
- 📋 **Cut**: draggable plane handle + rotate ring + keep top/bottom · **Measure**: 2-tap/face/3-tap.
- 📋 **Text/emboss**: tap-place + on-screen keyboard + sliders · **on-screen numeric keypad** for all fields.
- 📋 **Haptics** (place/delete/invalid) · **two-handed / left-handed** layout options.

## I. Cut & assembly (sectioning) — design: `docs/cut-tools.md`
- 📋 **Planar cut**: plane position (mm) + XYZ angles + draggable handle; into objects / into parts; keep upper/lower/both; watertight caps; grid/multi-cut.
- 📋 **Connectors / Assembly Keys** (pro): types **Plug / Dowel / Snap**; shapes round/dovetail/square/triangle; size + **tolerance (glue gap)** + depth + angle.
- 📋 **Placeable tabs**: auto-distribute by count **and** manual add/move/delete/select-all on the cut plane; overlap detection.
- 📋 **Split to objects/parts** · **auto-split to fit build volume** · hollowing-aware cut + pocket detection.
- 📋 **Touch**: tap-to-add connector (loupe), drag to move, tap→params sheet, distribute-N, ghost preview, numeric keypad.

## J. Alignment & movement — design: `docs/touch-interaction.md` (§ Alignment & movement)
- 🚧 Move X/Y/Z + rotate/scale exist (FDM `PlacementSheet`); resin needs the same.
- 📋 **Auto-Drop** (drop to plate) · **Center on plate** · **place-on-face / lay-flat** · **auto-orient** (min overhang/support, deterministic).
- 📋 **Scale** uniform/per-axis · % / mm · **fit-to-build-volume** · **Mirror X/Y/Z** · **Duplicate/array/clone**.
- 📋 **Arrange / auto-arrange** (spacing, rotate-for-arrange, per-plate/all; warn on no-fit) + auto-arrange after clone.
- 📋 **Align selected / distribute / center** · **multi-select (marquee) group-transform**.
- 📋 Support structures: FDM **brim** (width/type/ears/gap) · resin **pad/raft** (wall, slope, gap, around).
- 📋 **Snapping** (grid/plate/objects/faces) + collision warnings · **touch gizmos** (fat handles) + numeric keypad.

## K. Post-slice analysis & repair (UVTools-class) — design: `docs/research/slicer-feature-discovery.md`
- 📋 **Issue detection**: islands · overhangs · **resin traps** · **suction cups** · touching bounds · print height · empty layers.
- 📋 **Auto-repair**: remove islands ≤ N px · recursive island removal · attach islands down N layers · fill resin traps · ignore/delete per issue.
- 📋 **Pixel editor** (paint/delete per layer, add drain holes) · **pixel dimming** · **morph** (erode/dilate) · **edit any parameter on a sliced file**.

## L. Calibration & testing — design: `docs/research/slicer-feature-discovery.md`
- 📋 FDM: temperature · flow rate · pressure advance · retraction · cornering towers · auto-calibration + guide.
- 📋 Resin: **exposure test** plate · **XP/Cones** calibration artifacts (generated on-device).

---

### How to use this file
Move an item's mark as it lands. Add new lines freely. When a "how" question comes up, put the
algorithm/reference in `docs/research/feature-methods.md` and link it from the line.
