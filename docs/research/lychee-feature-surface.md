# Lychee feature surface (audit)

Mined from the shipped UI bundle (`/opt/LycheeSlicer/bin/renderUI.js`) — tokens, menus, panels.
Cross-checked against `FEATURES.md`; items marked **NEW** weren't captured before.

## Menus / app-level
Import/Export (local + **cloud**), Choose Printer, Config, Edit, Layout, Preview, **Printer**, **Print
History**, **Recovery** (crash-recovery prompt), **User Backup** (import/sync), **Shop**, **MangoDrive**
(cloud drive), **Viewer**, **License**, Update, **Landing**, **Low-Depth Generator**, **Projection
Supports**. _(Cloud/shop/licensing = N/A for our local-only, everything-free app; note only.)_

## Prepare / model tools
Import (STL/OBJ/3MF/STEP/AMF), **Boolean**, **Array**, Duplicate, Clone, Mirror, **Selection** ops
(add/invert/border/change selection), **Auto-orient** (+choice), **Auto-arrange** (+choice, margins),
**Planar Cut** (+ **Assembly Keys** pegs/sockets — see `cut-tools.md`), **Smooth/Interpolate**, wall
smooth, **Measure/Text/Emboss**, **Mesh Repair** (autoRepair, shouldRepair/cantRepair), **Island**
tools (large-island settings, `ignoreOverhangUnderDegree`).

## Supports
Auto-support (+ **FDM** variant), **auto-support type + "Plus"**, **accordion** supports, **bracing**
(auto + custom params), base support, support **tags** (create/tag per support), **mini-supports**
(+ height/ratio/resolution/size checks), **support grid**, inline supports, support-collision check,
`burnSupports` (expose supports on burn layers + %), `blurOnSupport`, `freestyleSupportIds`,
`copySupportSettings`, `ContextMenuSupports`, panels: `rightPanelSupportBasic/Main`, `supportSettingsTabs`.
**NEW: AddonAutoSupport, AddonSuctionCupDetector (suction-cup detection), menuLowDepthGenerator (AI
depth→support), menuProjectionSupports (projection supports).**

## Hollowing / drainage
Panels: **Hollow 2D**, **Hollow 3D**, **Hollow 3D-Voxels**, **Hollow Blocker**, **Holes** tabs.
`groupHollowingMode`, `defaultHollowBlocker`, `useVoxels`/`showVoxels`/`updatedHollowVoxelsAt`,
**voxel pocket detection** (`pocketDetectionMinPocketSide/Voxels`, `voxelsPocketDetectionRemoveBelowHoles`,
`voxelsPocketDetectionWithoutHoles`), drain holes.
**NEW: hollow 2D/3D/voxel modes, hollow blockers tab, voxel pocket detection.**

## Mask
`Mask`, `MaskGenerator`, `canvasMaskFinal/Preview`, `borderSelection`.

## Motion / exposure (SLA)
`normalLayer*`, `burnLayer*`, **TSMC** (`slowSectionBottomSpeed/Height/LiftSpeed/StepWait/ZLiftDistance`),
tilt/vat params, `burnSupportsExposurePercent`.

## Estimation / stats
`resinEstimation` (**Fast / Real / Object / Support**).

## Profiles / resin DB
`defaultResin`, `addDefaultResin`, **`addOfficialResin(s)`**, **community resins**
(`communityResinOpen`, `showNewCommunityResin`), `selectDefaultPrinterResin`, `popupResinConfig`.
**NEW: official + community resin DB.**

## Workflow / robustness
**Print History** (`menuPrintHistory`, `GetParameterHistory`, export), **Recovery**
(`checkRecovery`/accept/cancel), **User Backup** (`checkUserBackups`, `GetBackups`, `ImportUserBackup`,
`saveAutoBackupLocalLocation`, `saveOnLocalLimitBackup`), **autosave** (`needAutoSave`), **Batch**
(`Batch`, `checkerBatchTool`, `isInBatch`, `cancelBatchForObject`), **Settings** (accordion panels,
`showSettingsMenu`, `slicerSettingsUpdatedAt`).

## Verdict vs our tracker
Most of this is already captured (supports, hollowing, drain, masks, islands, repair, estimation,
motion, cut/assembly, arrange/orient, batch, history/recovery, resin DB). **Genuinely new → added to
tracker:** suction-cup detector, low-depth (AI) generator, projection supports, hollow 2D/3D/voxel
modes + blocker tab, voxel pocket detection, community/official resin DB, print history + backups,
smooth/interpolate + wall smooth.
