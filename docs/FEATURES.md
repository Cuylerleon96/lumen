# FEATURES — living tracker (FDM + resin mobile slicer)

**This is the single source of truth for feature status. Update it as work lands.**
Status marks: **✅ done** · **🚧 in progress** · **🔜 next (P0)** · **📋 todo (P1)** · **💤 backlog (P2)**.
Method/reference for each *how* → `docs/research/feature-methods.md`. Feature inventory origin →
`docs/research/lychee-feature-gap.md`.

_Last updated: 2026-11-07 (plate manipulation, auto-arrange UI, the raster-transpose fix — and an
independent CTB validator)._

## Progress log
- **2026-11-07 — tap-to-move/rotate on the plate, the auto-arrange UI, and the raster-transpose fix.**
  **Tap the model to select it** (accent tint + a footprint outline), then `Move` or `Rotate` and drag:
  the model slides in the bed plane 1:1 under the finger, or turns about Z. A drag that starts OFF the
  model still orbits the camera and two fingers still pan/zoom, so no mode ever traps the view; the live
  transform is a **shader uniform**, so a drag never re-uploads the mesh, and only the *committed*
  transform reaches the placement (an interrupted gesture leaves no trace). The screen→world maths lives
  in `:core` (`Picking`) so it is unit-tested (`PickingTest`). **Auto-arrange gained its UI** — an
  *Arrange* button on both plates plus spacing / margin / turn-to-fit in the placement sheet (persisted
  in `state.json`) — and packs then **centres** the group. Also fixed: resin slicing **ignored the
  position the user set** (every model was force-centred) and now honours it, clamping only the footprint
  onto the print area. **Known gap: the plate holds ONE model**, so arrange centres it and the
  spacing/margin knobs only bite with two or more — multi-model is blocked on the FDM engine accepting
  more than one object.
- **2026-11-07 — target printers corrected to the real machines: Anycubic Photon Mono X (4K) and
  Photon Mono X 6K.** Both are now built-in entries, listed first, with the 6K's pixel pitch at **34.4 µm**
  (corrected the same day from 34.0 µm: 34.0 comes from dividing the *published build volume* 195.84 × 122.4
  by 5760, but that figure is the PRINTABLE AREA — a firmware margin inside the panel; the panel at 34.4
  µm is 198.15 × 123.84 mm per Anycubic's own machine table in UVTools' `AnycubicFile.cs`, and the
  marketed 9.25″ diagonal agrees at 34.59 µm. **Using 34.0 as the pitch scales every print by 1.2%
  silently.**) `ResinPrinterTest` locks resolution × pitch against the **panel** for both — deliberately
  NOT the published build volume — and the app's default printer is now the Mono X 6K.
- 2026-11-07 — **FDM calibration towers (item 6) + the release pipeline.** `CalibrationModels.fdmTower`
  builds a stepped tower (segments unioned with real CSG, so no internal faces where the blocks meet)
  and `GcodeCalibration.apply` rewrites the sliced G-code so each Z segment runs its own temperature /
  flow / pressure-advance value. A boundary fires on the first move that *reaches* it — 0.2 mm layers
  into 10 mm segments never land on a boundary, so an equality test would silently apply nothing — and
  a move clearing several boundaries emits each. Everything not inserted is preserved byte for byte.
  **Releases and updates now come from GitHub**: `update/info.json` is committed and is the manifest
  the app fetches (raw master), pointing at the release asset; `server/publish.sh` builds, bumps
  `versionCode` from that manifest and stages it, and `lumen-release` cuts the GitHub release and
  uploads the APK. v0.7.2 is the first release cut through this path.
