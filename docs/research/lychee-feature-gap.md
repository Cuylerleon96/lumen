# Lychee Slicer — feature/setting inventory vs. ours (gap analysis)

**Method:** Lychee Slicer is installed at `/opt/LycheeSlicer` (CEF app + native
engine `libEmeraude.so`, Chitubox CTB SDK `libCTBAdapter.so`/`libCtbSDKFormat.so`). Its settings and
tool vocabulary were extracted from the bundled UI bundle `bin/renderUI.js` (19 MB) + `bin/renderFile.js`
token frequency analysis, and the format list from extension frequency. Not from docs — from the
shipped binary. (User data dir was absent → the app hasn't been run as this user; the schema lives in
the bundle regardless.)

Legend: **HAVE** = works now · **PART** = partial/stub · **MISS** = not present · priority **P0/P1/P2**.

---

## A. Print settings — layer / exposure / motion
Lychee's per-layer parameter set (each with normal-layer + burn/bottom-layer variants, plus a
**TSMC two-stage-motion** variant where the lift/retract is split into a fast + slow stage):

| Lychee key | Meaning | Ours |
|---|---|---|
| `normalLayerHeight`, `burnLayerHeight` | layer heights | **PART** (single layer height) |
| `normalLayerExposureTime`, `burnLayerExposureTime` | exposure | **PART** (exposure + bottom exp) |
| `normalLayerLightOffDelay`, `burnLayerLightOffDelay` | light-off | **PART** (one light-off) |
| `burnLayerNumber` | bottom layer count | **HAVE** |
| `burnLayerDecreasing`, `burnLayerDecreasingNumber` | tapered bottom layers | **MISS** P2 |
| `normalLayerCompensation(+GreyValue)`, `burnLayerCompensation(+GreyValue)` | shrink compensation / grey | **MISS** P1 |
| `normalLayerPwm`, `burnLayerPwm` | per-layer light PWM | **MISS** P2 |
| `normalLayerZLiftDistance(+TSMC)`, `burnLayerZLiftDistance(+TSMC)` | lift distance | **MISS** P0 |
| `normalLayerZRetractDistance(+TSMC)`, `burnLayerZRetractDistance(+TSMC)` | retract distance | **MISS** P0 |
| `normalLayerLiftSpeed(+TSMC)`, `burnLayerLiftSpeed(+TSMC)` | lift speed | **MISS** P0 |
| `normalLayerBottomSpeed(+TSMC)`, `burnLayerBottomSpeed(+TSMC)` | bottom/retract speed | **MISS** P0 |
| `normalLayerWaitBeforePrint/AfterPrint/AfterLift`, `burnLayerWait*` | rest timers | **MISS** P1 |
| `isTSMCOn` + `slowSection*` | two-stage motion toggle | **MISS** P2 |
| `tiltUp* / tiltDown*` (cycles, delays, init/finish speeds, offset steps, `tiltAreaThreshold`) | **tilt (vat) printer motion** | **MISS** P2 |
| `printerWidth/Height/Depth/X/Y/Z` | build volume | **PART** (res/µm → area) |

> Big one: **motion parameters (lift/retract distance+speed, rest waits) are entirely missing** — a
> resin print won't run without them. These belong in `ResinSettings` + the SL1 `config.ini` and, for
> real printers, in the CTB/GOO writers.

## B. Resin & printer profiles
- `resins`, `resinData`, `resinId`, `resinEstimation*` (Fast/Real/Support/Object), import/export resin
  presets, resin modal family. — **MISS.** We have a hardcoded 6-printer list + default exposures; no
  resin database, no import/export, no estimation.
