# FEATURES — living tracker (FDM + resin mobile slicer)

**This is the single source of truth for feature status. Update it as work lands.**
Status marks: **✅ done** · **🚧 in progress** · **🔜 next (P0)** · **📋 todo (P1)** · **💤 backlog (P2)**.
Method/reference for each *how* → `docs/research/feature-methods.md`. Feature inventory origin →
`docs/research/lychee-feature-gap.md`.

_Last updated: 2026-11-06 (initial full inventory)._

## Progress log
- 2026-11-07 — **SLA engine WORKS end-to-end.** PrusaSlicer **2.9.7 (newest)** `libslic3r` +
  `liblibseqarrange` cross-compiled for arm64 (deps incl. OpenVDB 11, OCCT, **Z3**, nlohmann_json);
  harness `native/sla/sla_cli.cpp` slices a cube → valid SL1 on the emulator; **JNI bridge**
  `libsla_engine.so` (`SlaEngine.nativeSlice`) packaged in the app + **loads** (verified via
  `nativeloader` logcat); `ResinBackend` prefers the engine; `sliceResin()` routes through it.
  **In-app update system** (Updater.kt + About UI + `server/{update_server.py,publish.sh}`).
  Dark `AppTheme`. App APK builds (~110 MB, both engines).
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
- ✅ Themed (dark-first `AppTheme` per `docs/design-language.md`).
- ✅ **In-app update system** (About → Check for updates; `server/` publishes from the build host).
- 🔜 **FDM-side "switch slicer"** entry (only resin has it today).
- 📋 Undo/redo across edits; multi-model plate (multiple objects on one plate).
- 📋 Onboarding / first-run tips; units + language settings (i18n).
- 💤 Theme editor, splash, print history screen.

## B. Shared model I/O & prep
- ✅ **Load STL**, place, move/rotate/scale (FDM `PlacementSheet`).
- ✅ Fit-check against build volume.
- 📋 **Import OBJ, 3MF, STEP, AMF** (Lychee reads all; we read STL only). — **✅ OBJ + 3MF done**
  (`ObjReader`, `ThreeMfReader`, wired into the loader, normalized to STL); STEP/AMF still todo.
- 📋 **Mirror**, **Array/duplicate**, **"align to face"/lay-flat**, **planar cut**, **boolean CSG**.
  — **✅ Mirror X/Y/Z done** (Placement + sheet).
- 📋 **Measure / dimension / text** tools.
- 💤 **Smooth/interpolate + wall smooth** · emboss · warp.

## C. FDM (via OrcaSlicer engine) — full page surface: `docs/research/complete-feature-surface.md`
- ✅ **Orca `libslic3r` engine** cross-compiled for arm64 (build in flight on the build host).
- ✅ ~950 Orca printer profiles (bundled).
- ✅ Print/filament/process settings editor (the tabbed `SettingsScreen`).
- ✅ Slice → G-code; layer/toolpath preview; print time + filament estimate.
- ✅ Tree/normal supports, brim, spiral vase (engine features).
- 📋 G-code post-processing scripts; per-object settings; calibration wizards (flow/PA/temp).
- 📋 Network print: Moonraker/Klipper, OctoPrint, PrusaLink, Bambu LAN/dev. — **✅ Moonraker /
  OctoPrint / PrusaLink done** (`PrinterUploader` + Send-to-printer dialog); Bambu LAN + post-processing
  scripts still todo.
- 💤 Belt printers; multi-material painting.

## D. Resin (MSLA) — the big build
### D.1 Engine
- ✅ **PrusaSlicer SLA engine** (`SLAPrint` + `sla::SupportTree` + OpenVDB) as a **second `.so`**
  — ADR 0003 option **B**. **PrusaSlicer 2.9.7 (newest)** cross-compiled for arm64; JNI bridge
  `libsla_engine.so` (`SlaEngine.nativeSlice(in, out, settingsJson)`); `ResinBackend` prefers it.
  Harness `native/sla/sla_cli.cpp` proves a cube → valid SL1 (90 layers) on the emulator.
  **Verified end-to-end in-app** (`nativeSlice rc=0`, real SL1); model placed on the **origin-centred
  SLA bed** (the FDM-bed placement produced empty layers).