- 2026-11-07 — **Anycubic PW0/PWS writer (item 3) + the target machines' native format.** Spec audited
  from UVTools into `docs/research/anycubic-pw.md` (643 lines), then implemented clean-room:
  `AnycubicPwWriter` (nibble RLE for the `.pw0`/`.pwmx`/`.pwmb` family, bit-plane for `.pws`; versions
  1/515/516/517/518; FileMark patched last via seek-back) + `Sl1ToPw` (re-containers like `Sl1ToCtb`,
  holds compressed PNGs and decodes one mask at a time). Wired as the native format for the Anycubic
  Mono X (`pwmx`) and Mono X 6K (`pwmb`) via `ResinPrinter.fileExtension`, available in the format
  picker. Two premises to remember: there is **no per-variant magic** (the variant is the filename
  extension) and **no XOR/encryption** in this format. **Remaining in item 3:** CBDDLP container (its
  codec `BitPlaneRle` is done), CXDLP, and the `.mdlp`/`.zcode`/`.lgs`/`.phz` backlog.
- 2026-11-07 — **Two engine-integration bugs found by slicing a real model, plus the shim bug behind
  them.** (a) The resin model was placed at the bed ORIGIN, but the engine's bed has its origin at a
  **corner** — models landed clipped in the corner with the top layers empty (700 KB of layers vs
  3.0 MB centred). Fixed with `ResinPlacement`. (b) The printer's **panel geometry was never sent**, so
  every printer sliced at the engine's default 120×68 mm / 2560 px panel. (c) The JNI shim applied
  settings with `cfg.set(key, string)`, which **silently dropped every key except four** — so support
  settings, hollowing and the panel geometry had never reached the engine; supports appeared only
  because `supports_enable` defaults to true. Fixed with `set_deserialize_strict` + a relink.
  `ResinEngineSmokeTest` (on-device) now asserts the raster size against the selected printer and that a
  bed-centred model keeps its material. **Item 3's sibling gap:** the settings surface is now real —
  audit which other SLA options still need exposing (elevation, pad_around_object, head penetration).
- 2026-11-07 — **Anycubic Photon S writer (item 3, first of the remaining formats).** `PhotonSWriter`
  (`.photon`): big-endian 98-byte header, 224×168 preview, per-layer LayerDef + RLE, all streamed one
  layer at a time. Clean-room from the open UVTools spec. Two bugs the tests caught, both worth
  knowing: (a) the run length in the RLE byte is an **8-bit** bit-reverse, not 7 — the 7-bit version
  encodes a 128-run as `0x7F` instead of `0xFF` and every run decodes to half its true length;
  (b) the header buffer was 64 bytes for a 98-byte header, which only shows up as a
  `BufferOverflowException`. Evidence: 96 tests, 0 failures — hand-computed RLE bytes, an independent
  parser walking header → preview → layer defs (asserting every `dataSize` matches the RLE it
  prefixes), and a full-res 1440×2560 three-layer pixel-for-pixel round-trip. `ResinFormat.PHOTONS`
  added. **Remaining in item 3:** PW0/PWS (`AnycubicFile.cs`) and CBDDLP (`CrealityCXDLP*.cs`).