- `printers`, `printerBrand`, discovery/connection managers, printer config/import/export, `lastMSLAPrinter`
  **and** `lastFDMPrinter`. — **PART** (FDM: Orca's ~950 profiles ✅; resin: 6 stub printers).

## C. Supports
- `supportLight/Medium/Heavy`, `supportsTip`, `supportsBase`, `supportCompensation`, `supportMirror`,
  `SupportDepth`, `SupportCureTime`, `SupportLayerNumber`, `support2support(+Mini/Duplicate)`,
  `supportGridGap`, `supportDistributionSpacing`, `supportMethod/Mode/Shape`, `supportOpacity/Visibility`,
  `supportTags` + `supportToFav/Light/Medium/Heavy` (paint-per-support), `supportsPresets` +
  `supportsPresetsLychee`, `autosupportPresets`, `autosupportPoint/Id`, `autoSupportType(+Plus)`,
  `bracingSupports`, `accordionSupport(+FDM)`, `acceptSupportCollision`, `checkMiniSupports*`,
  `supportCreator`, `sliceSupportAngle/Area/Density/Extra/Gap/Grow/Offset/Outline/Size/Type`,
  `overhang`/`showOverhang`/`overhangMode`. — **MISS (entirely).** This is *the* hardest and most
  valuable chunk. Comes from PrusaSlicer `sla::SupportTree` (auto) + a manual/paint support editor UI.

## D. Hollowing & drainage
- `hollowing`/`hollowingMode`, `hollowing3D`, `hollow2d/hollow3d/hollow3dVoxels`, `hollowBlocker(s)` +
  `hollowBlockerSettings`, `shell`, `sliceShells/sliceShellOrder`, `sliceTopLayers`,
  `ContextMenuHoles` (drain holes), `fillHoles`/`fillHoleParameters`. — **MISS.** PrusaSlicer hollowing
  (OpenVDB) + drain-hole drilling.

## E. Anti-aliasing & masks
- `antialias`, `antiAliasingSlice`, `antialiasV2`; `mask`/`maskMode/Type/Origin/Path/Position/Image/
  Composite/Clip/Enabled`, `maskBorderMode/Outset/Repeat/Slice/Source/Width`, `MaskGenerator`. — **MISS.**
  AA is a `gray levels` writer feature; masks are an export-time image compositor.

## F. Model prep / edit tools
- Move (`copyMove`), **`autoOrient`/`autoOrientChoice`/tilt**, scale, rotate (`cameraRotate` for view),
  mirror (`supportMirrorWorld`), **array**, **planar cut** (`menuPlanarCut`, `cut`), boolean,
  **measure/dimension/text** (`dimension`), smooth (`smooth`/`SmoothShading`), emboss, warp,
  `menuProjectionSupports`, `menuLowDepthGenerator` (AI depth→supports). — **PART.** We have
  move/rotate/scale (FDM `PlacementSheet`); **no orient/auto-orient, cut, boolean, measure, text,
  mirror, array.**

## G. Detection & repair
- `island`/`islandsDetector`/`islandDetector` (island detection + "small island settings"),
  `repair`/`repairStats`/`autoRepair`/`cantRepair`, `magic`/`batchAndMagic`. — **MISS.** Island
  detection is essential for reliability (print-failure avoidance, à la Encrust).

## H. Estimation
- `resinEstimation(Fast/Real/Support/Object)`, print-time, cost. — **MISS** (we compute nothing yet).

## I. Output formats
- **Resin:** ctb, cbddlp, goo, photon(s), pws, pw0, pwma, pwms, nanodlp, zcode, cxdlp, lgs, sl1/sl1s.
  — **PART** (SL1 only, our writer). CTB/GOO next (Phase 3).
- **Project:** lys (scene), lyl (library), 3mf. **Input:** stl, obj, 3mf, step, amf, svg. — **PART**
  (STL in; 3MF/OBJ/STEP missing; no project/library format).

## J. FDM (G-code) side — Lychee has this too
- `gCodeStart/End/Header/Layer/SubLayer/Object/AfterPrint/BeforePrint`, `gCodeFanPower`,
  `gCodeKill`, `gcodeGenerator`, `autoSupportFDM`, `accordionSupportFDM`, `lastFDMPrinter`. — **HAVE**
  (via Orca engine; richer than Lychee's FDM).

## K. Shell / app-level
- Library manager, print history, recovery, shop/cloud, user backup/sync, theme editor, viewer, CLI
  (`renderCLI.js`), splash. — **MISS** (we have a single-doc mobile flow).

---

## Priority read (what to build after the SLA engine graft)
1. **P0 — motion settings** (lift/retract dist+speed, rest waits) into `ResinSettings` + writers —
   blocks *any* real print. Small.
2. **P0 — supports** (auto via PrusaSlicer `sla::SupportTree`, then a manual/paint editor). Largest.
3. **P0 — hollowing + drain holes** (PrusaSlicer + OpenVDB).
4. **P1 — anti-aliasing**, **resin/printer profile DB + import/export**, **island detection**,
   **resin/time/cost estimation**.
5. **P1 — model prep**: auto-orient, planar cut, mirror, array.
6. **P2 — TSMC / tilt motion**, masks, PWM, projection supports, library/history.

**Bottom line vs. Lychee:** we have the FDM brain (via Orca) and the SL1 *container*; Lychee's resin
*authoring* surface — supports, hollowing, motion, AA, profiles, islands, estimation — is ~entirely
ahead of us and is exactly what the PrusaSlicer SLA graft (ADR 0003) + follow-ups deliver. This doc
is the scorecard to work down.