- ✅ **SL1 writer** (config.ini + per-layer grayscale PNG), streaming.
- ✅ Platform-free grayscale **PNG encoder**.
### D.2 Print settings
- ✅ Layer height; exposure; bottom exposure; bottom layer count; light-off (single).
- 🚧 **Motion: lift/retract distance+speed, bottom speeds, rest timers** — model + print-time wired
  (tested, 36 tests pass). CTB/GOO writers still to emit them.
- 📋 Bottom-layer taper (`burnLayerDecreasing`); grey-level compensation (`*Compensation`/`GreyValue`); PWM.
- 💤 **TSMC two-stage motion**; **tilt/vat-printer motion** (tiltUp/Down cycles/delays/speeds).
### D.3 Supports
- 🚧 **Auto supports** (PrusaSlicer `sla::SupportTree`) — **settings exposed** (enable, density,
  contact/pillar diameter, object gap) + wired to the engine; interactive add/remove/edit points todo.
- 🔜 **Auto-supports** (from the SLA engine) — island/overhang-driven, **load/peel-aware** tree supports.
- 📋 **Manual support editing**: place/move/delete · **struts** (point-to-point) · contact/strut **shapes** · advanced segment editing (Lychee) · **proximity checker**.
- 📋 **Support painting** (brush: light/medium/heavy tags, "no-support" faces).
- 📋 **Full param set** (PrusaSlicer SLA): pillar Ø/widening/connection · base Ø/height/safety · head front/penetration/width · object elevation/critical angle/max weight · point density/min-distance · enforcers · tree type/angle/branch/tip · **anchor + border-anchor distance**, **merge level**, **lattice XY/Z**, **intersection avoidance**.
- 📋 **Orientation strategies** (min-support / min-contacts / min-projection / face-up-down) — HeyGears-style (`docs/research/heygears-support.md`).
- 💤 **Low-depth (AI) support generator** · **projection supports** · mini-supports · accordion supports.
### D.4 Hollowing & drainage
- 🔜 **Hollowing** via OpenVDB — **2D / 3D / voxel modes** (Lychee), wall thickness, closing distance, quality.
  — **✅ enable + wall thickness + closing distance** wired to the engine; 2D/3D/voxel mode choice still todo.
- 🔜 **Drain holes** (place/drill, diameter/depth) + **hollow blockers** tab.
- 📋 **Voxel pocket detection** (min pocket side/voxels, remove-below-holes, without-holes) · infill lattice · fill-holes · editable hollow voxels (tube brush).
### D.5 Anti-aliasing & masks
- 🚧 **Anti-aliasing** (grayscale edge levels) — `AntiAliaser` edge-softening pass applied in the
  SL1→CTB path (AA-levels setting); full 3D SSAA would need higher-res engine output. Masks still todo.
- 💤 Masks (mask PNG composited per border rules: mode/outset/slice).
### D.6 Detection & repair
- 🔜 **Island detection** (per-layer unsupported-region scan) — reliability.
- 📋 **Mesh repair** (auto-repair broken/non-manifold meshes); fill holes.
### D.7 Estimation
- ✅ **Resin volume/weight/cost + print-time** estimate — `ResinEstimator` (volume→mass→cost) +
  `ResinSettings.estimatedPrintTimeSec` (per-layer exposure + light-off + motion); shown live in the
  resin summary; density + price/kg settings.
### D.8 Output formats
- ✅ SL1 / SL1S.
- ✅ **CTB v3+** — clean-room writer (`CtbWriter`) + SL1→CTB converter (`Sl1ToCtb`); header/RLE7/
  previews/ExtConfig per the catibo spec, unencrypted (key=0). **Independently validated** (Python
  parser: 90 layers, per-layer z/exposure, RLE7 round-trips). Export picks the **printer's native
  format** (`ResinPrinter.format`).
- ✅ **GOO** (Elegoo/Phrozen) — clean-room writer (`GooWriter`) built from the open GPL UVTools layout:
  full big-endian header (61 fields), LayerDef table, chunked RLE (magic `0x55`, plain runs, NOT
  checksum), footer. **RLE round-trip unit-tested.** (Blank previews; layer-table offsets correct.)
- 💤 PW0/PWS/PWMA/PWMS, PHOTON(S), CBDDLP, NANODLP, ZCODE, CXDLP, LGS.

## E. Profiles & libraries
- ✅ **FDM profiles (rich, bundled from Orca):** ~1,545 machine (printer **+ nozzle variants**) · ~7,053 filament · ~3,341 process.
- ✅ **Resin profile DB (starter)** — 7 built-in resins (Elegoo/Siraya/Anycubic/Generic) with
  layer height + exposures + density, picker in the resin sheet (`ResinProfile`).