- 2026-11-07 — **Drain-hole drilling (D.4) + boolean CSG land in `:core`.** New `Csg` (clean-room
  BSP boolean: `union`/`subtract`/`intersect`, plus `subtractLocal` which only runs the tree on the
  triangles near the tool) and `DrainHoles.drill()` (48-gon cylinder bit, 0.5 mm outset so the end cap
  never lands coplanar with the surface, through-all or blind depth). Evidence: `CsgTest` asserts the
  volume identities (`V(a−b) = V(a) − V(a∩b)`, union/intersect, cylinder-through-box = πr²h against the
  *polygonal* section) and `DrainHoleTest` fires rays down each bore — 0 crossings through a drilled
  hole vs 2 through the solid, and two crossing bores discount the Steinmetz solid (16r³/3) exactly
  once. **`:core:test` was NOT green at HEAD before this** — `PreviewRendererTest.kt` still declared the
  fork package (`io.github.whoxllm.orcamicro…`), so the test source set could not compile; fixed.
  Still todo: tap-to-place touch UI, pre-drill wiring in `ResinBackend`.
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
- 📋 Undo/redo across edits; **multi-model plate (multiple objects on one plate)** — requested by Cuyler
  2026-11-07, "with per model settings".
  **DECIDED 2026-11-07: per-model PRINT settings** (Cuyler: "Per model print settings") — infill, layer
  height, supports etc. per object, not just per-model placement.
  **FEASIBILITY SETTLED BY READING THE ENGINE, not guessed** (`libslic3r/Format/bbs_3mf.cpp`):
  1. The shim already loads the model with the GENERIC reader — `Model::read_from_file(path, …,
     LoadStrategy::LoadModel | LoadStrategy::AddDefaultInstances)` — which dispatches on format, so it
     already accepts **3MF/OBJ/AMF**, not just STL. **No native relink is needed to load a project
     file.** (A relink costs hours and risks the ninja state, so this is the difference between a
     feature and a project.)
  2. The 3MF importer reads `Metadata/model_settings.config` and applies each object's metadata with
     `model_object->config.set_deserialize(key, value, …)` — i.e. **any process key can be overridden
     per object**; `name`/`module` are the only special-cased keys.
  3. **TRAP**: an `<object>` block in that file without a `<part>` leaves that object's volume list
     EMPTY (the "whole geometry as one volume" fallback only runs when there is NO metadata entry at
     all), so it can silently produce an object with nothing to print. Always write one `<part
     id="1" subtype="normal_part">` per object.
  Plan: (a) a Kotlin 3MF writer in `:core` (zip of `[Content_Types].xml`, `_rels/.rels`,
  `3D/3dmodel.model` with one object+mesh per model and per-model placement in the build-item transform,
  and `Metadata/model_settings.config` with the per-object keys), (b) the slice job passes that 3MF when
  there is more than one model or any per-object override, keeping today's STL path as the single-model
  default, (c) a per-object settings surface driven by the engine's own key definitions, (d) assert the
  structure in a unit test with hand-written expected XML plus an independent Python unzip check, then
  verify on device that two objects with different `layer_height` really do differ in the G-code.
  Multi-model plate state (list + selected id, nearest-hit picking, arrange over N footprints) is the
  prerequisite and comes first.
- ✅ **Transparent transform sheet** (Cuyler 2026-11-07, shipped in `0f0ba5e` + `07e6ade`): the placement
  sheet no longer hides the plate. Both halves of the fix were needed — `scrimColor = Transparent` plus an
  82 % container **and** `skipPartiallyExpanded = false`, because a full-height sheet hides the model
  whatever its alpha. Verified on screen: the plate and model stay visible above it and the plate's own
  hint text shows through the sheet.
- 📋 Onboarding / first-run tips; units + language settings (i18n).
- 💤 Theme editor, splash, print history screen.

## B. Shared model I/O & prep
- ✅ **Load STL**, place, move/rotate/scale (FDM `PlacementSheet`).
- ✅ Fit-check against build volume.
- 📋 **Import OBJ, 3MF, STEP, AMF** (Lychee reads all; we read STL only). — **✅ OBJ + 3MF done**
  (`ObjReader`, `ThreeMfReader`, wired into the loader, normalized to STL); STEP/AMF still todo.
- 📋 **Mirror**, **Array/duplicate**, **"align to face"/lay-flat**, **planar cut**, **boolean CSG**.
  — **✅ Mirror X/Y/Z done** (Placement + sheet). — **✅ boolean CSG done** (`Csg`: union / subtract /
  intersect, in `:core`; the UI entry point is still todo). — **✅ lay-flat done** (`MeshOrient`:
  rests the model on its largest flat face, reports the face it chose; the placement-sheet button is
  still todo). Array/duplicate and planar cut remain.
- 📋 **Measure / dimension / text** tools.
- 💤 **Smooth/interpolate + wall smooth** · emboss · warp.

