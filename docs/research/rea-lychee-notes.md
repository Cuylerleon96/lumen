# REA analysis of Lychee (RE notes)

**Tool:** `rea-agents` 6.8.0 (npm, Node 24). Command: `rea analyze /opt/LycheeSlicer`
+ `rea analyze-javascript-application`. Lychee = CEF app (Chromium Embedded Framework, `libcef.so`).

## What REA established
- Lychee is a **multi-window Electron/CEF application** — 62 inventoried JS/HTML artifacts under `bin/`.
- **Windows / entry points:** `indexUI.html` (main UI), `index3D.html`, `indexPreview3D.html`,
  `indexCLI.html`, `indexFile.html`, `indexLibraryOffline.html`, `indexProcessManager.html`,
  `indexThemeEditor.html`, `indexTester.html`, **`indexLysToLyl.html`** (LYS→LYL project converter),
  `landing.html`, `dialogModal.html`.
- **Layout descriptors (JSON):** `layout.LycheeSlicer.json`, `layout.LycheeCLI.json`,
  `layout.LycheeViewer.json`, `layout.LibraryManager.json`.
- **JS bundles:** `renderUI.js`, `render3D.js`, `renderCLI.js`, `preloadLib.js`, … (renderUI.js is the
  settings/tool vocabulary source we already mined).
- **Native core (closed):** `libEmeraude.so` (slicer engine), `libCTBAdapter.so` + `libCtbSDKFormat.so`
  (Chitubox CTB SDK), `LycheeSlicerHelper`.

## Interpretation
- The **algorithms** (supports, hollowing, auto-orient, AA, island detection) live in the **native
  engine** — a large stripped C++ `.so`. Recovering them via REA would require Ghidra/IDA and is
  impractical *and* legally off-limits to lift into an AGPL project. (Confirmed by this pass.)
- The **JS layer is orchestration + UI** — it's where REA is genuinely useful (window/module graph,
  settings→output mapping, format I/O). This corroborates the `feature-methods.md` plan: implement
  algorithms from **open references** (PrusaSlicer/Encrust/mslicer), not from Lychee's binary.

## Status / caveats
- REA CLI works from a user-local Node install (`~/.local/bin/{node,npm,npx}`, Node 24;
  `npx rea-agents@latest`).
- Full `analyze` on the 100 MB+ app is **slow** (progress stalled mid-parse in-session; ~31/62 files).
  Re-run in the background when needed. High-value REA uses going forward: **capture Lychee's exact
  output bytes** (CTB/GOO) to validate our writers, and **`analyze-javascript-application`** for the
  window/module graph. Native decompilation is a dead end for this project.