- 📋 **Resin profile DB (official + community)** + import/export; per-resin calibrated defaults.
- 📋 **Resin printer DB** (LCD res, pixel pitch, motion caps) — beyond 6 stubs.
- 📋 **Strategy layer** (goal + material + printer → parameters; HeyGears-style) + per-resin calibrated defaults.
- 📋 Support presets; printer discovery/connection.

## F. Project & platform
- 🚧 **Project format** (save/load a plate with models+settings) — **✅ `.osproj`** (state+model ZIP;
  Save/Open buttons on the plate; `ProjectArchive` + `SettingsStore` export/import). Lychee LYS/LYL import still todo.
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
- ✅ Move X/Y/Z + rotate/scale on **both** FDM and resin (shared `PlacementSheet`); resin uses
  rotate/scale only (origin-centred SLA bed). **Mirror X/Y/Z** done.
- 📋 **Auto-Drop** (drop to plate) · **Center on plate** · **place-on-face / lay-flat** · **auto-orient** (min overhang/support, deterministic).
- 📋 **Scale** uniform/per-axis · % / mm · **fit-to-build-volume** · **Mirror X/Y/Z** · **Duplicate/array/clone**.
- 📋 **Arrange / auto-arrange** (spacing, rotate-for-arrange, per-plate/all; warn on no-fit) + auto-arrange after clone.
- 📋 **Align selected / distribute / center** · **multi-select (marquee) group-transform**.
- 📋 Support structures: FDM **brim** (width/type/ears/gap) · resin **pad/raft** (wall, slope, gap, around).
- 📋 **Snapping** (grid/plate/objects/faces) + collision warnings · **touch gizmos** (fat handles) + numeric keypad.

## K. Post-slice analysis & repair (UVTools-class) — design: `docs/research/slicer-feature-discovery.md`
- 🚧 **Issue detection**: **islands ✅** + **resin traps ✅** (`IslandDetector`, `ResinTrapDetector`,
  tested) · overhangs · **suction cups** · touching bounds · print height · empty layers.
- 📋 **Auto-repair**: remove islands ≤ N px · recursive island removal · attach islands down N layers · fill resin traps · ignore/delete per issue.
- 📋 **Pixel editor** (paint/delete per layer, add drain holes) · **pixel dimming** · **morph** (erode/dilate) · **edit any parameter on a sliced file**.

## L. Calibration & testing — design: `docs/research/slicer-feature-discovery.md`
- 📋 FDM: temperature · flow rate · pressure advance · retraction · cornering towers · auto-calibration + guide.
- 🚧 Resin: **exposure test** plate · **XP/Cones** calibration artifacts (generated on-device).
  — **✅ generator** (`CalibrationModels`: step bar + dimension cube, on-device, "Load calibration model"
  button); full XP/Cones artifact set still todo.

## P. Power-user workflow & polish — design: `docs/research/complete-feature-surface.md`
- 📋 **Preset management**: save/load/import/export · inheritance · **compare presets** · dependencies · compatible printers/materials.
- 📋 **Physical printer management** + device/network send.
- 📋 **Project files** (save scene) · **multi-plate** · **per-object settings**.
- 📋 **Undo/redo** · **autosave + crash recovery** · **print history** · **user backups**.
- 📋 **Batch** process scene · batch convert · file queue.
- 📋 **G-code console + preview** (layer slider, toolpath/speed/flow views).
- 📋 **Validation warnings** (out-of-bounds, thin walls, overhangs, floating) + "how to fix" · display toggles (printable box/shadow/triangles).
- 📋 **Settings search** · visibility modes (Simple/Advanced/Develop) · favorites/Frequent.
- 📋 **Community profile DB** + profile sharing · **CLI** + scripting/plugin hooks · config import/export · check-for-updates.

## Q. Accessibility & ergonomics — design: `docs/research/complete-feature-surface.md`
- 📋 Large-text scaling · **colorblind-safe** · high-contrast · **left-handed** · touch/gesture parity · haptics · screen-reader labels.

---

### How to use this file
Move an item's mark as it lands. Add new lines freely. When a "how" question comes up, put the
algorithm/reference in `docs/research/feature-methods.md` and link it from the line.