## C. FDM (via OrcaSlicer engine) — full page surface: `docs/research/complete-feature-surface.md`
- ✅ **Orca `libslic3r` engine** cross-compiled for arm64.
- ✅ ~950 Orca printer profiles (bundled).
- ✅ Print/filament/process settings editor (the tabbed `SettingsScreen`).
- ✅ Slice → G-code; layer/toolpath preview; print time + filament estimate. **The preview's slice data
  was verified independently** (2026-11-07): the app's own FDM output parsed by an outside Python parser
  and plotted is clean nested perimeters, and the app renders the same G-code cleanly — so a reported
  preview "tangle" is neither the G-code nor the parser layout. The one odd-looking line in the preview
  is the stock OrcaSlicer intro/prime extrusion, drawn green as `Custom`. **Then tested on the reporter's
  own model at his own scale — 200 %, 57.1 × 154.5 × 33.0 mm, 165 layers, ~4× the motion commands — and it
  STILL did not reproduce**: the preview and an independent parse agree, arc fitting (zero `G2`/`G3` in
  265,076 commands) and the segment-count theory are both closed by measurement, and the model carries the
  engine's own warning about floating regions. **Blocked on the reporter: a screenshot from his phone**,
  because every run here uses the emulator's software renderer, and a real-GPU artefact is the one class of
  cause this test cannot exclude — see the handoff's Open item 1.
- ✅ Tree/normal supports, brim, spiral vase (engine features).
- 📋 G-code post-processing scripts; per-object settings (see section A — the multi-model plate); calibration wizards (flow/PA/temp).
  — **✅ calibration towers + the G-code ramp** (`CalibrationModels.fdmTower` + `GcodeCalibration`:
  temperature / flow / pressure-advance per segment). The in-app wizard UI is still todo.
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
- 🔜 **Drain holes** (place/drill, diameter/depth) — **drilling done in `:core`** (`Csg` boolean +
  `DrainHoles.drill`, tested); **todo:** tap-to-place touch UI (D.9 tool rail) and pre-drill wiring
  into `ResinBackend` so a slice picks the holes up.
- 📋 **Hollow blockers** tab (exclude a region from hollowing).
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
### Fixed 2026-11-07 — the SL1→container raster transpose (verified)

The engine's layer PNG is **transposed** relative to the panel (its width is the panel's Y pixel count)
*and* world X runs down it **reversed** (`image-y == panelW - world X`), so a container that took the PNG's
dims as `resolutionX/Y` shipped a print rotated a quarter turn. **Measured, not inferred:** three
size-tagged markers at world (0,0) / (+30,0) / (0,+20) mm sliced through the standalone engine CLI landed
with their ink at image-x 0..12 mm and image-y 88..120 mm, measuring exactly their nominal 6.00 and
12.00 mm — a wrong mm-per-pixel would have shown up as a wrong marker size, so the reading checks itself
(and the markers' negative halves were missing, independently confirming the corner-origin raster). The
fix is a **90° clockwise** rotation into the panel frame: verified on real pixels, the source's
bottom-left ink (world X=0, Y=0) becomes the result's row 0 / column 0.

`RasterOrient` (`:core`) owns the transform; CTB, GOO and Anycubic now rotate the mask and declare the
**panel's** dims, and a raster matching neither the panel nor its transpose is still **refused** rather
than scaled — that mismatch is the signature of dropped `display_*` keys, and silently rescaling it
hides the fault behind a print that is subtly the wrong size. **`Sl1ToPw` no longer throws on real engine
output**, so the Anycubic format — the default for both target machines — produces a file again.

Evidence: `RasterOrientTest` (hand-computed 3×2 → 2×3 rotation; the same world pattern encoded in both
frames; a counter-rotation control that must NOT pass), `Sl1ToPwTest` on a **non-square** panel where the
two frames are distinguishable (the old fixture was square *and* built the PNG the natural way round,
which is how this shipped), and the bundled **real** engine SL1 (90 layers of 1440×2560) converting for a
2560×1440 panel — `scripts/ctb_validate.py` then decodes our CTB with an independent parser and confirms
the layer is **pixel-for-pixel** the source rotated 90° CW, with all 90 blobs decoding to exactly the
declared size. Reported by Cuyler as "small sections and details are getting messed up and not brought
over after slicing".

## D.8 Output formats
- ✅ SL1 / SL1S.
- ✅ **CTB v3+** — clean-room writer (`CtbWriter`) + SL1→CTB converter (`Sl1ToCtb`); header/RLE7/
  previews/ExtConfig per the catibo spec, unencrypted (key=0). **Independently validated** (Python
  parser: 90 layers, per-layer z/exposure, RLE7 round-trips). Export picks the **printer's native
  format** (`ResinPrinter.format`).
- ✅ **GOO** (Elegoo/Phrozen) — clean-room writer (`GooWriter`) built from the open GPL UVTools layout:
  full big-endian header (61 fields), LayerDef table, chunked RLE (magic `0x55`, plain runs, NOT
  checksum), footer. **RLE round-trip unit-tested.** (Blank previews; layer-table offsets correct.)
- ✅ CTB · ✅ GOO · ✅ SL1 · ✅ Anycubic PW0 family (`pw0`/`pwmx`/`pwmb`/`pwms`, and `.pws` bit-plane) · ✅ PhotonS (`.photon`).
- 💤 CBDDLP (bit-plane codec done, container todo), CXDLP, NANODLP, ZCODE/MDLP, LGS, PHZ.

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
- 🚧 Model library / recent files — **✅ recents** (`library/` dir, pruned to 12; "Recent" list on the
  empty plate re-opens in one tap). Full library UI (thumbnails, folders, search) still todo.
- 💤 Cloud sync / shop; CLI mode; viewer app.

## G. Build / release / infra
- ✅ Repo scaffold, ADRs, research docs, licensing (AGPL-3.0).
- 🚧 **FDM engine build** → then `:core:test` + `assembleDebug` + emulator screenshots.
- 📋 CI (engine-only build + tests); signed releases; in-app self-update.
- 📋 iOS build (same engine, SwiftUI).
- 📋 i18n/localization.

## H. Touch interaction & input
Design: `docs/touch-interaction.md` (mode separation · loupe/offset-cursor · stylus · per-tool mapping).
- ✅ 3D nav: 1-finger orbit, 2-finger pan, pinch zoom (`SceneView`).
- ✅ **Tap to select, then move/rotate the model directly** (`PlateGesture` + `PlateTool`): tap the
  model = selected (accent tint + footprint outline) and a Move/Rotate chip row; **Move** drags it
  across the bed, **Rotate** turns it about Z. A drag starting off the model orbits, and two fingers
  always pan/zoom — so the camera is never trapped in a tool. Live transform via a shader uniform.
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
- ✅ **Arrange / auto-arrange** — fit multiple models on the plate without overlap — **and its
  settings**: inter-object **spacing**, **rotate-for-arrange** (allowed rotations 0/90/180/270),
  **allow mirroring**, algorithm (grid / spiral / nested), keep-grouped, **per-plate / all-plates**,
  **multi-plate overflow**, warn on no-fit, and auto-arrange after clone.
  **Core done** (`PlateArranger`): deterministic shelf packing — largest-footprint-first, spacing +
  margin respected, 90° rotation tried when it helps, and anything that does not fit is *reported*
  rather than dropped. 9 tests, including an explicit pairwise no-overlap check and a spacing check —
  the spacing check caught a real overlap bug (the first wrap laid row 2 on top of row 1).
  **UI landed 2026-11-07:** an *Arrange* button on both plates (packing then **centring** the group,
  since packing from the corner reads as a bug) and spacing / margin / turn-to-fit in the placement
  sheet, persisted in `state.json`. **What is left is multi-model itself** — with one model the knobs
  have nothing to space, and the plate can hold only one until the FDM engine takes several objects.
  Build it on the placement frame settled 2026-11-07: the engine's raster is **corner-origin** (world
  (0,0)..(panelW, panelH)), so "on the plate" means the model's centre at half the panel. Arranging
  against the wrong frame clips every object silently — that is exactly the bug that took three
  attempts to fix. For resin, arranging is only half the job: each object also wants **auto-orient**
  for supports, and the two interact because orientation changes the footprint.
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
- 📋 **Project files** (save scene) · **multi-plate** · **per-object settings** — the per-object half is
  the "per model settings" request (see section A): placement-level overrides come with the multi-model
  plate; per-object PRINT settings wait on multi-object engine support.
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
